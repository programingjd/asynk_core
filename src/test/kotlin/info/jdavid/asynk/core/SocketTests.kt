package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.security.SecureRandom

class SocketTests {

  @Test fun testReadWrite() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    val port = (server.localAddress as InetSocketAddress).port

    val bytes = "Test".toByteArray()
    val n = bytes.size
    runBlocking {
      val s = async {
        server.connect().use {
          val buffer = ByteBuffer.wrap(bytes)
          while (buffer.remaining() > 0) it.writeFrom(buffer)
          it
        }
      }
      val c = AsynchronousSocketChannel.open()
      val r = async {
        c.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
      c.use {
          val buffer = ByteBuffer.allocate(n + 2)
          while (buffer.position() < n) it.readTo(buffer)
          buffer.flip()
          String(ByteArray(buffer.remaining()).apply { buffer.get(this) })
        }
      }.await()
      assertEquals("Test", r)
      assertFalse(c.isOpen)
      assertFalse(s.await().isOpen)
    }
  }

  @Test fun testWriteTimeout() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    val port = (server.localAddress as InetSocketAddress).port

    val c = AsynchronousSocketChannel.open()
    val bytes = SecureRandom.getSeed(128 * 1024 * 1024)
    val n = bytes.size
    try {
      runBlocking {
        launch {
          server.use { _ ->
            while (isActive) {
              server.connect().use {
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.remaining() > 0) it.writeFrom(buffer)
                it
              }
            }
          }
        }
        async {
          c.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
          c.use {
            withTimeout(1000) {
              val buffer = ByteBuffer.allocate(16)
              while (buffer.position() < n) it.readTo(buffer)
              buffer.flip()
              ByteArray(buffer.remaining()).apply { buffer.get(this) }
            }
          }
        }.await()
      }
    }
    catch (ignore: TimeoutCancellationException) {}
    assertFalse(c.isOpen)
    assertFalse(server.isOpen)
  }

  @Test fun testReadTimeout() {

  }


}
