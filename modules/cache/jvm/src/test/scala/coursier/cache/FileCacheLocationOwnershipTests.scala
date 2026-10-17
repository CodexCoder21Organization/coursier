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
        finally exchange.close()
      })
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val firstCache = FileCache[Task](directory.resolve("first").toFile)
          .withPool(downloadWorkers).withChecksums(Seq(None))
        val secondCache = FileCache[Task](directory.resolve("second").toFile)
          .withPool(downloadWorkers).withChecksums(Seq(None))

        val first = firstCache.file(artifact).run.future()(firstCache.ec)
        assert(ownerEntered.await(30, TimeUnit.secondS))
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
        assert(downloadWorkers.awaitTermination(10, TimeUnit.secondS))
        assert(serverWorkers.awaitTermination(10, TimeUnit.secondS))
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
        finally exchange.close()
      })
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val firstCache = FileCache[Task](directory.resolve("first").toFile)
          .withPool(downloadWorkers).withChecksums(Seq(None))
        val secondCache = FileCache[Task](directory.resolve("second").toFile)
          .withPool(downloadWorkers).withChecksums(Seq(None))

        val first = firstCache.file(artifact).run.future()(firstCache.ec)
        assert(ownerEntered.await(30, TimeUnit.secondS))
        val second = secondCache.file(artifact).run.future()(secondCache.ec)
        // The first response remains held: another location owns independent work.
        assert(secondEntered.await(30, TimeUnit.SECONDS))
        val secondResult = Await.result(second, 30.seconds).fold(error => throw new Exception(error), identity)
        assert(Files.readAllBytes(secondResult.toPath).toSeq == Seq[Byte](2))
        assert(!first.isCompleted)
        releaseOwner.countDown()
        val firstResult = Await.result(first, 30.seconds)
        assert(firstResult.isLeft)
        assert(firstResult.left.toOption.get.describe.contains("500"))
        assert(requests.get() == 2)
      }
      finally {
        releaseOwner.countDown()
        server.stop(0)
        downloadWorkers.shutdownNow()
        serverWorkers.shutdownNow()
        assert(downloadWorkers.awaitTermination(10, TimeUnit.secondS))
        assert(serverWorkers.awaitTermination(10, TimeUnit.secondS))
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
        finally exchange.close()
      })
      server.start()
      try {
        val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/artifact")
        val firstCache = FileCache[Task](directory.resolve("first").toFile)
          .withPool(downloadWorkers).withChecksums(Seq(None))
        val secondCache = FileCache[Task](directory.resolve("first").toFile)
          .withPool(downloadWorkers).withChecksums(Seq(None))
          .withLogger(new CacheLogger {
            override def downloadingArtifact(url: String, artifact: Artifact): Unit =
              secondEntered.countDown()
          })
        val first = firstCache.file(artifact).run.future()(firstCache.ec)
        assert(ownerEntered.await(30, TimeUnit.firstS))
        val second = secondCache.file(artifact).run.future()(secondCache.ec)
        // A shared cache must still publish one file from one HTTP response.
        assert(secondEntered.await(30, TimeUnit.SECONDS))
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
        assert(downloadWorkers.awaitTermination(10, TimeUnit.firstS))
        assert(serverWorkers.awaitTermination(10, TimeUnit.firstS))
        os.remove.all(os.Path(directory))
      }
    }
  }
}
