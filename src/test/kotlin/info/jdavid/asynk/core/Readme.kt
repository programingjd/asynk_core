package info.jdavid.asynk.core

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel

object Readme {

  @JvmStatic
  fun main(args: Array<String>) {
    AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0)).use { server ->
      val port = (server.localAddress as InetSocketAddress).port
      runBlocking {
        val echo = launch {
          // continuously accept connections
          while (isActive) {
            val socket = server.asyncAccept()
            // read from channel and echo all read bytes
            launch {
              socket.use {
                val buffer = ByteBuffer.allocate(16)
                while (true) {
                  if (socket.asyncRead(buffer) == -1L) break
                  buffer.flip()
                  socket.asyncWrite(buffer, true)
                  buffer.clear()
                }
              }
            }
          }
        }

        AsynchronousSocketChannel.open().use { socket ->
          launch {
            // connect to server
            socket.asyncConnect(InetSocketAddress(InetAddress.getLoopbackAddress(), port))

            // read incoming bytes and print them
            val reader = launch {
              val buffer = ByteBuffer.allocate(16)
              while (isActive) {
                withTimeout(50000L) {
                  socket.asyncRead(buffer)
                }
                if (buffer.position() > 0) {
                  buffer.flip()
                  print(String(ByteArray(buffer.remaining()).apply { buffer.get(this) }))
                  buffer.clear()
                }
              }
            }

            // write to channel
            val writer = launch {
              withTimeout(50000L) {
                socket.asyncWrite(ByteBuffer.wrap("Test1\r\nTest2\r\nTest3\r\n".toByteArray()), true)
                socket.asyncWrite(ByteBuffer.wrap("Test4\r\nTest5\r\n".toByteArray()), true)
              }
              // wait to server and reader time to echo.
              delay(1000L)
              // stop the reader
              reader.cancelAndJoin()
            }

            reader.start()
            writer.join()

          }.join()

          // stop the server
          echo.cancelAndJoin()
        }
      }
    }
  }

}
