package coursier.cache

import java.io.ByteArrayOutputStream
import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import java.nio.charset.StandardCharsets.{ISO_8859_1, UTF_8}
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.{
  ConcurrentHashMap,
  CountDownLatch,
  ExecutorService,
  Executors,
  TimeUnit
}
import java.util.zip.GZIPOutputStream

import coursier.paths.CachePath
import coursier.util.{Artifact, Task}
import utest._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/** Real loopback HTTP responses for download framing and resume validation. */
object Rcours4BlankMemberTests extends TestSuite {
  private val body = "abcdef".getBytes(UTF_8)

  private final case class ProgressGate(atLeast: Long, release: CountDownLatch)

  private final case class Reply(
    status: String,
    headers: Seq[(String, String)],
    chunks: List[Array[Byte]],
    pauseAfterFirstChunk: Option[ProgressGate] = None
  )

  private final case class FileState(
    finalExists: Boolean,
    temporaryExists: Boolean,
    temporaryBytes: Array[Byte]
  )

  private final case class Observation(
    url: String,
    result: Either[ArtifactError, Array[Byte]],
    requests: List[String],
    progress: List[Long],
    downloadResults: List[(String, Boolean)],
    finalExists: Boolean,
    temporaryExists: Boolean,
    temporaryBytes: Array[Byte],
    pendingState: Option[FileState],
    allResults: List[Either[ArtifactError, Array[Byte]]],
    outputErrorMessage: Option[String]
  )

  private final class RecordingLogger() extends CacheLogger {
    val watchingOwner = new CountDownLatch(1)
    override def downloadLength(
      url: String,
      totalLength: Long,
      alreadyDownloaded: Long,
      watching: Boolean
    ): Unit =
      if (watching) watchingOwner.countDown()

    private val lock             = new Object
    private val progress0        = ListBuffer.empty[Long]
    private val downloadResults0 = ListBuffer.empty[(String, Boolean)]
    private val waiters          = ListBuffer.empty[(Long, CountDownLatch)]

    def progress: List[Long] =
      lock.synchronized(progress0.toList)

    def downloadResults: List[(String, Boolean)] =
      lock.synchronized(downloadResults0.toList)

    def awaitProgressAtLeast(target: Long, timeout: FiniteDuration): Boolean = {
      val waiter = new CountDownLatch(1)
      val shouldWait = lock.synchronized {
        if (progress0.exists(_ >= target)) false
        else {
          waiters += ((target, waiter))
          true
        }
      }
      val reached = !shouldWait || waiter.await(timeout.toMillis, TimeUnit.MILLISECONDS)
      lock.synchronized(waiters --= waiters.filter(_._2 eq waiter))
      reached
    }

    override def downloadProgress(url: String, downloaded: Long): Unit =
      lock.synchronized {
        assert(downloaded > 0L)
        progress0 += downloaded
        val reached = waiters.filter(_._1 <= downloaded)
        reached.foreach(_._2.countDown())
        waiters --= reached
      }

    override def downloadedArtifact(url: String, success: Boolean): Unit =
      lock.synchronized(downloadResults0 += ((url, success)))
  }

  private def response(
    status: String,
    headers: Seq[(String, String)],
    chunks: List[Array[Byte]]
  ): Reply =
    Reply(status, headers, chunks)

  private def fixed(
    length: String,
    bytes: Array[Byte] = body,
    extra: Seq[(String, String)] = Nil,
    status: String = "200 OK"
  ): Reply =
    response(status, Seq("Content-Length" -> length) ++ extra, List(bytes))

  private def partial(
    contentRange: Option[String],
    contentLength: Option[String],
    bytes: Array[Byte]
  ): Reply = {
    val rangeHeader  = contentRange.map(value => Seq("Content-Range" -> value)).getOrElse(Nil)
    val lengthHeader = contentLength.map(value => Seq("Content-Length" -> value)).getOrElse(Nil)
    response("206 Partial Content", rangeHeader ++ lengthHeader, List(bytes))
  }

