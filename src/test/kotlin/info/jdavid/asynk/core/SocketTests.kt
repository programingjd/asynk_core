package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.TimeoutCancellationException
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancelAndJoin
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.isActive
import kotlinx.coroutines.experimental.joinAll
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

  @Test fun testAcceptTimeout() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      runBlocking {
        launch {
          withTimeout(1000L) {
            while (isActive) server.connect()
          }
        }.join()
        assertFalse(server.isOpen)
      }
    }
  }

  @Test fun testRead() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            while (isActive) {
              val socket1 = server.connect()
              launch {
                socket1.writeFrom(ByteBuffer.wrap("Test".toByteArray()), true)
              }
            }
          }
          joinAll(
            launch {
              socket2.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val buffer = ByteBuffer.allocate(512)
              val n = socket2.readTo(buffer)
              buffer.flip()
              assertTrue(n > 0L)
              assertEquals("Test".substring(0, n.toInt()),
                           String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
            }
          )
          accept.cancelAndJoin()
        }
      }
    }
  }

  @Test fun testReadMany() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val bytes = SecureRandom.getSeed(1024 * 1024)
            while (isActive) {
              val socket1 = server.connect()
              launch {
                while (true) socket1.writeFrom(ByteBuffer.wrap(bytes), true)
              }
            }
          }
          joinAll(
            launch {
              socket2.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val buffer = ByteBuffer.allocate(32*1024*1024)
              val n = socket2.readTo(buffer, true)
              assertEquals(buffer.capacity().toLong(), n)
            }
          )
          accept.cancelAndJoin()
        }
      }
    }
  }

  @Test fun testReadTimeout() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val bytes = SecureRandom.getSeed(1024 * 1024)
            while (isActive) {
              val socket1 = server.connect()
              launch {
                while (true) socket1.writeFrom(ByteBuffer.wrap(bytes), true)
              }
            }
          }
          try {
            async {
              socket2.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val buffer = ByteBuffer.allocate(32 * 1024 * 1024)
              withTimeout(50L) {
                socket2.readTo(buffer, true)
                fail<Nothing>()
              }
            }.await()
          }
          catch (e: TimeoutCancellationException) {
            assertFalse(socket2.isOpen)
          }
          accept.cancelAndJoin()
        }
      }
    }
  }

  @Test fun testWrite() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val bytes = "Test\r\n".toByteArray()
          val accept = launch {
            while (isActive) {
              val socket1 = server.connect()
              launch {
                val buffer = ByteBuffer.allocate(bytes.size)
                socket1.readTo(buffer, true)
                buffer.flip()
                assertEquals("Test\r\n",
                             String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
              }
            }
          }
          joinAll(
            launch {
              socket2.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              (0..10).forEach {
                val n = socket2.writeFrom(ByteBuffer.wrap(bytes), true)
                assertEquals(bytes.size.toLong(), n)
              }
              delay(100)
            }
          )
          accept.cancelAndJoin()
        }
      }
    }
  }

  @Test fun testWriteMany() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val buffer = ByteBuffer.allocate(32 * 1024 * 1024)
            while (isActive) {
              buffer.clear()
              val socket1 = server.connect()
              launch {
                val n = socket1.readTo(buffer, true)
                assertEquals(buffer.capacity().toLong(), n)
              }
            }
          }
          joinAll(
            launch {
              socket2.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val bytes = SecureRandom.getSeed(1024 * 1024)
              (0..32).forEach {
                val n = socket2.writeFrom(ByteBuffer.wrap(bytes), true)
                assertEquals(bytes.size.toLong(), n)
              }
              delay(100)
            }
          )
          accept.cancelAndJoin()
        }
      }
    }
  }

  @Test fun testWriteTimeout() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      val socket2 = AsynchronousSocketChannel.open()
      socket2.use {
        runBlocking {
          val accept = launch {
            val buffer = ByteBuffer.allocate(32 * 1024 * 1024)
            while (isActive) {
              buffer.clear()
              val socket1 = server.connect()
              launch {
                val n = socket1.readTo(buffer, true)
                assertEquals(buffer.capacity().toLong(), n)
              }
            }
          }
          try {
            launch {
              socket2.connectTo(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
              val bytes = SecureRandom.getSeed(1024 * 1024)
              withTimeout(150L) {
                (0..32).forEach {
                  val n = socket2.writeFrom(ByteBuffer.wrap(bytes), true)
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
    }
  }

}