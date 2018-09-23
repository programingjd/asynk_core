package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardOpenOption
import java.security.SecureRandom

class FileTests {

  @Test fun testReadWrite() {
    File.createTempFile("test", "file").let { file ->
      try {
        file.writeBytes(byteArrayOf())
        val buffer = ByteBuffer.wrap("Test".toByteArray())
        val n = buffer.remaining()
        val c1 = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE)
        c1.use {
          runBlocking {
            var pos = 0L
            while (pos < n) {
              pos += it.writeFrom(buffer, pos)
            }
          }
        }
        assertFalse(c1.isOpen)
        buffer.clear()
        val c2 = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
        c2.use {
          runBlocking {
            var pos = 0L
            while (pos < n) {
              pos += it.readTo(buffer, pos)
            }
          }
        }
        assertFalse(c2.isOpen)
        buffer.flip()
        assertEquals("Test", String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
      }
      finally {
        file.delete()
      }
    }
  }

  @Test fun testWriteTimeout() {
    File.createTempFile("test", "file").let { file ->
      try {
        file.writeBytes(byteArrayOf())
        val bytes = SecureRandom.getSeed(128*1024*1024)
        val buffer = ByteBuffer.wrap(bytes)
        val n = buffer.remaining()
        val c1 = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE)
        runBlocking {
          var pos = 0L
          try {
            c1.use {
              withTimeout(10) {
                while (pos < n) {
                  pos += it.writeFrom(buffer, pos)
                }
              }
            }
            fail<Nothing>()
          }
          catch (e: TimeoutCancellationException) {
            assertFalse(c1.isOpen)
            assertEquals(0L, pos)
          }
        }
      }
      finally {
        file.delete()
      }
    }
  }

  @Test fun testReadTimeout() {
    File.createTempFile("test", "file").let { file ->
      try {
        val bytes = SecureRandom.getSeed(128*1024*1024)
        file.writeBytes(bytes)
        val n = bytes.size
        val buffer = ByteBuffer.allocate(n)
        val c1 = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
        runBlocking {
          var pos = 0L
          try {
            c1.use {
              withTimeout(10) {
                while (pos < n) {
                  pos += it.readTo(buffer, pos)
                }
              }
            }
            fail<Nothing>()
          }
          catch (e: TimeoutCancellationException) {
            assertFalse(c1.isOpen)
            assertEquals(0L, pos)
            assertTrue(buffer.position() < n)
          }
        }
      }
      finally {
        file.delete()
      }
    }
  }

  @Test fun testLock() {
    File.createTempFile("test", "file").let { file ->
      try {
        file.writeBytes(byteArrayOf())
        val bytes1 = SecureRandom.getSeed(128*1024*1024)
        val bytes2 = SecureRandom.getSeed(128*1024*1024)
        val c1 = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE)
        val c2 = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE)
        c1.use { _ ->
          c2.use { _ ->
            try {
              runBlocking {
                launch {
                  c1.acquireLock().use {
                    val n = bytes1.size
                    var pos = 0L
                    while (pos < n) pos += c1.writeFrom(ByteBuffer.wrap(bytes1), pos)
                  }
                }
                launch {
                  val n = bytes2.size
                  var pos = 0L
                  while (pos < n) pos += c2.writeFrom(ByteBuffer.wrap(bytes2), pos)
                }
              }
              fail<Nothing>()
            }
            catch (ignore: IOException) {}
          }
        }
        assertFalse(c1.isOpen)
        assertFalse(c2.isOpen)
      }
      finally {
        file.delete()
      }
    }
  }


}
