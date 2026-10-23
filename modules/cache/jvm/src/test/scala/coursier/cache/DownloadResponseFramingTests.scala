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
object DownloadResponseFramingTests extends TestSuite {
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
    test("valid mixed case range unit completes the artifact") {
      val o =
        scenario(List(partial(Some("ByTeS 3-5/6"), Some("3"), body.drop(3))), prefix = body.take(3))
      successfulDownloadIs(o, body)
    }

    test("unsolicited 304 cannot publish an empty artifact") {
      val o = scenario(List(response("304 Not Modified", Nil, Nil)))
      downloadErrorIs(o, statusMessage(o.url, 304))
    }

    test("cut off chunked response removes temporary bytes") {
      val wire = "6\r\nabcdef\r\n".getBytes(ISO_8859_1)
      val o = scenario(List(response("200 OK", Seq("Transfer-Encoding" -> "chunked"), List(wire))))
      downloadErrorIs(o, chunkedMessage(o.url, 6))
    }

    test("cut off gzip response removes temporary bytes") {
      val output = new ByteArrayOutputStream()
      val gzip   = new GZIPOutputStream(output)
      gzip.write(body)
      gzip.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed.dropRight(4),
        Seq("Content-Encoding" -> "gzip")
      )))
      downloadErrorIs(o, incompleteMessage(o.url, compressed.length, compressed.length - 4))
    }

    test("short other encoded response cannot publish partial bytes") {
      val output  = new ByteArrayOutputStream()
      val deflate = new java.util.zip.DeflaterOutputStream(output)
      deflate.write(body)
      deflate.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed.dropRight(2),
        Seq("Content-Encoding" -> "deflate")
      )))
      downloadErrorIs(o, incompleteMessage(o.url, compressed.length, compressed.length - 2))
    }

    test("correct mixed case gzip decodes the body") {
      val output = new ByteArrayOutputStream()
      val gzip   = new GZIPOutputStream(output)
      gzip.write(body)
      gzip.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "GZip")
      )))
      successfulDownloadIs(o, body)
    }

    test("chunked range obeys its content range without content length") {
      val wire = "3\r\ndef\r\n0\r\n\r\n".getBytes(ISO_8859_1)
      val o = scenario(
        List(response(
          "206 Partial Content",
          Seq("Transfer-Encoding" -> "chunked", "Content-Range" -> "bytes 3-5/6"),
          List(wire)
        )),
        prefix = body.take(3)
      )
      successfulDownloadIs(o, body)
    }

    test("short chunked range cannot publish a partial artifact") {
      val wire = "2\r\nde\r\n0\r\n\r\n".getBytes(ISO_8859_1)
      val o = scenario(
        List(response(
          "206 Partial Content",
          Seq("Transfer-Encoding" -> "chunked", "Content-Range" -> "bytes 3-5/6"),
          List(wire)
        )),
        prefix = body.take(3)
      )
      downloadErrorIs(o, incompleteMessage(o.url, 3L, 2L))
    }

    test("empty close delimited response is a valid empty artifact") {
      val o = scenario(List(response("200 OK", Nil, Nil)))
      successfulDownloadIs(o, Array.emptyByteArray)
    }

    test("correct deflate body decodes without comparing decoded and wire lengths") {
      val output  = new ByteArrayOutputStream()
      val deflate = new java.util.zip.DeflaterOutputStream(output)
      deflate.write(body)
      deflate.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "deflate")
      )))
      successfulDownloadIs(o, body)
    }

    test("concurrent callers never publish the same short artifact") {
      val o =
        scenario(List(fixed("9", body), fixed("9", body)), clientCount = 2, joinBeforeBody = true)
      assert(o.allResults.size == 2)
      assert(o.allResults.map(_.left.toOption.map(_.message)) ==
        List.fill(2)(Some(incompleteMessage(o.url, 9, 6))))
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("concurrent callers share one correct artifact download") {
      val o = scenario(List(fixed("6", body)), clientCount = 2, joinBeforeBody = true)
      assert(o.allResults.size == 2)
      assert(o.allResults.forall(_.map(_.toSeq) == Right(body.toSeq)))
      successfulDownloadIs(o, body)
      assert(o.requests.count(_.startsWith("GET ")) == 1)
    }

    test("HEAD metadata length does not require response bytes") {
      val server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext(
        "/artifact",
        exchange => {
          assert(exchange.getRequestMethod == "HEAD")
          exchange.getResponseHeaders.set("Content-Length", "6")
          exchange.sendResponseHeaders(200, -1L)
          exchange.close()
        }
      )
      server.start()
      var connection: java.net.HttpURLConnection = null
      try {
        val url = s"http://127.0.0.1:${server.getAddress.getPort}/artifact"
        connection = ConnectionBuilder(
          url
        ).withMethod("HEAD").connection().asInstanceOf[java.net.HttpURLConnection]
        assert(connection.getResponseCode == 200)
        assert(connection.getContentLengthLong == 6L)
        assert(connection.getInputStream.read() == -1)
      }
      finally {
        if (connection != null) connection.disconnect()
        server.stop(0)
      }
    }

    test("chunked range must consume the closing chunk before publishing") {
      val o = scenario(
        List(response(
          "206 Partial Content",
          Seq(
            "Transfer-Encoding" -> "chunked",
            "Content-Range"     -> "bytes 3-5/6"
          ),
          List(chunk(body.drop(3), closing = false))
        )),
        prefix = body.take(3)
      )
      downloadErrorIs(o, chunkedMessage(o.url, 3))
    }

    test("chunked range rejects data beyond its stated range") {
      val o = scenario(
        List(response(
          "206 Partial Content",
          Seq(
            "Transfer-Encoding" -> "chunked",
            "Content-Range"     -> "bytes 3-5/6"
          ),
          List(chunk("defXYZ".getBytes(UTF_8)))
        )),
        prefix = body.take(3)
      )
      downloadErrorIs(o, s"Overlong response from ${o.url}: expected 3 bytes, received 6 bytes")
    }

    test("range data stays temporary until the closing chunk arrives") {
      val release = new CountDownLatch(1)
      val reply = Reply(
        "206 Partial Content",
        Seq(
          "Transfer-Encoding" -> "chunked",
          "Content-Range"     -> "bytes 3-5/6"
        ),
        List(
          chunk(body.drop(3), closing = false),
          "0\r\n\r\n".getBytes(ISO_8859_1)
        ),
        Some(ProgressGate(6, release))
      )
      val o = scenario(List(reply), prefix = body.take(3), inspectWhilePaused = Some((6, release)))
      val pending = o.pendingState.get
      assert(!pending.finalExists)
      assert(pending.temporaryExists)
      assert(pending.temporaryBytes.toSeq == body.toSeq)
      successfulDownloadIs(o, body)
    }

    test("no body status with a saved prefix is rejected and cleaned") {
      for (code <- Seq(204, 205, 304)) {
        val o = scenario(
          List(response(s"$code No Body", Seq("Content-Length" -> "6"), Nil)),
          prefix = body.take(3)
        )
        downloadErrorIs(o, statusMessage(o.url, code))
        assert(o.requests.count(_.startsWith("GET ")) == 1)
      }
    }

    test("empty content encoding means an unencoded body") {
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> ""))))
      successfulDownloadIs(o, body)
    }

    test("empty list elements do not change identity encoding") {
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> ", identity, ,"))))
      successfulDownloadIs(o, body)
    }

    test("empty list elements do not allow unknown encodings") {
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> ", bogus"))))
      downloadErrorIs(o, unsupportedMessage(o.url, ", bogus"))
    }

    test("only empty encoding list elements mean an unencoded body") {
      for (encoding <- Seq(" ", ",", ", , ,")) {
        val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> encoding))))
        successfulDownloadIs(o, body)
      }
    }

    test("empty list elements preserve compressed decoding") {
      for ((encoding, compressed) <- Seq(
        ", GZip, ," -> gzipBytes(body),
        ", DeFlAtE, ," -> deflateBytes(body),
        ", gzip, , deflate, ," -> deflateBytes(gzipBytes(body))
      )) {
        val o = scenario(List(fixed(
          compressed.length.toString,
          compressed,
          Seq("Content-Encoding" -> encoding)
        )))
        successfulDownloadIs(o, body)
      }
    }

    test("empty encoding list elements preserve length validation") {
      for (encoding <- Seq("", ", identity, ,")) {
        val o = scenario(List(fixed("9", body, Seq("Content-Encoding" -> encoding))))
        downloadErrorIs(o, incompleteMessage(o.url, 9L, 6L))
      }
    }

    test("empty encoding list elements preserve saved prefix handling") {
      for (encoding <- Seq("", ", identity, ,")) {
        val reply = partial(Some("bytes 3-5/6"), Some("3"), body.drop(3))
        val o = scenario(
          List(reply.copy(headers = reply.headers :+ ("Content-Encoding" -> encoding))),
          prefix = body.take(3)
        )
        successfulDownloadIs(o, body)
      }
    }

    test("unknown encodings cannot publish response bytes") {
      for (encoding <- Seq("custom", "br", "gzip, custom")) {
        val o = scenario(
          List(fixed("6", body, Seq("Content-Encoding" -> encoding))),
          prefix = body.take(3)
        )
        downloadErrorIs(o, unsupportedMessage(o.url, encoding))
      }
    }

    test("mixed case deflate is decoded") {
      val output  = new ByteArrayOutputStream()
      val deflate = new java.util.zip.DeflaterOutputStream(output)
      deflate.write(body)
      deflate.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "DeFlAtE")
      )))
      successfulDownloadIs(o, body)
    }

    test("chunked gzip validates the closing chunk after its trailer") {
      val compressed = gzipBytes(body)
      val o = scenario(List(response(
        "200 OK",
        Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "GZip"),
        List(chunk(compressed, closing = false))
      )))
      downloadErrorIs(o, chunkedMessage(o.url, compressed.length))
    }

    test("valid chunked gzip is decoded through the closing chunk") {
      val o = scenario(List(response(
        "200 OK",
        Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "gZiP"),
        List(chunk(gzipBytes(body)))
      )))
      successfulDownloadIs(o, body)
    }

    test("complete framing cannot hide an incomplete gzip trailer") {
      val compressed = gzipBytes(body).dropRight(4)
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "gzip")
      )))
      downloadErrorIs(
        o,
        s"Invalid Content-Encoding 'gzip' from ${o.url}: expected a complete decoded body, received ${compressed.length} response bytes (java.io.EOFException)"
      )
    }

    test("invalid gzip header removes a saved temporary prefix") {
      val o =
        scenario(List(fixed("6", body, Seq("Content-Encoding" -> "gzip"))), prefix = body.take(3))
      downloadErrorIs(
        o,
        s"Invalid Content-Encoding 'gzip' from ${o.url}: expected a complete decoded body, received 2 response bytes (Not in GZIP format)"
      )
    }

    test("encoded resume cannot append decoded bytes to an earlier representation") {
      val compressed = gzipBytes(body.drop(3))
      val o = scenario(
        List(response(
          "206 Partial Content",
          Seq(
            "Content-Length"   -> compressed.length.toString,
            "Content-Range"    -> s"bytes 3-${compressed.length + 2}/${compressed.length + 3}",
            "Content-Encoding" -> "gzip"
          ),
          List(compressed)
        )),
        prefix = body.take(3)
      )
      downloadErrorIs(
        o,
        s"Cannot resume encoded response from ${o.url}: expected identity encoding for saved prefix of 3 bytes, received 'gzip'"
      )
    }

    test("gzip decoder completion still checks the declared response length") {
      val compressed = gzipBytes(body)
      val o = scenario(List(fixed(
        (compressed.length + 5).toString,
        compressed,
        Seq("Content-Encoding" -> "gzip")
      )))
      downloadErrorIs(o, incompleteMessage(o.url, compressed.length + 5, compressed.length))
    }

    test("deflate decoder completion still checks the declared response length") {
      val compressed = deflateBytes(body)
      val o = scenario(List(fixed(
        (compressed.length + 5).toString,
        compressed,
        Seq("Content-Encoding" -> "deflate")
      )))
      downloadErrorIs(o, incompleteMessage(o.url, compressed.length + 5, compressed.length))
    }

    test("chunked deflate validates the closing chunk after decoding") {
      val compressed = deflateBytes(body)
      val o = scenario(List(response(
        "200 OK",
        Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "deflate"),
        List(chunk(compressed, closing = false))
      )))
      downloadErrorIs(o, chunkedMessage(o.url, compressed.length))
    }

    test("valid chunked deflate is decoded through the closing chunk") {
      val o = scenario(List(response(
        "200 OK",
        Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "deflate"),
        List(chunk(deflateBytes(body)))
      )))
      successfulDownloadIs(o, body)
    }

    test("mixed case stacked content codings decode in reverse order") {
      val compressed = deflateBytes(gzipBytes(body))
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "GZip, DeFlAtE")
      )))
      successfulDownloadIs(o, body)
    }

    test("corrupt gzip trailer cannot publish decoded data") {
      val compressed = gzipBytes(body)
      compressed(compressed.length - 1) = 0xff.toByte
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "gzip")
      )))
      downloadErrorIs(
        o,
        s"Invalid Content-Encoding 'gzip' from ${o.url}: expected a complete decoded body, received ${compressed.length} response bytes (Corrupt GZIP trailer)"
      )
    }

    test("corrupt deflate checksum cannot publish decoded data") {
      val compressed = deflateBytes(body)
      compressed(compressed.length - 1) = (compressed.last ^ 0xff).toByte
      val o = scenario(List(fixed(
        compressed.length.toString,
        compressed,
        Seq("Content-Encoding" -> "deflate")
      )))
      downloadErrorIs(
        o,
        s"Invalid Content-Encoding 'deflate' from ${o.url}: expected a complete decoded body, received ${compressed.length} response bytes (incorrect data check)"
      )
    }

    test("gzip members in separate chunks all decode before publication") {
      val release = new CountDownLatch(1)
      val reply = Reply(
        "200 OK",
        Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "gzip"),
        List(chunk(gzipBytes(body.take(3)), closing = false), chunk(gzipBytes(body.drop(3)))),
        Some(ProgressGate(3, release))
      )
      val o       = scenario(List(reply), inspectWhilePaused = Some((3, release)))
      val pending = o.pendingState.get
      assert(!pending.finalExists)
      assert(pending.temporaryExists)
      assert(pending.temporaryBytes.toSeq == body.take(3).toSeq)
      successfulDownloadIs(o, body)
    }

    test("empty encoded body with one byte buffer still validates its trailer") {
      val compressed = gzipBytes(Array.emptyByteArray)
      val o = scenario(
        List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "GZIP"))),
        cap = 1
      )
      successfulDownloadIs(o, Array.emptyByteArray)
    }

    test("body bearing successful statuses retain their complete artifact bytes") {
      for (code <- Seq(201, 202, 203, 207)) {
        val o = scenario(List(fixed("6", body, status = s"$code Complete Body")))
        successfulDownloadIs(o, body)
      }
    }

    test("encoded download output error is not reported as a coding error") {
      val compressed = gzipBytes(body)
      val o = scenario(
        List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "gzip"))),
        temporaryDirectory = true
      )
      downloadErrorIs(
        o,
        o.outputErrorMessage.get
      )
    }

    test("HTTP error responses retain their complete original download error") {
      val o = scenario(
        List(response("500 Internal Server Error", Seq("Content-Length" -> "0"), Nil)),
        prefix = body.take(3)
      )
      downloadErrorIs(
        o,
        s"Caught java.io.IOException (Server returned HTTP response code: 500 for URL: ${o.url}) while downloading ${o.url}"
      )
    }

    test("close delimited response reports progress before its next body fragment") {
      val release = new CountDownLatch(1)
      val reply =
        Reply("200 OK", Nil, List(body.take(3), body.drop(3)), Some(ProgressGate(3, release)))
      val o       = scenario(List(reply), inspectWhilePaused = Some((3, release)))
      val pending = o.pendingState.get
      assert(!pending.finalExists)
      assert(pending.temporaryExists)
      assert(pending.temporaryBytes.toSeq == body.take(3).toSeq)
      successfulDownloadIs(o, body)
    }

    test("unfollowed redirects retain their complete response body") {
      for (code <- Seq(301, 302, 303, 307, 308)) {
        val o = scenario(List(fixed("6", body, status = s"$code Redirect Body")))
        successfulDownloadIs(o, body)
      }
    }
  }
}
