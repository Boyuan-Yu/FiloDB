package filodb.memory.format

import java.nio.ByteBuffer

import com.kenai.jffi.MemoryIO

object UnsafeUtils {
  val unsafe = scala.concurrent.util.Unsafe.instance

  // scalastyle:off
  val ZeroPointer: Any = null
  // scalastyle:on

  val arayOffset = unsafe.arrayBaseOffset(classOf[Array[Byte]])

  /** Translate ByteBuffer into base, offset, numBytes */
  // scalastyle:off
  def BOLfromBuffer(buf: ByteBuffer): (Any, Long, Int) = {
    if (buf.hasArray) {
      (buf.array, arayOffset.toLong + buf.arrayOffset + buf.position, buf.limit - buf.position)
    } else {
      assert(buf.isDirect)
      val address = MemoryIO.getCheckedInstance.getDirectBufferAddress(buf)
      (UnsafeUtils.ZeroPointer, address + buf.position, buf.limit - buf.position)
      //throw new RuntimeException("Cannot support this ByteBuffer!")
    }
  }
  // scalastyle:on

  def asDirectBuffer(address: Long, size: Int): ByteBuffer = {
    MemoryIO.getCheckedInstance.newDirectByteBuffer(address,size)
  }

  /**
   * Generic methods to read and write data to any offset from a base object location.  Be careful, this
   * can easily crash the system!
   */
  final def getByte(obj: Any, offset: Long): Byte = unsafe.getByte(obj, offset)
  final def getShort(obj: Any, offset: Long): Short = unsafe.getShort(obj, offset)
  final def getInt(obj: Any, offset: Long): Int = unsafe.getInt(obj, offset)
  final def getLong(obj: Any, offset: Long): Long = unsafe.getLong(obj, offset)
  final def getDouble(obj: Any, offset: Long): Double = unsafe.getDouble(obj, offset)
  final def getFloat(obj: Any, offset: Long): Double = unsafe.getFloat(obj, offset)

  final def setByte(obj: Any, offset: Long, byt: Byte): Unit = unsafe.putByte(obj, offset, byt)
  final def setShort(obj: Any, offset: Long, s: Short): Unit = unsafe.putShort(obj, offset, s)
  final def setInt(obj: Any, offset: Long, i: Int): Unit = unsafe.putInt(obj, offset, i)
  final def setLong(obj: Any, offset: Long, l: Long): Unit = unsafe.putLong(obj, offset, l)
  final def setDouble(obj: Any, offset: Long, d: Double): Unit = unsafe.putDouble(obj, offset, d)
  final def setFloat(obj: Any, offset: Long, f: Float): Unit = unsafe.putFloat(obj, offset, f)

  /**
   * Compares two memory buffers of length numBytes, returns true if they are byte for byte equal
   * Compares long words for speed
   */
  def equate(srcObj: Any, srcOffset: Long, destObj: Any, destOffset: Long, numBytes: Int): Boolean = {
    var i = 0
    while (i <= numBytes - 8) {
      if (getLong(srcObj, srcOffset + i) != getLong(destObj, destOffset + i)) return false
      i += 8
    }
    while (i < numBytes) {
      if (getByte(srcObj, srcOffset + i) != getByte(destObj, destOffset + i)) return false
      i += 1
    }
    true
  }


  // Comparison of two memories assuming both are word aligned and length is rounded to next word (4 bytes)
  // Also assumes a little-endian (eg Intel) architecture
  def wordCompare(srcObj: Any, srcOffset: Long, destObj: Any, destOffset: Long, n: Int): Int = {
    import java.lang.Integer.reverseBytes
    var i = 0
    while (i < n) {
      val srcWord = reverseBytes(getInt(srcObj, srcOffset + i)) ^ 0x80000000
      val destWord = reverseBytes(getInt(destObj, destOffset + i)) ^ 0x80000000
      if (srcWord < destWord) return -1 else if (srcWord != destWord) return 1
      i += 4
    }
    0
  }
}