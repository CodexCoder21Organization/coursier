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
object DownloadLengthIntegrityTests extends TestSuite {
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
    pendingState: Option[FileState]
  )

  private final class RecordingLogger extends CacheLogger {
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
    inspectWhilePaused: Option[(Long, CountDownLatch)] = None
  ): Observation = {
    var directory: Path                  = null
    var server: ServerSocket             = null
    var serverWorker: ExecutorService    = null
    var downloadWorkers: ExecutorService = null
    var serving: Future[Unit]            = null
    val stopping                         = new java.util.concurrent.atomic.AtomicBoolean(false)
    val sockets                          = ConcurrentHashMap.newKeySet[Socket]()
    val requests                         = ListBuffer.empty[String]
    val logger                           = new RecordingLogger()

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

      val downloading = cache.file(Artifact(url)).run.future()(cache.ec)
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

      val result =
        Await.result(downloading, 30.seconds).map(file => Files.readAllBytes(file.toPath))

      stopServer()
      Await.result(serving, 30.seconds)

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
        pendingState
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
    test("zero buffer size is rejected before creating a temporary file") {
      val observation = scenario(List(fixed("6")), cap = 0)
      downloadErrorIs(
        observation,
        s"Invalid download buffer size 0 for ${observation.url}: expected a positive size"
      )
      assert(observation.requests.size == 1)
    }

    test("negative buffer size is rejected before creating a temporary file") {
      val observation = scenario(List(fixed("6")), cap = -1)
      downloadErrorIs(
        observation,
        s"Invalid download buffer size -1 for ${observation.url}: expected a positive size"
      )
      assert(observation.requests.size == 1)
    }

    test("short fixed-length response does not publish a partial artifact") {
      val observation = scenario(List(fixed("9", body.take(6))))

      downloadErrorIs(observation, incompleteMessage(observation.url, 9L, 6L))
      assert(observation.requests.size == 1)
      assert(observation.progress.contains(6L))
    }

    test("empty body with positive fixed length is an incomplete response") {
      val observation = scenario(List(fixed("9", Array.emptyByteArray)))

      downloadErrorIs(observation, incompleteMessage(observation.url, 9L, 0L))
      assert(observation.requests.size == 1)
      assert(observation.progress.isEmpty)
    }

    test("partial body remains temporary until the connection reaches EOF") {
      val release = new CountDownLatch(1)
      val reply = fixed("9", body.take(6)).copy(
        pauseAfterFirstChunk = Some(ProgressGate(6L, release))
      )
      val observation = scenario(
        List(reply),
        inspectWhilePaused = Some((6L, release))
      )

      val pending = observation.pendingState.get
      assert(!pending.finalExists)
      assert(pending.temporaryExists)
      assert(pending.temporaryBytes.toSeq == body.take(6).toSeq)
      downloadErrorIs(observation, incompleteMessage(observation.url, 9L, 6L))
      assert(observation.requests.size == 1)
      assert(observation.progress.contains(6L))
    }

    test("exact fixed-length response publishes all bytes") {
      val observation = scenario(List(fixed("6")))

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
    }

    test("zero length response ignores trailing bytes") {
      val observation = scenario(List(fixed("0", body)))

      successfulDownloadIs(observation, Array.emptyByteArray)
      assert(observation.requests.size == 1)
      assert(observation.progress.isEmpty)
    }

    test("fragmented fixed-length response does not expose bytes after its length") {
      val gate = ProgressGate(2L, new CountDownLatch(0))
      val reply = fixed("3", body).copy(
        chunks = List(body.take(2), body.drop(2)),
        pauseAfterFirstChunk = Some(gate)
      )
      val observation = scenario(List(reply))

      successfulDownloadIs(observation, body.take(3))
      assert(observation.requests.size == 1)
      assert(observation.progress.nonEmpty)
      assert(observation.progress.forall(_ <= 3L))
      assert(observation.progress.last == 3L)
    }

    test("two byte buffer stays within the framed length after a fragmented read") {
      val gate = ProgressGate(1L, new CountDownLatch(0))
      val reply = fixed("3", body).copy(
        chunks = List(body.take(1), body.drop(1)),
        pauseAfterFirstChunk = Some(gate)
      )
      val observation = scenario(List(reply), cap = 2)

      successfulDownloadIs(observation, body.take(3))
      assert(observation.requests.size == 1)
      assert(observation.progress.nonEmpty)
      assert(observation.progress.forall(_ <= 3L))
      assert(observation.progress.last == 3L)
    }

    test("close-delimited response reads through connection close") {
      val observation = scenario(List(response("200 OK", Nil, List(body))))

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
      assert(observation.progress.last == body.length.toLong)
    }

    test("chunked framing takes precedence over a conflicting content length") {
      val chunks = "3\r\nabc\r\n3\r\ndef\r\n0\r\n\r\n".getBytes(ISO_8859_1)
      val reply = response(
        "200 OK",
        Seq("Transfer-Encoding" -> "chunked", "Content-Length" -> "1"),
        List(chunks)
      )
      val observation = scenario(List(reply), cap = 17)

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
      assert(observation.progress.last == body.length.toLong)
    }

    test("gzip known length decodes fully with bounded progress increments") {
      val expected = Array.fill[Byte](32768)(65.toByte)
      val output   = new ByteArrayOutputStream()
      val gzip     = new GZIPOutputStream(output)
      gzip.write(expected)
      gzip.close()
      val compressed = output.toByteArray
      assert(compressed.length < 8192)

      val observation = scenario(
        List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "gzip"))),
        cap = 8192
      )

      successfulDownloadIs(observation, expected)
      assert(observation.requests.size == 1)
      assert(observation.progress.last == expected.length.toLong)
      val deltas = (0L +: observation.progress).zip(observation.progress).map {
        case (before, after) => after - before
      }
      assert(deltas.nonEmpty)
      assert(deltas.contains(8192L))
      assert(deltas.forall(_ <= 8192L))
    }

    test("mixed case identity encoding is accepted") {
      val observation = scenario(
        List(fixed("6", body, Seq("Content-Encoding" -> "IdEnTiTy")))
      )

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
    }

    test("mixed case identity encoding still enforces a short length") {
      val observation = scenario(
        List(fixed("9", body.take(6), Seq("Content-Encoding" -> "IdEnTiTy")))
      )

      downloadErrorIs(observation, incompleteMessage(observation.url, 9L, 6L))
      assert(observation.requests.size == 1)
    }

    test("negative content length is rejected") {
      val value       = "-1"
      val observation = scenario(List(fixed(value, body.take(6))))

      downloadErrorIs(observation, invalidContentLengthMessage(observation.url, value))
      assert(observation.requests.size == 1)
    }

    test("non-decimal content length is rejected") {
      val value       = "abc"
      val observation = scenario(List(fixed(value, body.take(6))))

      downloadErrorIs(observation, invalidContentLengthMessage(observation.url, value))
      assert(observation.requests.size == 1)
    }

    test("overflowing content length is rejected") {
      val value       = "9223372036854775808"
      val observation = scenario(List(fixed(value, body.take(6))))

      downloadErrorIs(observation, invalidContentLengthMessage(observation.url, value))
      assert(observation.requests.size == 1)
    }

    test("empty content length is rejected") {
      val value       = ""
      val observation = scenario(List(fixed(value, body.take(6))))

      downloadErrorIs(observation, invalidContentLengthMessage(observation.url, value))
      assert(observation.requests.size == 1)
    }

    test("content length at Int max reports the received byte count") {
      val expected    = Int.MaxValue.toLong
      val observation = scenario(List(fixed(expected.toString, body)))

      downloadErrorIs(observation, incompleteMessage(observation.url, expected, 6L))
      assert(observation.requests.size == 1)
    }

    test("content length above Int max reports the received byte count") {
      val expected    = Int.MaxValue.toLong + 1L
      val observation = scenario(List(fixed(expected.toString, body)))

      downloadErrorIs(observation, incompleteMessage(observation.url, expected, 6L))
      assert(observation.requests.size == 1)
    }

    test("five gigabyte content length reports the received byte count") {
      val expected    = 5L * 1024L * 1024L * 1024L
      val observation = scenario(List(fixed(expected.toString, body)))

      downloadErrorIs(observation, incompleteMessage(observation.url, expected, 6L))
      assert(observation.requests.size == 1)
    }

    test("Long max content length reports the received byte count") {
      val expected    = Long.MaxValue
      val observation = scenario(List(fixed(expected.toString, body)))

      downloadErrorIs(observation, incompleteMessage(observation.url, expected, 6L))
      assert(observation.requests.size == 1)
    }

    test("redirect body length does not replace the final response length") {
      val redirect = response(
        "302 Found",
        Seq("Location" -> "/final", "Content-Length" -> "1"),
        List(Array[Byte](120))
      )
      val observation = scenario(List(redirect, fixed("6")))

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 2)
      assert(observation.requests.head.startsWith("GET /artifact HTTP/1.1\r\n"))
      assert(observation.requests.last.startsWith("GET /final HTTP/1.1\r\n"))
    }

    test("short final response after redirect reports the original artifact URL") {
      val redirect = response(
        "302 Found",
        Seq("Location" -> "/final", "Content-Length" -> "1"),
        List(Array[Byte](120))
      )
      val observation = scenario(List(redirect, fixed("9", body)))

      downloadErrorIs(observation, incompleteMessage(observation.url, 9L, 6L))
      assert(observation.requests.size == 2)
      assert(observation.requests.head.startsWith("GET /artifact HTTP/1.1\r\n"))
      assert(observation.requests.last.startsWith("GET /final HTTP/1.1\r\n"))
    }

    test("valid range response resumes the existing prefix") {
      val observation = scenario(
        List(partial(Some("bytes 3-5/6"), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
      assert(observation.requests.head.toLowerCase.contains("range: bytes=3-"))
      assert(observation.progress.contains(6L))
    }

    test("valid range response can frame the suffix without content length") {
      val observation = scenario(
        List(partial(Some("bytes 3-5/6"), None, body.drop(3))),
        prefix = body.take(3)
      )

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
      assert(observation.requests.head.toLowerCase.contains("range: bytes=3-"))
      assert(observation.progress.contains(6L))
    }

    test("range response with an incorrect start is rejected without restarting") {
      val range = "bytes 2-5/6"
      val observation = scenario(
        List(partial(Some(range), Some("4"), body.drop(2))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
      assert(observation.requests.head.toLowerCase.contains("range: bytes=3-"))
    }

    test("range response without content range is rejected") {
      val observation = scenario(
        List(partial(None, Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, missingContentRangeMessage(observation.url, 3L))
      assert(observation.requests.size == 1)
      assert(observation.requests.head.toLowerCase.contains("range: bytes=3-"))
    }

    test("malformed content range is rejected with its exact value") {
      val range = "items 3-5/6"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("range end mismatch is rejected") {
      val range = "bytes 3-4/6"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("range total mismatch is rejected") {
      val range = "bytes 3-5/99"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("unknown range total is rejected") {
      val range = "bytes 3-5/*"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("range ending at or beyond the total is rejected") {
      val range = "bytes 3-6/6"
      val observation = scenario(
        List(partial(Some(range), Some("4"), body.drop(3) ++ Array[Byte](120))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("overflowing range start is rejected") {
      val range = "bytes 9223372036854775808-9223372036854775809/9223372036854775810"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("overflowing range end is rejected") {
      val range = "bytes 3-9223372036854775808/9223372036854775810"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("overflowing range total is rejected") {
      val range = "bytes 3-5/9223372036854775808"
      val observation = scenario(
        List(partial(Some(range), Some("3"), body.drop(3))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("content length conflicting with range is rejected") {
      val range = "bytes 3-5/6"
      val observation = scenario(
        List(partial(Some(range), Some("4"), "defx".getBytes(UTF_8))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 3L))
      assert(observation.requests.size == 1)
    }

    test("unsolicited range response must start at byte zero") {
      val range       = "bytes 3-5/6"
      val observation = scenario(List(partial(Some(range), Some("3"), body.drop(3))))

      downloadErrorIs(observation, invalidContentRangeMessage(observation.url, range, 0L))
      assert(observation.requests.size == 1)
      assert(!observation.requests.head.toLowerCase.contains("range:"))
    }

    test("range response from byte zero can satisfy a full download") {
      val observation = scenario(
        List(partial(Some("bytes 0-5/6"), Some("6"), body))
      )

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
      assert(!observation.requests.head.toLowerCase.contains("range:"))
    }

    test("server ignoring range with a full response replaces the prefix") {
      val observation = scenario(List(fixed("6", body)), prefix = body.take(3))

      successfulDownloadIs(observation, body)
      assert(observation.requests.size == 1)
      assert(observation.requests.head.toLowerCase.contains("range: bytes=3-"))
    }

    test("short range response with content length removes its temporary file") {
      val observation = scenario(
        List(partial(Some("bytes 3-5/6"), Some("3"), body.drop(3).take(2))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, incompleteMessage(observation.url, 3L, 2L))
      assert(observation.requests.size == 1)
      assert(observation.progress.contains(5L))
    }

    test("short range response uses content range length when content length is absent") {
      val observation = scenario(
        List(partial(Some("bytes 3-5/6"), None, body.drop(3).take(2))),
        prefix = body.take(3)
      )

      downloadErrorIs(observation, incompleteMessage(observation.url, 3L, 2L))
      assert(observation.requests.size == 1)
      assert(observation.progress.contains(5L))
    }
  }
}
