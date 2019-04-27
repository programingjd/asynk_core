package info.jdavid.asynk.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.security.SecureRandom

class SocketTests {

  @Test fun testAcceptTimeout() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    server.use {
      runBlocking {
        launch {
          withTimeout(1000L) {
            while (isActive) it.asyncAccept()
          }
        }.join()
      }
    }
    assertFalse(server.isOpen)
  }

  @Test fun testRead() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    @Suppress("NAME_SHADOWING") server.use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            while (isActive) {
              val socket1 = server.asyncAccept()
              launch {
                socket1.asyncWrite(ByteBuffer.wrap("Test".toByteArray()), true)
              }
            }
          }
          joinAll(
            launch {
              socket2.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val buffer = ByteBuffer.allocate(512)
              val n = socket2.asyncRead(buffer)
              buffer.flip()
              assertTrue(n > 0L)
              assertEquals("Test".substring(0, n.toInt()),
                           String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
            }
          )
          accept.cancelAndJoin()
        }
      }
      assertFalse(socket2.isOpen)
    }
    assertFalse(server.isOpen)
  }

  @Test fun testReadMany() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    @Suppress("NAME_SHADOWING") server.use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val bytes = SecureRandom.getSeed(1024 * 1024)
            while (isActive) {
              val socket1 = server.asyncAccept()
              launch {
                while (true) socket1.asyncWrite(ByteBuffer.wrap(bytes), true)
              }
            }
          }
          joinAll(
            launch {
              socket2.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val buffer = ByteBuffer.allocate(32*1024*1024)
              val n = socket2.asyncRead(buffer, true)
              assertEquals(buffer.capacity().toLong(), n)
            }
          )
          accept.cancelAndJoin()
        }
      }
      assertFalse(socket2.isOpen)
    }
    assertFalse(server.isOpen)
  }

  @Test fun testReadTimeout() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    @Suppress("NAME_SHADOWING") server.use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val bytes = SecureRandom.getSeed(1024 * 1024)
            while (isActive) {
              val socket1 = server.asyncAccept()
              launch {
                while (true) socket1.asyncWrite(ByteBuffer.wrap(bytes), true)
              }
            }
          }
          try {
            async {
              socket2.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val buffer = ByteBuffer.allocate(32 * 1024 * 1024)
              withTimeout(50L) {
                socket2.asyncRead(buffer, true)
                fail<Nothing>()
              }
            }.await()
          }
          catch (e: TimeoutCancellationException) {}
          accept.cancelAndJoin()
        }
      }
      assertFalse(socket2.isOpen)
    }
    assertFalse(server.isOpen)
  }

  @Test fun testWrite() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    @Suppress("NAME_SHADOWING")  server.use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val bytes = "Test\r\n".toByteArray()
          val accept = launch {
            while (isActive) {
              val socket1 = server.asyncAccept()
              launch {
                val buffer = ByteBuffer.allocate(bytes.size)
                socket1.asyncRead(buffer, true)
                buffer.flip()
                assertEquals("Test\r\n",
                             String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
              }
            }
          }
          joinAll(
            launch {
              socket2.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              (0..10).forEach {
                val n = socket2.asyncWrite(ByteBuffer.wrap(bytes), true)
                assertEquals(bytes.size.toLong(), n)
              }
              delay(100)
            }
          )
          accept.cancelAndJoin()
        }
      }
      assertFalse(socket2.isOpen)
    }
    assertFalse(server.isOpen)
  }

  @Test fun testWriteMany() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    @Suppress("NAME_SHADOWING") server.use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val buffer = ByteBuffer.allocate(32 * 1024 * 1024)
            while (isActive) {
              buffer.clear()
              val socket1 = server.asyncAccept()
              launch {
                val n = socket1.asyncRead(buffer, true)
                assertEquals(buffer.capacity().toLong(), n)
              }
            }
          }
          joinAll(
            launch {
              socket2.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val bytes = SecureRandom.getSeed(1024 * 1024)
              (0..32).forEach {
                val n = socket2.asyncWrite(ByteBuffer.wrap(bytes), true)
                assertEquals(bytes.size.toLong(), n)
              }
              delay(100)
            }
          )
          accept.cancelAndJoin()
        }
      }
      assertFalse(socket2.isOpen)
    }
    assertFalse(server.isOpen)
  }

  @Test fun testWriteTimeout() {
    val server = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    @Suppress("NAME_SHADOWING") server.use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val buffer = ByteBuffer.allocate(32 * 1024 * 1024)
            while (isActive) {
              buffer.clear()
              val socket1 = server.asyncAccept()
              launch {
                val n = socket1.asyncRead(buffer, true)
                assertEquals(buffer.capacity().toLong(), n)
              }
            }
          }
          try {
            launch {
              socket2.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val bytes = SecureRandom.getSeed(1024 * 1024)
              withTimeout(150L) {
                (0..32).forEach {
                  val n = socket2.asyncWrite(ByteBuffer.wrap(bytes), true)
                  assertEquals(bytes.size.toLong(), n)
                }
                fail<Nothing>()
              }
            }
          }
          catch (e: TimeoutCancellationException) {
            assertFalse(socket2.isOpen)
          }
          accept.cancelAndJoin()
        }
      }
      assertFalse(socket2.isOpen)
    }
    assertFalse(server.isOpen)
  }

}
