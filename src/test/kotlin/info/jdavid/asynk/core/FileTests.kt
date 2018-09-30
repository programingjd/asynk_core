package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.joinAll
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

  @Test fun testRead() {
    File.createTempFile("test", "file").let { file ->
      try {
        file.writeBytes("Test".toByteArray())
        val buffer = ByteBuffer.allocate(64)
        AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ).use {
          val p = runBlocking {
            it.asyncRead(buffer, 0)
          }.toInt()
          buffer.flip()
          assertTrue(p > 0)
          assertEquals("Test".substring(0, p), String(ByteArray(p).apply { buffer.get(this) }))
        }
      }
      finally {
        file.delete()
      }
    }
  }

  @Test fun testReadAll() {
    File.createTempFile("test", "file").let { file ->
      try {
        val bytes = SecureRandom.getSeed(32*1024*1024)
        file.writeBytes(bytes)
        runBlocking {
          joinAll(
            launch {
              val buffer = ByteBuffer.allocate(32*1024*1024-1)
              AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ).use {
                val p = it.asyncRead(buffer, 0, true)
                assertEquals(buffer.capacity().toLong(), p)
                buffer.clear()
                assertEquals(1L, it.asyncRead(buffer, p))
              }
            },
            launch {
              val buffer = ByteBuffer.allocate(32*1024*1024+1)
              AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ).use {
                val p = it.asyncRead(buffer, 0, true)
                assertEquals(buffer.capacity().toLong() - 1, p)
              }
            }
          )
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
        val buffer = ByteBuffer.allocate(128 * 1024 * 1024 - 1)
        val channel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ)
        runBlocking {
          channel.use {
            try {
              async {
                withTimeout(50L) {
                  it.asyncRead(buffer, 0, true)
                  fail<Nothing>()
                }
              }.await()
            }
            catch (e: TimeoutCancellationException) {
              assertFalse(channel.isOpen)
            }
          }
        }
      }
      finally {
        file.delete()
      }
    }
  }

  @Test fun testWrite() {
    File.createTempFile("test", "file").let { file ->
      try {
        file.writeBytes(byteArrayOf())
        val buffer = ByteBuffer.wrap("Test".toByteArray())
        AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE).use {
          val p = runBlocking {
            it.asyncWrite(buffer, 0)
          }.toInt()
          assertTrue(p > 0)
          assertEquals("Test".substring(0, p), String(file.readBytes()))
        }
      }
      finally {
        file.delete()
      }
    }
  }

  @Test fun testWriteAll() {
    File.createTempFile("test", "file").let { file ->
      try {
        file.writeBytes(byteArrayOf())
        val bytes = SecureRandom.getSeed(32*1024*1024)
        runBlocking {
          async {
            val buffer = ByteBuffer.wrap(bytes)
            AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE).use {
              val p = it.asyncWrite(buffer, 0, true)
              assertEquals(buffer.capacity().toLong(), p)
              assertEquals(bytes.size.toLong(), file.length())
            }
          }.await()
        }
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
        val channel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.WRITE)
        runBlocking {
          channel.use {
            try {
              async {
                withTimeout(50L) {
                  it.asyncWrite(buffer, 0, true)
                  fail<Nothing>()
                }
              }.await()
            }
            catch (e: TimeoutCancellationException) {
              assertFalse(channel.isOpen)
            }
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
        c1.use {
          c2.use {
            try {
              runBlocking {
                launch {
                  c1.asyncLock().use {
                    val n = bytes1.size
                    var pos = 0L
                    while (pos < n) pos += c1.asyncWrite(ByteBuffer.wrap(bytes1), pos)
                  }
                }
                launch {
                  val n = bytes2.size
                  var pos = 0L
                  while (pos < n) pos += c2.asyncWrite(ByteBuffer.wrap(bytes2), pos)
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
