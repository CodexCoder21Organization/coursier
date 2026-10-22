package coursier.cache

import java.io.{ByteArrayOutputStream, FilterInputStream, InputStream}
import java.net.{InetAddress, ServerSocket, Socket, URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.nio.charset.StandardCharsets.{ISO_8859_1, UTF_8}
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import java.util.zip.GZIPOutputStream
import coursier.paths.CachePath
import coursier.util.{Artifact, Task}
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import utest._

/** Real loopback HTTP responses, including headers HttpServer refuses to send. */
object RcoursResponseTests extends TestSuite {
  private val body = "abcdef".getBytes(UTF_8)
  private def response(status: String, headers: Seq[(String, String)], bytes: Array[Byte]): Array[Byte] =
    (s"HTTP/1.1 $status\r\n" + headers.map { case (k, v) => s"$k: $v\r\n" }.mkString +
      "Connection: close\r\n\r\n").getBytes(ISO_8859_1) ++ bytes

  private def fixed(length: Long, bytes: Array[Byte] = body, extra: Seq[(String,String)] = Nil): Array[Byte] =
    response("200 OK", Seq("Content-Length" -> length.toString) ++ extra, bytes)

  private final case class Observation(result: Either[ArtifactError, Array[Byte]], requests: List[String], arrays: List[Int])

  private def scenario(
    replies: List[Array[Byte]],
    prefix: Array[Byte] = Array.emptyByteArray,
    cap: Int = CacheDefaults.bufferSize,
    observe: Boolean = false
  ): Observation = {
    val directory = Files.createTempDirectory("rcours-response")
    val server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val workers = Executors.newFixedThreadPool(2)
    val serverWorker = Executors.newSingleThreadExecutor()
    val sockets = ConcurrentHashMap.newKeySet[Socket]()
    val requests = ListBuffer.empty[String]
    val arrays = ListBuffer.empty[Int]
    val streams = ConcurrentHashMap.newKeySet[InputStream]()
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.fromExecutor(serverWorker)
    val serving = Future {
      replies.foreach { reply =>
        val socket = server.accept()
        sockets.add(socket)
        try {
          val input = socket.getInputStream
          val request = new ByteArrayOutputStream()
          var tail = 0
          while (tail != 0x0d0a0d0a) {
            val next = input.read()
            require(next >= 0, "Client closed before request headers ended")
            request.write(next)
            tail = (tail << 8) | next
          }
          requests.synchronized(requests += request.toString("ISO-8859-1"))
          socket.getOutputStream.write(reply)
          socket.getOutputStream.flush()
        } finally {
          socket.close()
          sockets.remove(socket)
        }
      }
    }
    val protocol = "rcours" + UUID.randomUUID().toString.replace("-", "")
    val handler = new URLStreamHandlerFactory {
      override def createURLStreamHandler(name: String): URLStreamHandler =
        if (name != protocol) null
        else new URLStreamHandler {
          override protected def openConnection(url: URL): URLConnection = {
            val delegate = new URL("http", url.getHost, url.getPort, url.getFile).openConnection()
            new URLConnection(url) {
              private var stream: InputStream = null
              override def connect(): Unit = { delegate.connect(); connected = true }
              override def getContentLengthLong(): Long = delegate.getContentLengthLong
              override def getContentEncoding(): String = delegate.getContentEncoding
              override def getLastModified(): Long = delegate.getLastModified
              override def getHeaderField(name: String): String = delegate.getHeaderField(name)
              override def getInputStream(): InputStream = {
                if (stream == null) {
                  stream = new FilterInputStream(delegate.getInputStream) {
                    override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
                      arrays.synchronized(arrays += bytes.length)
                      super.read(bytes, offset, length)
                    }
                    override def close(): Unit = try super.close() finally streams.remove(this)
                  }
                  streams.add(stream)
                }
                stream
              }
            }
          }
        }
    }
    try {
      val url = s"${if (observe) protocol else "http"}://127.0.0.1:${server.getLocalPort}/artifact"
      val baseCache = FileCache[Task](directory.toFile).withPool(workers)
        .withChecksums(Seq(None)).withRetry(0).withBufferSize(cap)
      val cache = if (observe) baseCache.withCustomHandlerFactory(Some(handler)) else baseCache
      if (prefix.nonEmpty) {
        val target = CachePath.temporaryFile(cache.localFile(url))
        Files.createDirectories(target.toPath.getParent)
        Files.write(target.toPath, prefix)
      }
      val result = Await.result(cache.file(Artifact(url)).run.future()(cache.ec), 30.seconds)
        .map(file => Files.readAllBytes(file.toPath))
      Await.result(serving, 30.seconds)
      val observation = Observation(result, requests.synchronized(requests.toList), arrays.synchronized(arrays.toList))
      println("RCOURS observed result=" + result.map(_.map(b => f"${b & 255}%02x").mkString) +
        " requests=" + observation.requests.map(_.takeWhile(_ != '\r')).mkString(";") +
        " arrays=" + observation.arrays.distinct.mkString(","))
      observation
    } finally {
      server.close()
      sockets.toArray(new Array[Socket](0)).foreach(_.close())
      streams.toArray(new Array[InputStream](0)).foreach(_.close())
      serverWorker.shutdownNow()
      workers.shutdownNow()
      assert(serverWorker.awaitTermination(10, TimeUnit.SECONDS))
      assert(workers.awaitTermination(10, TimeUnit.SECONDS))
      os.remove.all(os.Path(directory))
    }
  }

  private def bytesAre(o: Observation, expected: Array[Byte]): Unit =
    assert(o.result.map(_.toSeq) == Right(expected.toSeq))

  private def bounded(o: Observation, expected: Int): Unit = {
    assert(o.arrays.nonEmpty)
    assert(o.arrays.forall(_ <= expected))
    assert(o.arrays.contains(expected))
  }

  val tests = Tests {
    test("overstated_length_must_not_publish_incomplete_body") {
      val o = scenario(List(fixed(9)))
      assert(o.result.isLeft)
    }
    test("understated_length_must_not_overread_framed_body") {
      val o = scenario(List(fixed(3)))
      bytesAre(o, body.take(3))
    }
    test("zero_length_with_trailing_body_must_stay_empty") {
      bytesAre(scenario(List(fixed(0))), Array.emptyByteArray)
    }
    test("chunked_without_length_reads_all_chunks") {
      val wire = "3\r\nabc\r\n3\r\ndef\r\n0\r\n\r\n".getBytes(UTF_8)
      val o = scenario(List(response("200 OK", Seq("Transfer-Encoding" -> "chunked"), wire)), observe = true)
      bytesAre(o, body)
      bounded(o, CacheDefaults.bufferSize)
    }
    test("chunked_overrides_conflicting_length") {
      val wire = "3\r\nabc\r\n3\r\ndef\r\n0\r\n\r\n".getBytes(UTF_8)
      bytesAre(scenario(List(response("200 OK", Seq("Transfer-Encoding" -> "chunked", "Content-Length" -> "1"), wire))), body)
    }
    test("close_delimited_unknown_length") {
      val o = scenario(List(response("200 OK", Nil, body)), observe = true)
      bytesAre(o, body)
      bounded(o, CacheDefaults.bufferSize)
    }
    test("unknown_content_encoding_keeps_cap") {
      val o = scenario(List(fixed(6, extra = Seq("Content-Encoding" -> "custom"))), cap = 17, observe = true)
      bytesAre(o, body)
      bounded(o, 17)
    }
    test("gzip_decodes_expansion_larger_than_wire") {
      val expected = Array.fill[Byte](32768)(65)
      val compressed = new ByteArrayOutputStream()
      val gzip = new GZIPOutputStream(compressed)
      gzip.write(expected)
      gzip.close()
      bytesAre(scenario(List(fixed(compressed.size().toLong, compressed.toByteArray, Seq("Content-Encoding" -> "gzip")))), expected)
    }
    test("redirect_uses_final_response_length") {
      val redirect = response("302 Found", Seq("Location" -> "/final", "Content-Length" -> "1"), Array[Byte](120))
      val o = scenario(List(redirect, fixed(6)))
      bytesAre(o, body)
      assert(o.requests.size == 2)
      assert(o.requests.last.startsWith("GET /final HTTP/1.1"))
    }
    test("wrong_range_start_restarts_without_appending") {
      val wrong = response("206 Partial Content", Seq("Content-Range" -> "bytes 2-5/6", "Content-Length" -> "4"), body.drop(2))
      val o = scenario(List(wrong, fixed(6)), prefix = body.take(3))
      bytesAre(o, body)
      assert(o.requests.head.contains("Range: bytes=3-"))
      assert(!o.requests.last.contains("Range:"))
    }
    test("wrong_range_end_must_not_publish_inconsistent_body") {
      val wrong = response("206 Partial Content", Seq("Content-Range" -> "bytes 3-4/6", "Content-Length" -> "3"), body.drop(3))
      val o = scenario(List(wrong), prefix = body.take(3))
      assert(o.result.isLeft)
    }
    test("wrong_range_total_must_not_publish_incomplete_artifact") {
      val wrong = response("206 Partial Content", Seq("Content-Range" -> "bytes 3-5/99", "Content-Length" -> "3"), body.drop(3))
      val o = scenario(List(wrong), prefix = body.take(3))
      assert(o.result.isLeft)
    }
    test("known_tiny_uses_exact_length") {
      val o = scenario(List(fixed(6)), observe = true)
      bytesAre(o, body)
      bounded(o, 6)
    }
    test("configured_cap_below_equal_and_above_length") {
      Seq(5, 6, 7).foreach { cap =>
        val o = scenario(List(fixed(6)), cap = cap, observe = true)
        bytesAre(o, body)
        bounded(o, math.min(6, cap))
      }
    }
    test("one_byte_cap_remains_positive") {
      val o = scenario(List(fixed(6)), cap = 1, observe = true)
      bytesAre(o, body)
      bounded(o, 1)
    }
    test("large_lengths_remain_capped_without_int_overflow") {
      Seq(Int.MaxValue.toLong, Int.MaxValue.toLong + 1, 5L * 1024 * 1024 * 1024, Long.MaxValue).foreach { length =>
        val o = scenario(List(fixed(length)), cap = 8192, observe = true)
        bytesAre(o, body)
        bounded(o, 8192)
      }
    }
    test("default_http_transport_uses_http_1_1") {
      val o = scenario(List(fixed(6)))
      bytesAre(o, body)
      assert(o.requests.head.startsWith("GET /artifact HTTP/1.1\r\n"))
      assert(!o.requests.head.contains("Upgrade:"))
      assert(!o.requests.head.contains("HTTP2-Settings:"))
    }
  }
}
