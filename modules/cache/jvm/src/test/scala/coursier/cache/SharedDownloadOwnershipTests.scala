package coursier.cache

import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{CountDownLatch, Executors, LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger

import com.sun.net.httpserver.HttpServer
import utest._

import scala.collection.mutable.ListBuffer

/** Download ownership across processes sharing one cache directory.
  *
  * Each download caller runs in its own JVM ([[SharedDownloadOwnershipProcess]]), because the
  * ownership being tested is coordinated through file locks, which a JVM does not contend for
  * against itself. The test HTTP server holds and releases responses to force one exact ordering,
  * and records every request so the test can observe which processes download.
  */
object SharedDownloadOwnershipTests extends TestSuite {

  private def described(bytes: Array[Byte]): String = {
    val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    s"OK ${bytes.length} bytes, SHA-256 ${sha256.map(b => f"${b & 0xff}%02x").mkString}"
  }

  private def nextEvent(events: LinkedBlockingQueue[String], awaited: String): String = {
    val event = events.poll(60, TimeUnit.SECONDS)
    if (event == null)
      throw new java.lang.AssertionError(
        s"The test server saw no request within 60 seconds while waiting for $awaited"
      )
    event
  }

  val tests = Tests {
    test("a process arriving after a failed owner waits for the current owner") {
      val directory     = Files.createTempDirectory("shared-download-ownership")
      val cacheLocation = directory.resolve("cache")
      val body          = Array.tabulate[Byte](4096)(i => (i * 31 + 7).toByte)
      val server        = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      val serverWorkers = Executors.newCachedThreadPool()
      // Every request the server receives, in order: "HEAD" or "GET <n>".
      val events          = new LinkedBlockingQueue[String]
      val gets            = new AtomicInteger
      val getsInFlight    = new AtomicInteger
      val maxGetsInFlight = new AtomicInteger
      val releaseFirst    = new CountDownLatch(1)
      val releaseSecond   = new CountDownLatch(1)
      val processes       = ListBuffer.empty[(String, Process, Path)]
      server.setExecutor(serverWorkers)
      server.createContext(
        "/artifact",
        exchange =>
          try
            if (exchange.getRequestMethod == "HEAD") {
              events.put("HEAD")
              exchange.getResponseHeaders.add("Content-Length", body.length.toString)
              exchange.sendResponseHeaders(200, -1)
            }
            else {
              val inFlight = getsInFlight.incrementAndGet()
              maxGetsInFlight.accumulateAndGet(inFlight, Math.max)
              try {
                val get = gets.incrementAndGet()
                events.put(s"GET $get")
                get match {
                  case 1 =>
                    // The first owner's download fails.
                    releaseFirst.await()
                    exchange.sendResponseHeaders(500, -1)
                  case 2 =>
                    releaseSecond.await()
                    exchange.sendResponseHeaders(200, body.length)
                    exchange.getResponseBody.write(body)
                  case _ =>
                    exchange.sendResponseHeaders(200, body.length)
                    exchange.getResponseBody.write(body)
                }
              }
              finally getsInFlight.decrementAndGet()
            }
          finally exchange.close()
      )
      server.start()
      val url = s"http://127.0.0.1:${server.getAddress.getPort}/artifact"

      def start(name: String): Unit = {
        val output = directory.resolve(s"$name.out")
        val java   = new File(new File(System.getProperty("java.home"), "bin"), "java").getPath
        val process = new ProcessBuilder(
          java,
          "-cp",
          System.getProperty("java.class.path"),
          SharedDownloadOwnershipProcess.getClass.getName.stripSuffix("$"),
          cacheLocation.toString,
          url
        )
          .redirectErrorStream(true)
          .redirectOutput(output.toFile)
          .start()
        processes += ((name, process, output))
      }

      def outcome(name: String): (Int, String) = {
        val (_, process, output) = processes.find(_._1 == name).get
        assert(process.waitFor(120, TimeUnit.SECONDS))
        (process.exitValue(), new String(Files.readAllBytes(output), StandardCharsets.UTF_8).trim)
      }

      try {
        // A becomes the owner; its response is held.
        start("A")
        assert(nextEvent(events, "the first download request") == "GET 1")

        // B opens the coordination file A holds and waits; its first wait iteration probes the
        // artifact length with a HEAD request, which only B can send at this point.
        start("B")
        assert(nextEvent(events, "the waiting process's length probe") == "HEAD")

        // A's download fails and A gives up ownership. B becomes the owner; its response is held.
        releaseFirst.countDown()
        var event = nextEvent(events, "the second download request")
        while (event == "HEAD")
          event = nextEvent(events, "the second download request")
        assert(event == "GET 2")
        // Every request so far came from A or B, and B is now inside its download.
        events.clear()

        // C arrives while B is downloading. It must wait for B (a HEAD length probe from its
        // wait loop), never start a second download of the same file.
        start("C")
        val cFirstRequest = nextEvent(events, "the third process's first request")
        if (cFirstRequest != "HEAD")
          throw new java.lang.AssertionError(
            s"A process that arrived while another process owned the download started its own download " +
              s"of the same cache file (server request: $cFirstRequest, downloads in progress at once: " +
              s"${maxGetsInFlight.get()}); at most one process may download a given file at a time"
          )

        releaseSecond.countDown()
        val (aExit, aOutput) = outcome("A")
        val (bExit, bOutput) = outcome("B")
        val (cExit, cOutput) = outcome("C")
        assert(aExit == 1)
        assert(
          aOutput == s"ERROR download error: Caught java.io.IOException (Server returned HTTP response code: 500 for URL: $url) while downloading $url"
        )
        assert(bExit == 0)
        assert(bOutput == described(body))
        assert(cExit == 0)
        assert(cOutput == described(body))
        assert(gets.get() == 2)
        assert(maxGetsInFlight.get() == 1)
      }
      finally {
        releaseFirst.countDown()
        releaseSecond.countDown()
        for ((name, process, output) <- processes) {
          if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(30, TimeUnit.SECONDS)
          }
          if (Files.exists(output))
            System.err.println(s"process $name output: " + new String(
              Files.readAllBytes(output),
              StandardCharsets.UTF_8
            ).trim)
        }
        server.stop(0)
        serverWorkers.shutdownNow()
        assert(serverWorkers.awaitTermination(10, TimeUnit.SECONDS))
        os.remove.all(os.Path(directory))
      }
    }
  }
}
