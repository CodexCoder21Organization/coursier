package coursier.cache

import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.{CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import com.sun.net.httpserver.HttpServer
import coursier.util.{Artifact, Task}
import utest._

import scala.concurrent.Await
import scala.concurrent.duration._

object FileCacheRetrySettingsTests extends TestSuite {
  val tests = Tests {
    test("explicit retry settings govern same-file waiting") {
      val directory       = Files.createTempDirectory("cache-retry-settings")
      val server          = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val serverWorkers   = Executors.newFixedThreadPool(2)
      val downloadWorkers = Executors.newFixedThreadPool(4)
      val ownerEntered    = new CountDownLatch(1)
      val releaseOwner    = new CountDownLatch(1)
      val waiterEntered   = new CountDownLatch(1)
      val waiterCompleted = new CountDownLatch(1)
      val waitingChecks   = new AtomicInteger()
      val requests        = new AtomicInteger()
      server.setExecutor(serverWorkers)
      server.createContext(
        "/artifact",
        exchange =>
          try
            if (exchange.getRequestMethod == "HEAD") {
              exchange.getResponseHeaders.add("Content-Length", "3")
              exchange.sendResponseHeaders(200, -1)
            }
            else {
              requests.incrementAndGet()
              ownerEntered.countDown()
              releaseOwner.await()
              exchange.sendResponseHeaders(200, 3)
              exchange.getResponseBody.write(Array[Byte](1, 2, 3))
            }
          finally exchange.close()
      )
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val cache = FileCache[Task](
          location = directory.resolve("cache").toFile,
          cachePolicies = Seq(CachePolicy.FetchMissing),
          checksums = Seq(None),
          credentials = Nil,
          logger = CacheLogger.nop,
          pool = downloadWorkers,
          ttl = Some(24.hours),
          localArtifactsShouldBeCached = false,
          followHttpToHttpsRedirections = true,
          followHttpsToHttpRedirections = false,
          maxRedirections = Some(20),
          sslRetry = 5,
          sslSocketFactoryOpt = None,
          hostnameVerifierOpt = None,
          retry = 2,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 1.millisecond,
          retryBackoffMultiplier = 1.0
        )
        val waiterCache = cache.withLogger(new CacheLogger {
          override def downloadLength(
            url: String,
            totalLength: Long,
            alreadyDownloaded: Long,
            watching: Boolean
          ): Unit =
            if (watching && waitingChecks.incrementAndGet() == 3) waiterEntered.countDown()
        })

        val owner = cache.file(artifact).run.future()(cache.ec)
        assert(ownerEntered.await(30, TimeUnit.SECONDS))
        val waiter = waiterCache.file(artifact).run.future()(waiterCache.ec)
        // Three checks require both the configured initial delay and its multiplier.
        // Keep the owner held until those public waiter events have occurred.
        assert(waiterEntered.await(30, TimeUnit.SECONDS))
        assert(!owner.isCompleted)
        assert(!waiter.isCompleted)
        assert(requests.get() == 1)
        releaseOwner.countDown()
        waiter.onComplete(_ => waiterCompleted.countDown())(waiterCache.ec)
        assert(waiterCompleted.await(30, TimeUnit.SECONDS))
        val ownerFile = Await.result(owner, 30.seconds)
          .fold(error => throw new Exception(error), identity)
        val waiterFile = Await.result(waiter, 30.seconds)
          .fold(error => throw new Exception(error), identity)
        assert(ownerFile.getCanonicalFile == waiterFile.getCanonicalFile)
        assert(Files.readAllBytes(ownerFile.toPath).toSeq == Seq[Byte](1, 2, 3))
        assert(Files.readAllBytes(waiterFile.toPath).toSeq == Seq[Byte](1, 2, 3))
        assert(requests.get() == 1)
      }
      finally {
        releaseOwner.countDown()
        server.stop(0)
        downloadWorkers.shutdownNow()
        serverWorkers.shutdownNow()
        assert(downloadWorkers.awaitTermination(10, TimeUnit.SECONDS))
        assert(serverWorkers.awaitTermination(10, TimeUnit.SECONDS))
        os.remove.all(os.Path(directory))
      }
    }
  }
}