  private def rawHeaders(reply: Reply): Array[Byte] =
    (s"HTTP/1.1 ${reply.status}\r\n" +
      reply.headers.map { case (key, value) => s"$key: $value\r\n" }.mkString +
      "Connection: close\r\n\r\n").getBytes(ISO_8859_1)

  private def readRequest(socket: Socket): String = {
    val input = socket.getInputStream
    val bytes = new ByteArrayOutputStream()
    var tail  = 0
    while (tail != 0x0d0a0d0a) {
      val next = input.read()
      require(next >= 0, "Client closed before request headers ended")
      bytes.write(next)
      tail = (tail << 8) | next
    }
    bytes.toString("ISO-8859-1")
  }

  private def scenario(
    replies: List[Reply],
    prefix: Array[Byte] = Array.emptyByteArray,
    cap: Int = CacheDefaults.bufferSize,
    inspectWhilePaused: Option[(Long, CountDownLatch)] = None,
    clientCount: Int = 1,
    joinBeforeBody: Boolean = false,
    temporaryDirectory: Boolean = false
  ): Observation = {
    var directory: Path                    = null
    var server: ServerSocket               = null
    var serverWorker: ExecutorService      = null
    var downloadWorkers: ExecutorService   = null
    var connectionWorkers: ExecutorService = null
    val handlers                           = ListBuffer.empty[Future[Unit]]
    var serving: Future[Unit]              = null
    val stopping                           = new java.util.concurrent.atomic.AtomicBoolean(false)
    val sockets                            = ConcurrentHashMap.newKeySet[Socket]()
    val requests                           = ListBuffer.empty[String]
    val logger                             = new RecordingLogger()

    var serverExecutionContext: ExecutionContext = null

    def fileState(finalFile: java.io.File, temporaryFile: java.io.File): FileState =
      FileState(
        Files.exists(finalFile.toPath),
        Files.exists(temporaryFile.toPath),
        if (Files.exists(temporaryFile.toPath)) Files.readAllBytes(temporaryFile.toPath)
        else Array.emptyByteArray
      )

    def stopServer(): Unit =
      if (server != null && stopping.compareAndSet(false, true))
        server.close()

    try {
      directory = Files.createTempDirectory("download-length-integrity")
      server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
      serverWorker = Executors.newSingleThreadExecutor()
      downloadWorkers = Executors.newFixedThreadPool(2)
      connectionWorkers = Executors.newFixedThreadPool(2)
      val connectionExecutionContext = ExecutionContext.fromExecutor(connectionWorkers)
      serverExecutionContext = ExecutionContext.fromExecutor(serverWorker)

      val fallbackReply = fixed(body.length.toString, body)
      serving = Future {
        val requestIndex = new java.util.concurrent.atomic.AtomicInteger()
        while (!stopping.get()) {
          val accepted =
            try Some(server.accept())
            catch {
              // Closing the listener releases the accept loop during teardown.
              case _: SocketException if stopping.get() && server.isClosed => None
            }
          accepted.foreach { socket =>
            val handler = Future {
              sockets.add(socket)
              try {
                val requestText = readRequest(socket)
                requests.synchronized(requests += requestText)
                val isHead        = requestText.startsWith("HEAD ")
                val index         = if (isHead) 0 else requestIndex.getAndIncrement()
                val originalReply = if (index < replies.length) replies(index) else fallbackReply
                val reply = if (isHead)
                  originalReply.copy(chunks = Nil, pauseAfterFirstChunk = None)
                else originalReply
                val output = socket.getOutputStream
                output.write(rawHeaders(reply))
                output.flush()
                if (joinBeforeBody && !isHead)
                  assert(logger.watchingOwner.await(30, TimeUnit.SECONDS))
                reply.chunks.zipWithIndex.foreach { case (chunk, index) =>
                  output.write(chunk)
                  output.flush()
                  if (index == 0)
                    reply.pauseAfterFirstChunk.foreach { gate =>
                      if (!logger.awaitProgressAtLeast(gate.atLeast, 30.seconds))
                        throw new IllegalStateException(
                          s"Timed out waiting for download progress to reach ${gate.atLeast}"
                        )
                      if (!gate.release.await(30, TimeUnit.SECONDS))
                        throw new IllegalStateException(
                          "Timed out waiting for the test to release the response"
                        )
                    }
                }
              }
              finally {
                socket.close()
                sockets.remove(socket)
              }
            }(connectionExecutionContext)
            handlers.synchronized(handlers += handler)
          }
        }
      }(serverExecutionContext)

      val url = s"http://127.0.0.1:${server.getLocalPort}/artifact"
      val cache = FileCache[Task](directory.toFile)
        .withPool(downloadWorkers)
        .withChecksums(Seq(None))
        .withRetry(0)
        .withBufferSize(cap)
        .withLogger(logger)
      val finalFile     = cache.localFile(url)
      val temporaryFile = CachePath.temporaryFile(finalFile)
      val expectedOutputError =
        if (temporaryDirectory) {
          Files.createDirectories(temporaryFile.toPath)
          val error = assertThrows[java.io.IOException] {
            Files.newOutputStream(temporaryFile.toPath).close()
          }
          Some(
            s"Caught ${error.getClass.getName}${Option(error.getMessage).fold("")(" (" + _ + ")")} while downloading $url"
          )
        }
        else None
      if (prefix.nonEmpty) {
        Files.createDirectories(temporaryFile.toPath.getParent)
        Files.write(temporaryFile.toPath, prefix)
      }

      val downloads   = List.fill(clientCount)(cache.file(Artifact(url)).run.future()(cache.ec))
      val downloading = downloads.head
      val pendingState = inspectWhilePaused.map { case (target, release) =>
        if (!logger.awaitProgressAtLeast(target, 30.seconds))
          throw new IllegalStateException(
            s"Timed out waiting for download progress to reach $target"
          )
        assert(!downloading.isCompleted)
        val state = fileState(finalFile, temporaryFile)
        release.countDown()
        state
      }

      val results =
        downloads.map(f => Await.result(f, 30.seconds).map(file => Files.readAllBytes(file.toPath)))
      val result = results.head

      stopServer()
      Await.result(serving, 30.seconds)
      handlers.synchronized(handlers.toList).foreach(f => Await.result(f, 30.seconds))

      val observedRequests  = requests.synchronized(requests.toList)
      val observedFileState = fileState(finalFile, temporaryFile)
      Observation(
        url,
        result,
        observedRequests,
        logger.progress,
        logger.downloadResults,
        observedFileState.finalExists,
        observedFileState.temporaryExists,
        observedFileState.temporaryBytes,
        pendingState,
        results,
        expectedOutputError
      )
    }
    finally {
      replies.flatMap(_.pauseAfterFirstChunk).foreach(_.release.countDown())
      inspectWhilePaused.foreach(_._2.countDown())
      try {
        stopServer()
        sockets.toArray(new Array[Socket](0)).foreach(_.close())
        if (serving != null)
          Await.result(serving, 30.seconds)
      }
      finally
        try
          if (connectionWorkers != null) {
            connectionWorkers.shutdownNow()
            assert(connectionWorkers.awaitTermination(10, TimeUnit.SECONDS))
          }
        finally
          try
            if (serverWorker != null) {
              serverWorker.shutdownNow()
              assert(serverWorker.awaitTermination(10, TimeUnit.SECONDS))
            }
          finally
            try
              if (downloadWorkers != null) {
                downloadWorkers.shutdownNow()
                assert(downloadWorkers.awaitTermination(10, TimeUnit.SECONDS))
              }
            finally if (directory != null) os.remove.all(os.Path(directory))
    }
  }

