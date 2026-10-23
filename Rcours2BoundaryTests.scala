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
object Rcours2BoundaryTests extends TestSuite {
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
    allResults: List[Either[ArtifactError, Array[Byte]]]
  )

  private final class RecordingLogger(clientCount: Int) extends CacheLogger {
    val downloadingCalls = new CountDownLatch(clientCount)
    override def downloadingArtifact(url: String, artifact: Artifact): Unit = downloadingCalls.countDown()

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
    joinBeforeBody: Boolean = false
  ): Observation = {
    var directory: Path                  = null
    var server: ServerSocket             = null
    var serverWorker: ExecutorService    = null
    var downloadWorkers: ExecutorService = null
    var serving: Future[Unit]            = null
    val stopping                         = new java.util.concurrent.atomic.AtomicBoolean(false)
    val sockets                          = ConcurrentHashMap.newKeySet[Socket]()
    val requests                         = ListBuffer.empty[String]
    val logger                           = new RecordingLogger(clientCount)

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
      serverExecutionContext = ExecutionContext.fromExecutor(serverWorker)

      val fallbackReply = fixed(body.length.toString, body)
      serving = Future {
        var requestIndex = 0
        while (!stopping.get()) {
          val accepted =
            try Some(server.accept())
            catch {
              // Closing the listener releases the accept loop during teardown.
              case _: SocketException if stopping.get() && server.isClosed => None
            }
          accepted.foreach { socket =>
            sockets.add(socket)
            try {
              val requestText = readRequest(socket)
              requests.synchronized(requests += requestText)
              val reply =
                if (requestIndex < replies.length) replies(requestIndex)
                else fallbackReply
              requestIndex += 1
              val output = socket.getOutputStream
              output.write(rawHeaders(reply))
              output.flush()
              if (joinBeforeBody)
                assert(logger.downloadingCalls.await(30, TimeUnit.SECONDS))
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
      if (prefix.nonEmpty) {
        Files.createDirectories(temporaryFile.toPath.getParent)
        Files.write(temporaryFile.toPath, prefix)
      }

      val downloads = List.fill(clientCount)(cache.file(Artifact(url)).run.future()(cache.ec))
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

      val results = downloads.map(f => Await.result(f, 30.seconds).map(file => Files.readAllBytes(file.toPath)))
      val result = results.head

      stopServer()
      Await.result(serving, 30.seconds)

      val observedRequests  = requests.synchronized(requests.toList)
      val observedFileState = fileState(finalFile, temporaryFile)
      println(s"RCOURS2 url=$url result=${result.left.toOption.map(_.message)} bytes=${result.toOption.map(_.length)} requests=${observedRequests.size} final=${observedFileState.finalExists} tmp=${observedFileState.temporaryExists} tmpBytes=${observedFileState.temporaryBytes.length}")
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
        results
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

  val tests = Tests {
    test("valid mixed case range unit completes the artifact") {
      val o = scenario(List(partial(Some("ByTeS 3-5/6"), Some("3"), body.drop(3))), prefix = body.take(3))
      successfulDownloadIs(o, body)
    }

    test("unsolicited 304 cannot publish an empty artifact") {
      val o = scenario(List(response("304 Not Modified", Nil, Nil)))
      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("cut off chunked response removes temporary bytes") {
      val wire = "6\r\nabcdef\r\n".getBytes(ISO_8859_1)
      val o = scenario(List(response("200 OK", Seq("Transfer-Encoding" -> "chunked"), List(wire))))
      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("cut off gzip response removes temporary bytes") {
      val output = new ByteArrayOutputStream()
      val gzip = new GZIPOutputStream(output)
      gzip.write(body)
      gzip.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(compressed.length.toString, compressed.dropRight(4), Seq("Content-Encoding" -> "gzip"))))
      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("short other encoded response cannot publish partial bytes") {
      val output = new ByteArrayOutputStream()
      val deflate = new java.util.zip.DeflaterOutputStream(output)
      deflate.write(body)
      deflate.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(compressed.length.toString, compressed.dropRight(2), Seq("Content-Encoding" -> "deflate"))))
      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("correct mixed case gzip decodes the body") {
      val output = new ByteArrayOutputStream()
      val gzip = new GZIPOutputStream(output)
      gzip.write(body)
      gzip.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "GZip"))))
      successfulDownloadIs(o, body)
    }

    test("chunked range obeys its content range without content length") {
      val wire = "3\r\ndef\r\n0\r\n\r\n".getBytes(ISO_8859_1)
      val o = scenario(List(response("206 Partial Content", Seq("Transfer-Encoding" -> "chunked", "Content-Range" -> "bytes 3-5/6"), List(wire))), prefix = body.take(3))
      successfulDownloadIs(o, body)
    }

    test("short chunked range cannot publish a partial artifact") {
      val wire = "2\r\nde\r\n0\r\n\r\n".getBytes(ISO_8859_1)
      val o = scenario(List(response("206 Partial Content", Seq("Transfer-Encoding" -> "chunked", "Content-Range" -> "bytes 3-5/6"), List(wire))), prefix = body.take(3))
      downloadErrorIs(o, incompleteMessage(o.url, 3L, 2L))
    }

    test("empty close delimited response is a valid empty artifact") {
      val o = scenario(List(response("200 OK", Nil, Nil)))
      successfulDownloadIs(o, Array.emptyByteArray)
    }

    test("correct other encoded body is not compared with a decoded length") {
      val output = new ByteArrayOutputStream()
      val deflate = new java.util.zip.DeflaterOutputStream(output)
      deflate.write(body)
      deflate.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "deflate"))))
      successfulDownloadIs(o, compressed)
    }

    test("concurrent callers never publish the same short artifact") {
      val o = scenario(List(fixed("9", body), fixed("9", body)), clientCount = 2, joinBeforeBody = true)
      assert(o.allResults.size == 2)
      assert(o.allResults.forall(_.isLeft))
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
      val server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/artifact", exchange => {
        assert(exchange.getRequestMethod == "HEAD")
        exchange.getResponseHeaders.set("Content-Length", "6")
        exchange.sendResponseHeaders(200, -1L)
        exchange.close()
      })
      server.start()
      var connection: java.net.HttpURLConnection = null
      try {
        val url = s"http://127.0.0.1:${server.getAddress.getPort}/artifact"
        connection = ConnectionBuilder(url).withMethod("HEAD").connection().asInstanceOf[java.net.HttpURLConnection]
        assert(connection.getResponseCode == 200)
        assert(connection.getContentLengthLong == 6L)
        assert(connection.getInputStream.read() == -1)
      }
      finally {
        if (connection != null) connection.disconnect()
        server.stop(0)
      }
    }
  }
}
