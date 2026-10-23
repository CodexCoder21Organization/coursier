package coursier.cache

import java.net.{InetAddress, InetSocketAddress}
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.zip.{DeflaterOutputStream, GZIPOutputStream}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import coursier.paths.CachePath
import coursier.util.{Artifact, Task}
import scala.concurrent.Await
import scala.concurrent.duration._
import utest._

object Rc8StreamingTests extends TestSuite {
  private def streamed(coding: String, mib: Int): Unit = {
    val directory = Files.createTempDirectory("rc8-streaming")
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
    val serving = Executors.newSingleThreadExecutor()
    val downloading = Executors.newFixedThreadPool(2)
    val position = new AtomicLong(0)
    val peakHeap = new AtomicLong(0)
    val requestCount = new AtomicLong(0)
    val serverDone = new CountDownLatch(1)
    val expectedDigest = new AtomicReference[Array[Byte]]()
    val lock = new Object
    val endStates = scala.collection.mutable.ListBuffer.empty[Boolean]
    val logger = new CacheLogger {
      override def downloadProgress(url: String, downloaded: Long): Unit = lock.synchronized {
        assert(downloaded > position.get())
        position.set(downloaded)
        val heap = Runtime.getRuntime.totalMemory() - Runtime.getRuntime.freeMemory()
        peakHeap.set(math.max(peakHeap.get(), heap))
        lock.notifyAll()
      }
      override def downloadedArtifact(url: String, success: Boolean): Unit = lock.synchronized {
        assert(position.get() == mib.toLong * 1024 * 1024)
        endStates += success
      }
    }
    val url = s"http://127.0.0.1:${server.getAddress.getPort}/artifact"
    val cache = FileCache[Task](directory.toFile).withPool(downloading).withChecksums(Seq(None))
      .withRetry(0).withBufferSize(16384).withLogger(logger)
    val finalFile = cache.localFile(url)
    val temporaryFile = CachePath.temporaryFile(finalFile)
    server.setExecutor(serving)
    server.createContext("/artifact", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        requestCount.incrementAndGet()
        try {
          exchange.getResponseHeaders.add("Content-Encoding", coding)
          exchange.sendResponseHeaders(200, 0)
          val wire = exchange.getResponseBody
          val out: java.io.OutputStream = coding match {
            case "gzip" => new GZIPOutputStream(wire, true)
            case "deflate, gzip" => new DeflaterOutputStream(new GZIPOutputStream(wire, true), true)
            case "gzip, deflate" => new GZIPOutputStream(new DeflaterOutputStream(wire, true), true)
            case "identity" => wire
          }
          val digest = MessageDigest.getInstance("SHA-256")
          val block = new Array[Byte](8192)
          val random = new java.util.Random(71426L)
          for (part <- 1 to mib) {
            for (_ <- 0 until 128) {
              random.nextBytes(block)
              digest.update(block)
              out.write(block)
            }
            out.flush()
            val target = part.toLong * 1024 * 1024 - 32768
            lock.synchronized {
              val deadline = System.nanoTime() + 30.seconds.toNanos
              while (position.get() < target && System.nanoTime() < deadline)
                lock.wait(math.max(1L, (deadline-System.nanoTime())/1000000))
              assert(position.get() >= target)
              assert(!finalFile.exists())
              assert(temporaryFile.length() >= target)
            }
          }
          out.close()
          expectedDigest.set(digest.digest())
        }
        finally { exchange.close(); serverDone.countDown() }
      }
    })
    val started = System.nanoTime()
    try {
      server.start()
      val result = Await.result(cache.file(Artifact(url)).run.future()(cache.ec), 30.seconds)
      assert(result.isRight)
      assert(serverDone.await(30, TimeUnit.SECONDS))
      assert(requestCount.get() == 1)
      assert(finalFile.length() == mib.toLong * 1024 * 1024)
      assert(!temporaryFile.exists())
      assert(endStates.toList == List(true))
      val digest = MessageDigest.getInstance("SHA-256")
      val input = Files.newInputStream(finalFile.toPath)
      try {
        val block = new Array[Byte](8192)
        var n = input.read(block)
        while (n >= 0) { digest.update(block, 0, n); n = input.read(block) }
      }
      finally input.close()
      assert(java.util.Arrays.equals(digest.digest(), expectedDigest.get()))
      println(s"RC8-STREAM coding=$coding mib=$mib seconds=${(System.nanoTime()-started)/1e9} peakUsedHeap=${peakHeap.get()} finalProgress=${position.get()}")
    }
    finally {
      server.stop(0)
      serving.shutdownNow(); downloading.shutdownNow()
      assert(serving.awaitTermination(10, TimeUnit.SECONDS))
      assert(downloading.awaitTermination(10, TimeUnit.SECONDS))
      os.remove.all(os.Path(directory))
    }
  }
  val tests = Tests {
    test("gzip streams large responses into the cache") { for (n <- Seq(8, 64)) streamed("gzip", n) }
    test("deflate inside gzip streams large responses into the cache") { for (n <- Seq(8, 64)) streamed("deflate, gzip", n) }
    test("gzip inside deflate streams large responses into the cache") { for (n <- Seq(8, 64)) streamed("gzip, deflate", n) }
    test("identity streams large responses into the cache") { for (n <- Seq(8, 64)) streamed("identity", n) }
  }
}