  private def bytesAre(observation: Observation, expected: Array[Byte]): Unit =
    assert(observation.result.map(_.toSeq) == Right(expected.toSeq))

  private def successfulDownloadIs(observation: Observation, expected: Array[Byte]): Unit = {
    bytesAre(observation, expected)
    assert(observation.finalExists)
    assert(!observation.temporaryExists)
    assert(observation.temporaryBytes.isEmpty)
    assert(observation.downloadResults.contains((observation.url, true)))
  }

  private def errorMessage(observation: Observation): Option[String] =
    observation.result.left.toOption.collect {
      case error: ArtifactError.DownloadError => error.message
    }

  private def downloadErrorIs(observation: Observation, expected: String): Unit = {
    assert(errorMessage(observation) == Some(expected))
    assert(!observation.finalExists)
    assert(!observation.temporaryExists)
    assert(observation.temporaryBytes.isEmpty)
    assert(observation.downloadResults.contains((observation.url, false)))
  }

  private def incompleteMessage(url: String, expected: Long, received: Long): String =
    s"Incomplete response from $url: expected $expected bytes, received $received bytes"

  private def invalidContentLengthMessage(url: String, value: String): String =
    s"Invalid Content-Length '$value' from $url: expected a non-negative 64-bit decimal length"

  private def invalidContentRangeMessage(url: String, value: String, start: Long): String =
    s"Invalid Content-Range '$value' from $url: expected bytes starting at $start and ending at the complete response length"

