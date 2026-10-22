package coursier.cache

import java.io.{FilterInputStream, InputStream}
import java.net.{InetSocketAddress, URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import com.sun.net.httpserver.HttpServer
import coursier.util.{Artifact, Task}
import utest._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer

object DownloadBufferCapacityTests extends TestSuite {
  private final case class ReadObservation(
    advertisedLength: Long,
    requestedLength: Int,
    arrayLength: Int
  )

  val tests = Tests {
    test("known tiny response does not request an oversized transfer buffer") {
      var directory: Path = null
      var server: HttpServer = null
      var serverStarted = false
      var serverWorkers: ExecutorService = null
      var downloadWorkers: ExecutorService = null
      val openStreams = ConcurrentHashMap.newKeySet[InputStream]()
      val reads = ListBuffer.empty[ReadObservation]
      val openedStreams = new AtomicInteger()
      val closedStreams = new AtomicInteger()
      val servedResponses = new AtomicInteger()
      val expectedBody = Array[Byte](11, 22, 33)

      def closeStreams(streams: List[InputStream], index: Int = 0): Unit =
        if (index < streams.length)
          try streams(index).close()
          finally closeStreams(streams, index + 1)

      try {
        directory = Files.createTempDirectory("download-buffer-capacity")
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        serverWorkers = Executors.newFixedThreadPool(2)
        downloadWorkers = Executors.newFixedThreadPool(2)
        server.setExecutor(serverWorkers)
        server.createContext(
          "/artifact",
          exchange =>
            try {
              servedResponses.incrementAndGet()
              exchange.getResponseHeaders.set("Content-Type", "application/octet-stream")
              exchange.sendResponseHeaders(200, expectedBody.length.toLong)
              exchange.getResponseBody.write(expectedBody)
            }
            finally exchange.close()
        )
        server.start()
        serverStarted = true

        val protocol = "tinybuffer" + UUID.randomUUID().toString.replace("-", "")
        val handlerFactory = new URLStreamHandlerFactory {
          override def createURLStreamHandler(requestedProtocol: String): URLStreamHandler =
            if (requestedProtocol != protocol) null
            else
              new URLStreamHandler {
                override protected def openConnection(url: URL): URLConnection = {
                  val delegate = new URL("http", url.getHost, url.getPort, url.getFile).openConnection()
                  new URLConnection(url) {
                    private var exposedStream: InputStream = null

                    override def connect(): Unit =
                      if (!connected) {
                        delegate.connect()
                        connected = true
                      }

                    override def getHeaderField(name: String): String = delegate.getHeaderField(name)
                    override def getContentLengthLong(): Long = delegate.getContentLengthLong()
                    override def getLastModified(): Long = delegate.getLastModified()

                    override def getInputStream(): InputStream = {
                      if (exposedStream != null) exposedStream
                      else {
                        connect()
                        val advertisedLength = delegate.getContentLengthLong
                        val observed = new FilterInputStream(delegate.getInputStream) {
                          private val countedClose = new AtomicBoolean()

                          override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
                            reads.synchronized {
                              reads += ReadObservation(advertisedLength, length, bytes.length)
                            }
                            super.read(bytes, offset, length)
                          }

                          override def close(): Unit =
                            try super.close()
                            finally {
                              if (countedClose.compareAndSet(false, true)) closedStreams.incrementAndGet()
                              openStreams.remove(this)
                            }
                        }
                        openedStreams.incrementAndGet()
                        openStreams.add(observed)
                        exposedStream = observed
                        observed
                      }
                    }
                  }
                }
              }
        }

        val cache = FileCache[Task](directory.toFile)
          .withPool(downloadWorkers)
          .withChecksums(Seq(None))
          .withCustomHandlerFactory(Some(handlerFactory))
        val artifactUrl = s"$protocol://127.0.0.1:${server.getAddress.getPort}/artifact"
        val downloaded = Await.result(
          cache.file(Artifact(artifactUrl)).run.future()(cache.ec),
          30.seconds
        ).fold(error => throw new Exception(error), identity)

        assert(Files.readAllBytes(downloaded.toPath).toSeq == expectedBody.toSeq)
        assert(servedResponses.get() > 0)
        assert(openedStreams.get() > 0)
        assert(closedStreams.get() == openedStreams.get())
        assert(openStreams.isEmpty)

        val observedReads = reads.synchronized { reads.toList }
        assert(observedReads.nonEmpty)
        assert(observedReads.forall(_.advertisedLength == expectedBody.length.toLong))
        observedReads.foreach { read =>
          assert(read.requestedLength <= expectedBody.length)
          assert(read.arrayLength <= expectedBody.length)
        }
      }
      finally {
        try closeStreams(openStreams.toArray(new Array[InputStream](0)).toList)
        finally {
          try if (serverStarted) server.stop(0)
          finally {
            try {
              if (serverWorkers != null) {
                serverWorkers.shutdownNow()
                assert(serverWorkers.awaitTermination(10, TimeUnit.SECONDS))
              }
            }
            finally {
              try {
                if (downloadWorkers != null) {
                  downloadWorkers.shutdownNow()
                  assert(downloadWorkers.awaitTermination(10, TimeUnit.SECONDS))
                }
              }
              finally if (directory != null) os.remove.all(os.Path(directory))
            }
          }
        }
      }
    }
  }
}
