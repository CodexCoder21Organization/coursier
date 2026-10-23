package coursier.cache.internal

import java.io.{EOFException, IOException, InputStream, PushbackInputStream}
import java.util.zip.{CRC32, DataFormatException, Inflater, ZipException}

/** Streams each gzip member and requires a complete header, body and trailer for every member. */
private[internal] final class GzipInputStream(underlying: InputStream, bufferSize: Int)
    extends InputStream {
  private val input  = new PushbackInputStream(underlying, bufferSize)
  private val buffer = new Array[Byte](bufferSize)
  private val single = new Array[Byte](1)
  private val crc    = new CRC32()
  private var size   = 0
  private var ended  = false
  private var closed = false

  // Parse before allocating the inflater so a rejected header cannot retain native resources.
  readHeader(requiredByte())
  private val inflater = new Inflater(true)

  private def requiredByte(): Int = {
    val value = input.read()
    if (value < 0) throw new EOFException()
    value
  }

  private def unsignedShort(): Int = {
    val low = requiredByte()
    low | (requiredByte() << 8)
  }

  private def unsignedInt(): Long = {
    val low = unsignedShort().toLong
    low | (unsignedShort().toLong << 16)
  }

  private def readHeader(first: Int): Unit = {
    crc.reset()
    crc.update(first)
    def headerByte(): Int = {
      val value = requiredByte()
      crc.update(value)
      value
    }
    if ((first | (headerByte() << 8)) != 0x8b1f)
      throw new ZipException("Not in GZIP format")
    if (headerByte() != 8)
      throw new ZipException("Unsupported compression method")
    val flags = headerByte()
    if ((flags & 0xe0) != 0)
      throw new ZipException(s"Unsupported GZIP flags: $flags")
    for (_ <- 0 until 6) headerByte() // Modification time, extra flags, operating system.
    if ((flags & 4) != 0) {
      val low    = headerByte()
      val length = low | (headerByte() << 8)
      for (_ <- 0 until length) headerByte()
    }
    if ((flags & 8) != 0)
      while (headerByte() != 0) ()
    if ((flags & 16) != 0)
      while (headerByte() != 0) ()
    if ((flags & 2) != 0) {
      val expected = (crc.getValue & 0xffffL).toInt
      if (unsignedShort() != expected)
        throw new ZipException("Corrupt GZIP header")
    }
    crc.reset()
  }

  override def read(): Int = {
    val n = read(single, 0, 1)
    if (n < 0) -1 else single(0) & 0xff
  }

  override def read(bytes: Array[Byte], offset: Int, length: Int): Int = {
    if (closed) throw new IOException("Stream closed")
    if (offset < 0 || length < 0 || offset > bytes.length - length)
      throw new IndexOutOfBoundsException()
    if (length == 0) return 0
    while (!ended) {
      val n =
        try inflater.inflate(bytes, offset, length)
        catch {
          case error: DataFormatException =>
            val failure = new ZipException(Option(error.getMessage).getOrElse("Invalid gzip data"))
            failure.initCause(error)
            throw failure
        }
      if (n > 0) {
        crc.update(bytes, offset, n)
        // Return available decoded bytes before reading a trailer or waiting for more input.
        return n
      }
      if (inflater.finished()) {
        val remaining = inflater.getRemaining
        if (remaining > 0)
          input.unread(buffer, size - remaining, remaining)
        val expectedCrc  = unsignedInt()
        val expectedSize = unsignedInt()
        if (expectedCrc != crc.getValue || expectedSize != (inflater.getBytesWritten & 0xffffffffL))
          throw new ZipException("Corrupt GZIP trailer")
        // Only EOF at a member boundary completes gzip. A started header must finish.
        val first = input.read()
        if (first < 0) ended = true
        else {
          readHeader(first)
          inflater.reset()
        }
      }
      else if (inflater.needsDictionary())
        throw new ZipException("Unexpected gzip dictionary request")
      else if (inflater.needsInput()) {
        size = input.read(buffer, 0, buffer.length)
        if (size < 0) throw new EOFException()
        inflater.setInput(buffer, 0, size)
      }
    }
    -1
  }

  override def close(): Unit =
    if (!closed) {
      closed = true
      try inflater.end()
      finally input.close()
    }
}
