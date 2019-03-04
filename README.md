![jcenter](https://img.shields.io/badge/_jcenter_-0.0.0.28-6688ff.png?style=flat) &#x2003; ![jcenter](https://img.shields.io/badge/_Tests_-17/17-green.png?style=flat)
# Asynk core library
Code shared between the various asynk libraries.

# Usage

## Async Closeable

```kotlin
class Example: AsyncCloseable {
  var closed = false
    private set
  
  @Override suspend fun close() {
    //...
  }    
}

runBlocking {
  Example().use {
    //...
  }.apply {
    assertTrue(closed)
  }
  
  val example = Example()
  try {
    example.use {
      throw RuntimeException()
    }
  }
  catch (ignore: RuntimeException) {
    assertTrue(example.closed)
  }
}

```

## Async Channels

### Files

```kotlin
val bytes = SecureRandom.getSeed(1234567)
File("./test.txt").also { file ->
  AsynchronousFileSocket.open(file.toPath(), StandardOpenOption.WRITE).also { socket ->
    runBlocking {
      assertEquals(bytes.size, socket.asyncWrite(ByteBuffer.wrap(bytes), 0, true))
    }
  }
}

File("./test.txt").also { file ->
  AsynchronousFileSocket.open(file.toPath(), StandardOpenOption.READ).also { socket ->
    val buffer = ByteBuffer.allocate(file.length)
    runBlocking {
      assertEquals(buffer.capacity(), socket.asyncRead(buffer, 0, true))
    }
  }
}
```

### Sockets

```kotlin
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
```