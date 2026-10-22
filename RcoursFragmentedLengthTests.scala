package coursier.cache

import java.io.ByteArrayOutputStream
import java.net.{InetAddress, ServerSocket, Socket}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import coursier.util.{Artifact, Task}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import utest._

object RcoursFragmentedLengthTests extends TestSuite {
  val tests = Tests {
    test("understated_fragmented_body_must_not_read_past_length") {
      val directory = Files.createTempDirectory("rcours-paused")
      val server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
      val socket = new AtomicReference[Socket]()
      val serverWorker = Executors.newSingleThreadExecutor()
      val downloadWorkers = Executors.newFixedThreadPool(2)
      val receivedBody = new CountDownLatch(1)
      val releaseEof = new CountDownLatch(1)
      implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(serverWorker)
      val serving = Future {
        val accepted = server.accept()
        socket.set(accepted)
        try {
          val input = accepted.getInputStream
          var tail = 0
          while (tail != 0x0d0a0d0a) {
            val value = input.read()
            require(value >= 0, "Request ended before its headers")
            tail = (tail << 8) | value
          }
          accepted.getOutputStream.write(
            "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nab".getBytes(UTF_8)
          )
          accepted.getOutputStream.flush()
          assert(releaseEof.await(30, TimeUnit.SECONDS))
          accepted.getOutputStream.write("cdef".getBytes(UTF_8))
          accepted.getOutputStream.flush()
        } finally accepted.close()
      }
      try {
        val logger = new CacheLogger {
          override def downloadProgress(url: String, downloaded: Long): Unit =
            if (downloaded == 2L) receivedBody.countDown()
        }
        val url = s"http://127.0.0.1:${server.getLocalPort}/artifact"
        val cache = FileCache[Task](directory.toFile).withPool(downloadWorkers)
          .withChecksums(Seq(None)).withRetry(0).withLogger(logger)
        val running = cache.file(Artifact(url)).run.future()(cache.ec)
        assert(receivedBody.await(30, TimeUnit.SECONDS))
        assert(!running.isCompleted)
        println("RCOURS fragmented body: two bytes copied before the rest is released")
        releaseEof.countDown()
        val result = Await.result(running, 30.seconds)
        Await.result(serving, 30.seconds)
        println("RCOURS fragmented completion: " + result.map(file => Files.size(file.toPath)))
        assert(result.map(file => Files.readAllBytes(file.toPath).toSeq) == Right("abc".getBytes(UTF_8).toSeq))
      } finally {
        releaseEof.countDown()
        server.close()
        if (socket.get() != null) socket.get().close()
        serverWorker.shutdownNow()
        downloadWorkers.shutdownNow()
        assert(serverWorker.awaitTermination(10, TimeUnit.SECONDS))
        assert(downloadWorkers.awaitTermination(10, TimeUnit.SECONDS))
        os.remove.all(os.Path(directory))
      }
    }
  }
}
