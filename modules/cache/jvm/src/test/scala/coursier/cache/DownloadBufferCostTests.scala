package coursier.cache

import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpServer
import coursier.util.{Artifact, Task}
import utest._

object DownloadBufferCostTests extends TestSuite {
  val tests = Tests {
    test("small bodies do not initialize the buffer element by element") {
      val entries = scala.collection.mutable.LinkedHashSet.empty[String]
      var loader: ClassLoader = getClass.getClassLoader
      while (loader != null) {
        loader match {
          case urls: java.net.URLClassLoader =>
            urls.getURLs.foreach(url => entries += new java.io.File(url.toURI).getAbsolutePath)
          case _ =>
        }
        loader = loader.getParent
      }
      System.getProperty("java.class.path").split(java.io.File.pathSeparator)
        .foreach(entries += _)
      val process = new ProcessBuilder(
        new java.io.File(System.getProperty("java.home"), "bin/java").getAbsolutePath,
        "-XX:TieredStopAtLevel=1", "-cp", entries.mkString(java.io.File.pathSeparator),
        "coursier.cache.DownloadBufferCostProbe"
      ).redirectErrorStream(true).start()
      try {
        val output = scala.io.Source.fromInputStream(process.getInputStream)
        val text = try output.mkString finally output.close()
        val exit = process.waitFor()
        println(text)
        assert(exit == 0)
      }
      finally {
        if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      }
    }
  }
}

object DownloadBufferCostProbe {
  def main(args: Array[String]): Unit = {
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val workers = Executors.newCachedThreadPool()
      val pool = Executors.newFixedThreadPool(2)
      val directory = Files.createTempDirectory("download-buffer-cost")
      val operatingSystem = ManagementFactory.getOperatingSystemMXBean
        .asInstanceOf[com.sun.management.OperatingSystemMXBean]
      server.setExecutor(workers)
      server.createContext("/", exchange => {
        try {
          exchange.sendResponseHeaders(200, 1)
          exchange.getResponseBody.write(42)
        }
        finally exchange.close()
      })
      server.start()
      try {
        val base = FileCache[Task](directory.toFile).withPool(pool).withChecksums(Seq(None))
        var sequence = 0
        def fetch(bufferSize: Int): Unit = {
          sequence += 1
          val artifact = Artifact(s"http://127.0.0.1:${server.getAddress.getPort}/$sequence")
          val cache = base.withBufferSize(bufferSize)
          val file = cache.file(artifact).run.unsafeRun(wrapExceptions = true)(cache.ec)
            .fold(error => throw new Exception(error), identity)
          assert(Files.readAllBytes(file.toPath).toSeq == Seq[Byte](42))
        }
        for (_ <- 0 until 16) { fetch(8192); fetch(1024 * 1024) }
        def measure(bufferSize: Int): Long = {
          val start = operatingSystem.getProcessCpuTime
          for (_ <- 0 until 64) fetch(bufferSize)
          operatingSystem.getProcessCpuTime - start
        }
        val small = measure(8192)
        val large = measure(1024 * 1024)
        println(s"DOWNLOAD_BUFFER_CPU smallNs=$small largeNs=$large ratio=${large.toDouble / small}")
        assert(large < small * 4)
      }
      finally {
        server.stop(0)
        workers.shutdownNow()
        pool.shutdownNow()
        assert(workers.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        assert(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        os.remove.all(os.Path(directory))
      }
  }
}
