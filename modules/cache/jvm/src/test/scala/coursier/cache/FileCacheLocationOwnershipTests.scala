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

object FileCacheLocationOwnershipTests extends TestSuite {
  val tests = Tests {
    test("independent locations") {
      val directory = Files.createTempDirectory("cache-location-ownership")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val serverWorkers = Executors.newFixedThreadPool(2)
      val downloadWorkers = Executors.newFixedThreadPool(4)
      val ownerEntered = new CountDownLatch(1)
      val releaseOwner = new CountDownLatch(1)
      val secondEntered = new CountDownLatch(1)
      val requests = new AtomicInteger()
      server.setExecutor(serverWorkers)
      server.createContext("/artifact", exchange => {
        try {
          if (exchange.getRequestMethod == "HEAD") {
            exchange.getResponseHeaders.add("Content-Length", "1")
            exchange.sendResponseHeaders(200, -1)
          }
          else {
            val request = requests.incrementAndGet()
            if (request == 1) {
              ownerEntered.countDown()
              releaseOwner.await()
            }
            else secondEntered.countDown()
            val status = 200
            exchange.sendResponseHeaders(status, 1)
            exchange.getResponseBody.write(request)
          }
        }
        finally exchange.close()
      })
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val firstCache = FileCache[Task](
          location = directory.resolve("first").toFile,
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
          retry = 5,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0
        )
        val secondCache = FileCache[Task](
          location = directory.resolve("second").toFile,
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
          retry = 5,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0
        )

        val first = firstCache.file(artifact).run.future()(firstCache.ec)
        assert(ownerEntered.await(30, TimeUnit.SECONDS))
        val second = secondCache.file(artifact).run.future()(secondCache.ec)
        // The first response remains held: another location owns independent work.
        assert(secondEntered.await(30, TimeUnit.SECONDS))
        val secondResult = Await.result(second, 30.seconds).fold(error => throw new Exception(error), identity)
        assert(Files.readAllBytes(secondResult.toPath).toSeq == Seq[Byte](2))
        assert(!first.isCompleted)
        releaseOwner.countDown()
        val firstResult = Await.result(first, 30.seconds).fold(error => throw new Exception(error), identity)
        assert(Files.readAllBytes(firstResult.toPath).toSeq == Seq[Byte](1))
        assert(firstResult.getCanonicalFile != secondResult.getCanonicalFile)
        assert(requests.get() == 2)
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
    test("independent locations after an owner error") {
      val directory = Files.createTempDirectory("cache-location-ownership")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val serverWorkers = Executors.newFixedThreadPool(2)
      val downloadWorkers = Executors.newFixedThreadPool(4)
      val ownerEntered = new CountDownLatch(1)
      val releaseOwner = new CountDownLatch(1)
      val secondEntered = new CountDownLatch(1)
      val requests = new AtomicInteger()
      server.setExecutor(serverWorkers)
      server.createContext("/artifact", exchange => {
        try {
          if (exchange.getRequestMethod == "HEAD") {
            exchange.getResponseHeaders.add("Content-Length", "1")
            exchange.sendResponseHeaders(200, -1)
          }
          else {
            val request = requests.incrementAndGet()
            if (request == 1) {
              ownerEntered.countDown()
              releaseOwner.await()
            }
            else secondEntered.countDown()
            val status = if (request == 1) 500 else 200
            exchange.sendResponseHeaders(status, 1)
            exchange.getResponseBody.write(request)
          }
        }
        finally exchange.close()
      })
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val firstCache = FileCache[Task](
          location = directory.resolve("first").toFile,
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
          retry = 5,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0
        )
        val secondCache = FileCache[Task](
          location = directory.resolve("second").toFile,
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
          retry = 5,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0
        )

        val first = firstCache.file(artifact).run.future()(firstCache.ec)
        assert(ownerEntered.await(30, TimeUnit.SECONDS))
        val second = secondCache.file(artifact).run.future()(secondCache.ec)
        // The first response remains held: another location owns independent work.
        assert(secondEntered.await(30, TimeUnit.SECONDS))
        val secondResult = Await.result(second, 30.seconds).fold(error => throw new Exception(error), identity)
        assert(Files.readAllBytes(secondResult.toPath).toSeq == Seq[Byte](2))
        assert(!first.isCompleted)
        releaseOwner.countDown()
        val firstResult = Await.result(first, 30.seconds)
        assert(firstResult.isLeft)
        assert(firstResult.left.toOption.get.describe ==
          s"download error: Caught java.io.IOException (Server returned HTTP response code: 500 for URL: ${artifact.url}) while downloading ${artifact.url}")
        assert(requests.get() == 2)
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
    test("same location joins its owner") {
      val directory = Files.createTempDirectory("cache-location-ownership")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val serverWorkers = Executors.newFixedThreadPool(2)
      val downloadWorkers = Executors.newFixedThreadPool(4)
      val ownerEntered = new CountDownLatch(1)
      val releaseOwner = new CountDownLatch(1)
      val secondEntered = new CountDownLatch(1)
      val requests = new AtomicInteger()
      server.setExecutor(serverWorkers)
      server.createContext("/artifact", exchange => {
        try {
          if (exchange.getRequestMethod == "HEAD") {
            exchange.getResponseHeaders.add("Content-Length", "1")
            exchange.sendResponseHeaders(200, -1)
          }
          else {
            val request = requests.incrementAndGet()
            if (request == 1) {
              ownerEntered.countDown()
              releaseOwner.await()
            }
            else secondEntered.countDown()
            val status = 200
            exchange.sendResponseHeaders(status, 1)
            exchange.getResponseBody.write(request)
          }
        }
        finally exchange.close()
      })
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val firstLocation = Files.createDirectory(directory.resolve("first"))
        val aliasLocation = Files.createSymbolicLink(directory.resolve("alias"), firstLocation)
        val firstCache = FileCache[Task](
          location = firstLocation.toFile,
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
          retry = 5,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0
        )
        val secondCache = FileCache[Task](
          location = aliasLocation.toFile,
          cachePolicies = Seq(CachePolicy.FetchMissing),
          checksums = Seq(None),
          credentials = Nil,
          pool = downloadWorkers,
          ttl = Some(24.hours),
          localArtifactsShouldBeCached = false,
          followHttpToHttpsRedirections = true,
          followHttpsToHttpRedirections = false,
          maxRedirections = Some(20),
          sslRetry = 5,
          sslSocketFactoryOpt = None,
          hostnameVerifierOpt = None,
          retry = 5,
          bufferSize = 1024 * 1024,
          classLoaders = Nil,
          customHandlerFactory = None,
          clock = java.time.Clock.systemUTC(),
          retryBackoffInitialDelay = 10.milliseconds,
          retryBackoffMultiplier = 2.0,
          logger = new CacheLogger {
            override def downloadLength(
              url: String,
              totalLength: Long,
              alreadyDownloaded: Long,
              watching: Boolean
            ): Unit =
              if (watching) secondEntered.countDown()
          }
        )
        val firstDestination = firstCache.localFile(artifact.url)
        val secondDestination = secondCache.localFile(artifact.url)
        assert(firstDestination.getAbsolutePath != secondDestination.getAbsolutePath)
        assert(firstDestination.getCanonicalFile == secondDestination.getCanonicalFile)
        val first = firstCache.file(artifact).run.future()(firstCache.ec)
        assert(ownerEntered.await(30, TimeUnit.SECONDS))
        val second = secondCache.file(artifact).run.future()(secondCache.ec)
        // watching=true is emitted inside checkDownload, after ownership contention.
        // Keep the response held until that same-file wait is actually observed.
        assert(secondEntered.await(30, TimeUnit.SECONDS))
        assert(!first.isCompleted)
        assert(!second.isCompleted)
        assert(requests.get() == 1)
        releaseOwner.countDown()
        val firstResult = Await.result(first, 30.seconds).fold(error => throw new Exception(error), identity)
        val secondResult = Await.result(second, 30.seconds).fold(error => throw new Exception(error), identity)
        assert(firstResult.getCanonicalFile == secondResult.getCanonicalFile)
        assert(Files.readAllBytes(firstResult.toPath).toSeq == Seq[Byte](1))
        assert(Files.readAllBytes(secondResult.toPath).toSeq == Seq[Byte](1))
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
