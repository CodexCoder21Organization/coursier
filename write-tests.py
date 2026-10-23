from pathlib import Path
repo=Path('.')
src=Path('../FCOURS4-evidence-20261023/Rcours2BoundaryTests.scala').read_text()
src=src.replace('object Rcours2BoundaryTests','object DownloadResponseFramingTests')
src=src.replace('RecordingLogger(clientCount: Int)','RecordingLogger()').replace('new RecordingLogger(clientCount)','new RecordingLogger()')
src='\n'.join(line for line in src.split('\n') if 'println(s"RCOURS2' not in line)
start=src.index('  val tests = Tests {')
head=src[:start]
tests=src[start:]
tests=tests.replace('''      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("cut off chunked''','''      downloadErrorIs(o, statusMessage(o.url, 304))
    }

    test("cut off chunked''',1)
tests=tests.replace('''      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("cut off gzip''','''      downloadErrorIs(o, chunkedMessage(o.url, 6))
    }

    test("cut off gzip''',1)
for next_test, cut in [('short other encoded',4),('correct mixed case',2)]:
 old='''      assert(o.result.isLeft)
      assert(!o.finalExists)
      assert(!o.temporaryExists)
    }

    test("'''+next_test
 new='''      downloadErrorIs(o, incompleteMessage(o.url, compressed.length, compressed.length - '''+str(cut)+'''))
    }

    test("'''+next_test
 assert old in tests,next_test
 tests=tests.replace(old,new,1)
tests=tests.replace('correct other encoded body is not compared with a decoded length','correct deflate body decodes without comparing decoded and wire lengths').replace('successfulDownloadIs(o, compressed)','successfulDownloadIs(o, body)')
tests=tests.replace('''      assert(o.allResults.forall(_.isLeft))''','''      assert(o.allResults.map(_.left.toOption.map(_.message)) ==
        List.fill(2)(Some(incompleteMessage(o.url, 9, 6))))''')
