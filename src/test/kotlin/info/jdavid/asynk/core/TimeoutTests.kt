package info.jdavid.asynk.core

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
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

class TimeoutTests {

  @Test fun testReadTimeout() {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      runBlocking {
        val channel0 = Channel<Int>(Channel.UNLIMITED)
        val accept = launch {
          while (isActive) {
            server.asyncAccept()
            channel0.send(1)
          }
        }

        val channel1 = Channel<Int>(Channel.UNLIMITED)
        val channel2 = Channel<Int>(Channel.UNLIMITED)

        val n = 50

        joinAll(
          *(1..n).map {
            launch {
              try {
                withTimeout(6000L) {
                  delay(it * 100L)
                  println(it)
                  channel1.send(it)
                  AsynchronousSocketChannel.open().use { socket ->
                    socket.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))
                    val buffer = ByteBuffer.allocate(512)
                    socket.asyncRead(buffer, true)
                  }
                }
              }
              catch (timeout: TimeoutCancellationException) {
                channel2.send(it)
              }
            }
          }.toTypedArray()
        )
        channel0.close()
        channel1.close()
        channel2.close()
        accept.cancelAndJoin()
        assertEquals(n, channel0.toList().size)
        assertEquals(n, channel1.toList().size)
        assertEquals(n, channel2.toList().size)
      }
    }
  }

}