  private def missingContentRangeMessage(url: String, start: Long): String =
    s"Missing Content-Range from $url: expected bytes starting at $start"

  private def statusMessage(url: String, code: Int): String =
    s"Unexpected HTTP status $code from $url: expected a response with an artifact body"

  private def chunkedMessage(url: String, received: Long): String =
    s"Invalid response framing from $url: expected a complete chunked message, received $received bytes (Premature EOF)"

  private def unsupportedMessage(url: String, encoding: String): String =
    s"Unsupported Content-Encoding '$encoding' from $url: expected identity, gzip, or deflate"

  private def gzipBytes(bytes: Array[Byte]): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    val gzip   = new GZIPOutputStream(output)
    gzip.write(bytes)
    gzip.close()
    output.toByteArray
  }

  private def deflateBytes(bytes: Array[Byte]): Array[Byte] = {
    val output  = new ByteArrayOutputStream()
    val deflate = new java.util.zip.DeflaterOutputStream(output)
    deflate.write(bytes)
    deflate.close()
    output.toByteArray
  }

  private def chunk(bytes: Array[Byte], closing: Boolean = true): Array[Byte] =
    (Integer.toHexString(bytes.length) + "\r\n").getBytes(ISO_8859_1) ++ bytes ++
      (if (closing) "\r\n0\r\n\r\n" else "\r\n").getBytes(ISO_8859_1)

  val tests = Tests {
    test("unknown coding after blanks is rejected") {
      val encoding = ", ,BoGuS"
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> encoding))))
      downloadErrorIs(o, unsupportedMessage(o.url, encoding))
    }
    test("unknown coding before blanks is rejected") {
      val encoding = "bogus, ,"
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> encoding))))
      downloadErrorIs(o, unsupportedMessage(o.url, encoding))
    }
    test("unknown coding between supported and blank members is rejected") {
      val encoding = ", identity, ,br, ,gzip, ,"
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> encoding))))
      downloadErrorIs(o, unsupportedMessage(o.url, encoding))
    }
    test("blank only encoding allows a complete fixed resume") {
      val reply = partial(Some("bytes 3-5/6"), Some("3"), body.drop(3))
      val o = scenario(List(reply.copy(headers = reply.headers :+ ("Content-Encoding" -> ", , ,"))), prefix = body.take(3))
      successfulDownloadIs(o, body)
      assert(o.requests.count(_.contains("Range: bytes=3-")) == 1)
    }
    test("blank only encoding preserves short fixed resume rejection") {
      val reply = partial(Some("bytes 3-5/6"), Some("3"), body.drop(3).take(2))
      val o = scenario(List(reply.copy(headers = reply.headers :+ ("Content-Encoding" -> ", , ,"))), prefix = body.take(3))
      downloadErrorIs(o, incompleteMessage(o.url, 3, 2))
    }
    test("blank only encoding preserves complete chunked resume") {
      val reply = response("206 Partial Content", Seq("Content-Range" -> "bytes 3-5/6", "Transfer-Encoding" -> "chunked", "Content-Encoding" -> ", , ,"), List(chunk(body.drop(3))))
      val o = scenario(List(reply), prefix = body.take(3))
      successfulDownloadIs(o, body)
    }
    test("blank only encoding preserves short chunked resume rejection") {
      val reply = response("206 Partial Content", Seq("Content-Range" -> "bytes 3-5/6", "Transfer-Encoding" -> "chunked", "Content-Encoding" -> ", , ,"), List(chunk(body.drop(3).take(2))))
      val o = scenario(List(reply), prefix = body.take(3))
      downloadErrorIs(o, incompleteMessage(o.url, 3, 2))
    }
    test("blank only encoding preserves overlong chunked resume rejection") {
      val reply = response("206 Partial Content", Seq("Content-Range" -> "bytes 3-5/6", "Transfer-Encoding" -> "chunked", "Content-Encoding" -> ", , ,"), List(chunk("defXYZ".getBytes(UTF_8))))
      val o = scenario(List(reply), prefix = body.take(3))
      downloadErrorIs(o, s"Overlong response from ${o.url}: expected 3 bytes, received 6 bytes")
    }
    test("blank only encoding preserves missing chunk terminator rejection") {
      val reply = response("206 Partial Content", Seq("Content-Range" -> "bytes 3-5/6", "Transfer-Encoding" -> "chunked", "Content-Encoding" -> ", , ,"), List(chunk(body.drop(3), closing = false)))
      val o = scenario(List(reply), prefix = body.take(3))
      downloadErrorIs(o, chunkedMessage(o.url, 3))
    }
    test("blank only resume remains temporary until its closing chunk") {
      val release = new CountDownLatch(1)
      val reply = Reply("206 Partial Content", Seq("Content-Range" -> "bytes 3-5/6", "Transfer-Encoding" -> "chunked", "Content-Encoding" -> ", , ,"), List(chunk(body.drop(3), closing = false), "0\r\n\r\n".getBytes(ISO_8859_1)), Some(ProgressGate(6, release)))
      val o = scenario(List(reply), prefix = body.take(3), inspectWhilePaused = Some((6, release)))
      val pending = o.pendingState.get
      assert(!pending.finalExists)
      assert(pending.temporaryExists)
      assert(pending.temporaryBytes.toSeq == body.toSeq)
      successfulDownloadIs(o, body)
    }
    test("blank only encoding preserves invalid range rejection") {
      val reply = partial(Some("bytes 2-4/5"), Some("3"), body.drop(3))
      val o = scenario(List(reply.copy(headers = reply.headers :+ ("Content-Encoding" -> ", , ,"))), prefix = body.take(3))
      downloadErrorIs(o, invalidContentRangeMessage(o.url, "bytes 2-4/5", 3))
    }
    test("blank only full response replaces a saved prefix") {
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> ", , ,"))), prefix = body.take(3))
      successfulDownloadIs(o, body)
    }
    test("blank only encoding accepts an empty complete artifact") {
      val o = scenario(List(fixed("0", Array.emptyByteArray, Seq("Content-Encoding" -> ", , ,"))))
      successfulDownloadIs(o, Array.emptyByteArray)
    }
    test("unknown coding amid blanks cannot resume a prefix") {
      val encoding = ", ,bogus, ,"
      val reply = partial(Some("bytes 3-5/6"), Some("3"), body.drop(3))
      val o = scenario(List(reply.copy(headers = reply.headers :+ ("Content-Encoding" -> encoding))), prefix = body.take(3))
      downloadErrorIs(o, unsupportedMessage(o.url, encoding))
    }
    test("a repeated blank field must not hide an earlier unknown coding") {
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> "bogus", "Content-Encoding" -> ", , ,"))))
      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }
    test("a repeated blank field must not hide an earlier gzip coding") {
      val encoded = gzipBytes(body)
      val o = scenario(List(fixed(encoded.length.toString, encoded, Seq("Content-Encoding" -> "gzip", "Content-Encoding" -> ", , ,"))))
      successfulDownloadIs(o, body)
    }
  }
}
