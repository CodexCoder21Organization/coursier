package coursier.cache

import java.io.{ByteArrayOutputStream, FilterInputStream, InputStream}
import java.net.{HttpURLConnection, InetSocketAddress, URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ExecutorService, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

import com.sun.net.httpserver.HttpServer
import coursier.util.{Artifact, Task}
import coursier.paths.CachePath
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

  private final case class ScenarioObservation(
    downloadedBody: Array[Byte],
    servedResponses: Int,
    openedStreams: Int,
    closedStreams: Int,
    remainingStreams: Int,
    reads: List[ReadObservation]
  )

  val tests = Tests {
    def downloadScenario(
      responseBody: Array[Byte],
      responseContentLength: Option[Long],
      contentEncoding: Option[String],
      configuredBufferSize: Option[Int]
    ): ScenarioObservation = {
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
              contentEncoding.foreach(value => exchange.getResponseHeaders.set("Content-Encoding", value))
              responseContentLength match {
                case Some(0L) => exchange.sendResponseHeaders(200, -1L)
                case Some(length) => exchange.sendResponseHeaders(200, length)
                case None => exchange.sendResponseHeaders(200, 0L)
              }
              if (responseBody.nonEmpty) exchange.getResponseBody.write(responseBody)
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
                    override def getContentEncoding(): String = delegate.getContentEncoding()
                    override def getLastModified(): Long = delegate.getLastModified()

                    override def getInputStream(): InputStream = {
                      if (exposedStream != null) exposedStream
                      else {
                        connect()
                        val advertisedLength = delegate.getContentLengthLong()
                        val observed = new FilterInputStream(delegate.getInputStream()) {
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

        val baseCache = FileCache[Task](directory.toFile)
          .withPool(downloadWorkers)
          .withChecksums(Seq(None))
          .withCustomHandlerFactory(Some(handlerFactory))
        val cache = configuredBufferSize match {
          case Some(size) => baseCache.withBufferSize(size)
          case None => baseCache
        }
        val artifactUrl = s"$protocol://127.0.0.1:${server.getAddress.getPort}/artifact"
        val downloaded = Await.result(
          cache.file(Artifact(artifactUrl)).run.future()(cache.ec),
          30.seconds
        ).fold(error => throw new Exception(error), identity)

        ScenarioObservation(
          Files.readAllBytes(downloaded.toPath),
          servedResponses.get(),
          openedStreams.get(),
          closedStreams.get(),
          openStreams.size(),
          reads.synchronized { reads.toList }
        )
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

    def assertScenario(
      observation: ScenarioObservation,
      expectedBody: Array[Byte],
      expectedAdvertisedLength: Long,
      maximumReadSize: Int
    ): Unit = {
      assert(observation.downloadedBody.toSeq == expectedBody.toSeq)
      assert(observation.servedResponses > 0)
      assert(observation.openedStreams > 0)
      assert(observation.closedStreams == observation.openedStreams)
      assert(observation.remainingStreams == 0)
      val observedReads = observation.reads
      assert(observedReads.nonEmpty)
      assert(observedReads.forall(_.advertisedLength == expectedAdvertisedLength))
      observedReads.foreach { read =>
        assert(read.requestedLength <= maximumReadSize)
        assert(read.arrayLength <= maximumReadSize)
      }
    }

    def gzip(body: Array[Byte]): Array[Byte] = {
      val output = new ByteArrayOutputStream()
      val gzipOutput = new GZIPOutputStream(output)
      try gzipOutput.write(body)
      finally gzipOutput.close()
      output.toByteArray
    }

    test("known tiny response does not request an oversized transfer buffer") {
      val expectedBody = Array[Byte](11, 22, 33)
      val observation = downloadScenario(
        expectedBody,
        Some(expectedBody.length.toLong),
        None,
        None
      )
      assertScenario(
        observation,
        expectedBody,
        expectedBody.length.toLong,
        expectedBody.length
      )
    }

    test("known empty response keeps read buffers at one byte or less") {
      val expectedBody = Array.empty[Byte]
      val observation = downloadScenario(expectedBody, Some(0L), None, None)
      assertScenario(observation, expectedBody, 0L, 1)
    }

    test("known body larger than configured maximum uses at most that maximum") {
      val configuredMaximum = 8192
      val expectedBody = Array.tabulate[Byte](configuredMaximum * 2 + 1)(index => (index % 251).toByte)
      val observation = downloadScenario(
        expectedBody,
        Some(expectedBody.length.toLong),
        None,
        Some(configuredMaximum)
      )
      assert(expectedBody.length > configuredMaximum * 2)
      assertScenario(observation, expectedBody, expectedBody.length.toLong, configuredMaximum)
    }

    test("unknown chunked response retains the default maximum") {
      val expectedBody = Array.tabulate[Byte](CacheDefaults.bufferSize + 17)(index => (index % 251).toByte)
      val observation = downloadScenario(expectedBody, None, None, None)
      assertScenario(observation, expectedBody, -1L, CacheDefaults.bufferSize)
    }

    test("gzip response retains the configured maximum while returning decoded bytes") {
      val configuredMaximum = 8192
      val expectedBody = Array.fill[Byte](32 * 1024)(65.toByte)
      val compressedBody = gzip(expectedBody)
      assert(compressedBody.length < expectedBody.length)
      val observation = downloadScenario(
        compressedBody,
        Some(compressedBody.length.toLong),
        Some("gzip"),
        Some(configuredMaximum)
      )
      assertScenario(observation, expectedBody, compressedBody.length.toLong, configuredMaximum)
    }

    test("partial response resumes from the persisted prefix with a bounded read buffer") {
      val prefix = Array[Byte](11, 22, 33)
      val suffix = Array[Byte](44, 55, 66)
      val expectedBody = prefix ++ suffix
      var directory: Path = null
      var server: HttpServer = null
      var serverStarted = false
      var serverWorkers: ExecutorService = null
      var downloadWorkers: ExecutorService = null
      val openStreams = ConcurrentHashMap.newKeySet[InputStream]()
      val reads = ListBuffer.empty[ReadObservation]
      val openedStreams = new AtomicInteger()
      val closedStreams = new AtomicInteger()
      val headRequests = new AtomicInteger()
      val getRequests = new AtomicInteger()
      val observedRange = new AtomicReference[String]()
      val lengthEvents = ListBuffer.empty[(Long, Long, Boolean)]
      val progressEvents = ListBuffer.empty[Long]

      def closeStreams(streams: List[InputStream], index: Int = 0): Unit =
        if (index < streams.length)
          try streams(index).close()
          finally closeStreams(streams, index + 1)

      try {
        directory = Files.createTempDirectory("download-buffer-resume")
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
        serverWorkers = Executors.newFixedThreadPool(2)
        downloadWorkers = Executors.newFixedThreadPool(2)
        server.setExecutor(serverWorkers)
        server.createContext(
          "/artifact",
          exchange =>
            try {
              exchange.getResponseHeaders.set("Content-Type", "application/octet-stream")
              if (exchange.getRequestMethod == "HEAD") {
                headRequests.incrementAndGet()
                exchange.getResponseHeaders.set("Content-Length", expectedBody.length.toString)
                exchange.sendResponseHeaders(200, -1L)
              }
              else {
                getRequests.incrementAndGet()
                observedRange.set(exchange.getRequestHeaders.getFirst("Range"))
                exchange.getResponseHeaders.set("Content-Range", "bytes 3-5/6")
                exchange.getResponseHeaders.set("Content-Length", suffix.length.toString)
                exchange.sendResponseHeaders(206, suffix.length.toLong)
                exchange.getResponseBody.write(suffix)
              }
            }
            finally exchange.close()
        )
        server.start()
        serverStarted = true

        val protocol = "resumebuffer" + UUID.randomUUID().toString.replace("-", "")
        val handlerFactory = new URLStreamHandlerFactory {
          override def createURLStreamHandler(requestedProtocol: String): URLStreamHandler =
            if (requestedProtocol != protocol) null
            else
              new URLStreamHandler {
                override protected def openConnection(url: URL): URLConnection = {
                  val delegate = new URL("http", url.getHost, url.getPort, url.getFile)
                    .openConnection()
                    .asInstanceOf[HttpURLConnection]
                  new HttpURLConnection(url) {
                    private var exposedInputStream: InputStream = null
                    private var exposedErrorStream: InputStream = null

                    private def observe(stream: InputStream): InputStream =
                      if (stream == null) null
                      else {
                        val observed = new FilterInputStream(stream) {
                          private val countedClose = new AtomicBoolean()

                          override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
                            reads.synchronized {
                              reads += ReadObservation(delegate.getContentLengthLong, length, bytes.length)
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
                        observed
                      }

                    override def connect(): Unit = {
                      delegate.connect()
                      connected = true
                    }

                    override def disconnect(): Unit = delegate.disconnect()
                    override def usingProxy(): Boolean = delegate.usingProxy()
                    override def getResponseCode: Int = delegate.getResponseCode
                    override def getResponseMessage: String = delegate.getResponseMessage
                    override def getHeaderField(name: String): String = delegate.getHeaderField(name)
                    override def getHeaderField(index: Int): String = delegate.getHeaderField(index)
                    override def getHeaderFieldKey(index: Int): String = delegate.getHeaderFieldKey(index)
                    override def getHeaderFields: java.util.Map[String, java.util.List[String]] = delegate.getHeaderFields
                    override def getContentLength: Int = delegate.getContentLength
                    override def getContentLengthLong: Long = delegate.getContentLengthLong
                    override def getContentEncoding: String = delegate.getContentEncoding
                    override def getLastModified: Long = delegate.getLastModified
                    override def getErrorStream: InputStream = {
                      if (exposedErrorStream == null)
                        exposedErrorStream = observe(delegate.getErrorStream)
                      exposedErrorStream
                    }
                    override def getInputStream: InputStream = {
                      if (exposedInputStream == null)
                        exposedInputStream = observe(delegate.getInputStream)
                      exposedInputStream
                    }
                    override def getRequestMethod: String = delegate.getRequestMethod
                    override def setRequestMethod(method: String): Unit = delegate.setRequestMethod(method)
                    override def getRequestProperty(key: String): String = delegate.getRequestProperty(key)
                    override def getRequestProperties: java.util.Map[String, java.util.List[String]] =
                      delegate.getRequestProperties
                    override def setRequestProperty(key: String, value: String): Unit =
                      delegate.setRequestProperty(key, value)
                    override def addRequestProperty(key: String, value: String): Unit =
                      delegate.addRequestProperty(key, value)
                    override def getInstanceFollowRedirects: Boolean = delegate.getInstanceFollowRedirects
                    override def setInstanceFollowRedirects(follow: Boolean): Unit =
                      delegate.setInstanceFollowRedirects(follow)
                    override def getConnectTimeout: Int = delegate.getConnectTimeout
                    override def setConnectTimeout(timeout: Int): Unit = delegate.setConnectTimeout(timeout)
                    override def getReadTimeout: Int = delegate.getReadTimeout
                    override def setReadTimeout(timeout: Int): Unit = delegate.setReadTimeout(timeout)
                    override def getUseCaches: Boolean = delegate.getUseCaches
                    override def setUseCaches(useCaches: Boolean): Unit = delegate.setUseCaches(useCaches)
                    override def getDoInput: Boolean = delegate.getDoInput
                    override def setDoInput(doInput: Boolean): Unit = delegate.setDoInput(doInput)
                    override def getDoOutput: Boolean = delegate.getDoOutput
                    override def setDoOutput(doOutput: Boolean): Unit = delegate.setDoOutput(doOutput)
                  }
                }
              }
        }

        val artifactUrl = s"$protocol://127.0.0.1:${server.getAddress.getPort}/artifact"
        val logger = new CacheLogger {
          override def downloadLength(
            url: String,
            totalLength: Long,
            alreadyDownloaded: Long,
            watching: Boolean
          ): Unit = lengthEvents.synchronized {
            lengthEvents += ((totalLength, alreadyDownloaded, watching))
          }

          override def downloadProgress(url: String, downloaded: Long): Unit =
            progressEvents.synchronized { progressEvents += downloaded }
        }
        val cache = FileCache[Task](directory.toFile)
          .withPool(downloadWorkers)
          .withChecksums(Seq(None))
          .withCustomHandlerFactory(Some(handlerFactory))
          .withLogger(logger)
        val finalFile = cache.localFile(artifactUrl)
        Files.createDirectories(finalFile.toPath.getParent)
        val temporaryFile = CachePath.temporaryFile(finalFile)
        Files.write(temporaryFile.toPath, prefix)

        val downloaded = Await.result(
          cache.file(Artifact(artifactUrl)).run.future()(cache.ec),
          30.seconds
        ).fold(error => throw new Exception(error), identity)

        assert(Files.readAllBytes(downloaded.toPath).toSeq == expectedBody.toSeq)
        assert(Files.exists(finalFile.toPath))
        assert(!Files.exists(temporaryFile.toPath))
        assert(headRequests.get() > 0)
        assert(getRequests.get() == 1)
        assert(observedRange.get() == "bytes=3-")
        assert(openedStreams.get() > 0)
        assert(closedStreams.get() == openedStreams.get())
        assert(openStreams.isEmpty)
        val observedReads = reads.synchronized { reads.toList }
        assert(observedReads.nonEmpty)
        assert(observedReads.forall(_.advertisedLength == suffix.length.toLong))
        observedReads.foreach { read =>
          assert(read.requestedLength <= suffix.length)
          assert(read.arrayLength <= suffix.length)
        }
        assert(lengthEvents.synchronized { lengthEvents.contains((6L, 3L, false)) })
        assert(progressEvents.synchronized { progressEvents.contains(6L) })
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
