package coursier.cache

import java.io.ByteArrayOutputStream
import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import java.nio.charset.StandardCharsets.{ISO_8859_1, UTF_8}
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, ExecutorService, Executors, TimeUnit}

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
    private val lock = new Object
    private val progress0 = ListBuffer.empty[Long]
    private val downloadResults0 = ListBuffer.empty[(String, Boolean)]
    private val waiters = ListBuffer.empty[(Long, CountDownLatch)]

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

  private def rawResponse(reply: Reply): Array[Byte] = {
    val headerBytes =
      (s"HTTP/1.1 ${reply.status}\r\n" +
        reply.headers.map { case (key, value) => s"$key: $value\r\n" }.mkString +
        "Connection: close\r\n\r\n").getBytes(ISO_8859_1)
    headerBytes ++ reply.chunks.flatten
  }

  private def readRequest(socket: Socket): String = {
    val input = socket.getInputStream
    val bytes = new ByteArrayOutputStream()
    var tail = 0
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
    var directory: Path = null
    var server: ServerSocket = null
    var serverWorker: ExecutorService = null
    var downloadWorkers: ExecutorService = null
    var serving: Future[Unit] = null
    val stopping = new java.util.concurrent.atomic.AtomicBoolean(false)
    val sockets = ConcurrentHashMap.newKeySet[Socket]()
    val requests = ListBuffer.empty[String]
    val logger = new RecordingLogger()

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
        try
          while (!stopping.get()) {
            val socket = server.accept()
            sockets.add(socket)
            try {
              val requestText = readRequest(socket)
              requests.synchronized(requests += requestText)
              val reply =
                if (requestIndex < replies.length) replies(requestIndex)
                else fallbackReply
              requestIndex += 1
              val output = socket.getOutputStream
              output.write(rawResponse(reply))
              output.flush()
              reply.pauseAfterFirstChunk.foreach { gate =>
                if (reply.chunks.nonEmpty && !logger.awaitProgressAtLeast(gate.atLeast, 30.seconds))
                  throw new IllegalStateException(
                    s"Timed out waiting for download progress to reach ${gate.atLeast}"
                  )
                if (!gate.release.await(30, TimeUnit.SECONDS))
                  throw new IllegalStateException("Timed out waiting for the test to release the response")
              }
            }
            finally {
              socket.close()
              sockets.remove(socket)
            }
          }
        catch {
          case _: SocketException if stopping.get() => ()
        }
      }(serverExecutionContext)

      val url = s"http://127.0.0.1:${server.getLocalPort}/artifact"
      val cache = FileCache[Task](directory.toFile)
        .withPool(downloadWorkers)
        .withChecksums(Seq(None))
        .withRetry(0)
        .withBufferSize(cap)
        .withLogger(logger)
      val finalFile = cache.localFile(url)
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
        val state = fileState(finalFile, temporaryFile)
        release.countDown()
        state
      }

      val result = Await.result(downloading, 30.seconds).map(file => Files.readAllBytes(file.toPath))

      stopServer()
      Await.result(serving, 30.seconds)

      val observedRequests = requests.synchronized(requests.toList)
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
      stopServer()
      sockets.toArray(new Array[Socket](0)).foreach(_.close())
      if (serving != null)
        Await.result(serving, 30.seconds)
      if (serverWorker != null) {
        serverWorker.shutdownNow()
        assert(serverWorker.awaitTermination(10, TimeUnit.SECONDS))
      }
      if (downloadWorkers != null) {
        downloadWorkers.shutdownNow()
        assert(downloadWorkers.awaitTermination(10, TimeUnit.SECONDS))
      }
      if (directory != null) os.remove.all(os.Path(directory))
    }
  }

  private def bytesAre(observation: Observation, expected: Array[Byte]): Unit =
    assert(observation.result.map(_.toSeq) == Right(expected.toSeq))

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

  val tests = Tests {
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
  }
}