helpers='''  private def statusMessage(url: String, code: Int): String =
    s"Unexpected HTTP status $code from $url: expected 200 or 206 with an artifact body"

  private def chunkedMessage(url: String, received: Long): String =
    s"Invalid response framing from $url: expected a complete chunked message, received $received bytes (Premature EOF)"

  private def unsupportedMessage(url: String, encoding: String): String =
    s"Unsupported Content-Encoding '$encoding' from $url: expected identity, gzip, or deflate"

  private def gzipBytes(bytes: Array[Byte]): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    val gzip = new GZIPOutputStream(output)
    gzip.write(bytes)
    gzip.close()
    output.toByteArray
  }

  private def chunk(bytes: Array[Byte], closing: Boolean = true): Array[Byte] =
    (Integer.toHexString(bytes.length) + "\\r\\n").getBytes(ISO_8859_1) ++ bytes ++
      (if (closing) "\\r\\n0\\r\\n\\r\\n" else "\\r\\n").getBytes(ISO_8859_1)

'''
more='''
    test("chunked range must consume the closing chunk before publishing") {
      val o = scenario(List(response("206 Partial Content", Seq("Transfer-Encoding" -> "chunked",
        "Content-Range" -> "bytes 3-5/6"), List(chunk(body.drop(3), closing = false)))), prefix = body.take(3))
      downloadErrorIs(o, chunkedMessage(o.url, 3))
    }

    test("chunked range rejects data beyond its stated range") {
      val o = scenario(List(response("206 Partial Content", Seq("Transfer-Encoding" -> "chunked",
        "Content-Range" -> "bytes 3-5/6"), List(chunk("defXYZ".getBytes(UTF_8))))), prefix = body.take(3))
      downloadErrorIs(o, s"Overlong response from ${o.url}: expected 3 bytes, received 6 bytes")
    }

    test("range data stays temporary until the closing chunk arrives") {
      val release = new CountDownLatch(1)
      val reply = Reply("206 Partial Content", Seq("Transfer-Encoding" -> "chunked",
        "Content-Range" -> "bytes 3-5/6"), List(chunk(body.drop(3), closing = false),
        "0\\r\\n\\r\\n".getBytes(ISO_8859_1)), Some(ProgressGate(6, release)))
      val o = scenario(List(reply), prefix = body.take(3), inspectWhilePaused = Some((6, release)))
      val pending = o.pendingState.get
      assert(!pending.finalExists)
      assert(pending.temporaryExists)
      assert(pending.temporaryBytes.toSeq == body.toSeq)
      successfulDownloadIs(o, body)
    }

    test("no body status with a saved prefix is rejected and cleaned") {
      for (code <- Seq(204, 205, 304)) {
        val o = scenario(List(response(s"$code No Body", Seq("Content-Length" -> "6"), Nil)), prefix = body.take(3))
        downloadErrorIs(o, statusMessage(o.url, code))
        assert(o.requests.count(_.startsWith("GET ")) == 1)
      }
    }

    test("unknown and empty encodings cannot publish response bytes") {
      for (encoding <- Seq("custom", "br", "gzip, custom", "")) {
        val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> encoding))), prefix = body.take(3))
        downloadErrorIs(o, unsupportedMessage(o.url, encoding))
      }
    }

    test("mixed case deflate is decoded") {
      val output = new ByteArrayOutputStream()
      val deflate = new java.util.zip.DeflaterOutputStream(output)
      deflate.write(body)
      deflate.close()
      val compressed = output.toByteArray
      val o = scenario(List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "DeFlAtE"))))
      successfulDownloadIs(o, body)
    }

    test("chunked gzip validates the closing chunk after its trailer") {
      val compressed = gzipBytes(body)
      val o = scenario(List(response("200 OK", Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "GZip"),
        List(chunk(compressed, closing = false)))))
      downloadErrorIs(o, chunkedMessage(o.url, compressed.length))
    }

    test("valid chunked gzip is decoded through the closing chunk") {
      val o = scenario(List(response("200 OK", Seq("Transfer-Encoding" -> "chunked", "Content-Encoding" -> "gZiP"),
        List(chunk(gzipBytes(body))))))
      successfulDownloadIs(o, body)
    }

    test("complete framing cannot hide an incomplete gzip trailer") {
      val compressed = gzipBytes(body).dropRight(4)
      val o = scenario(List(fixed(compressed.length.toString, compressed, Seq("Content-Encoding" -> "gzip"))))
      downloadErrorIs(o, s"Invalid Content-Encoding 'gzip' from ${o.url}: expected a complete decoded body, received ${compressed.length} response bytes (java.io.EOFException)")
    }

    test("invalid gzip header removes a saved temporary prefix") {
      val o = scenario(List(fixed("6", body, Seq("Content-Encoding" -> "gzip"))), prefix = body.take(3))
      downloadErrorIs(o, s"Invalid Content-Encoding 'gzip' from ${o.url}: expected a complete decoded body, received 2 response bytes (Not in GZIP format)")
    }

    test("encoded resume cannot append decoded bytes to an earlier representation") {
      val compressed = gzipBytes(body.drop(3))
      val o = scenario(List(response("206 Partial Content", Seq("Content-Length" -> compressed.length.toString,
        "Content-Range" -> s"bytes 3-${compressed.length + 2}/${compressed.length + 3}", "Content-Encoding" -> "gzip"),
        List(compressed))), prefix = body.take(3))
      downloadErrorIs(o, s"Cannot resume encoded response from ${o.url}: expected identity encoding for saved prefix of 3 bytes, received 'gzip'")
    }
'''
tests=tests[:-6]+more+'  }\n}\n' if tests.endswith('  }\n}\n') else None
assert tests
Path('modules/cache/jvm/src/test/scala/coursier/cache/DownloadResponseFramingTests.scala').write_text(head+helpers+tests)
