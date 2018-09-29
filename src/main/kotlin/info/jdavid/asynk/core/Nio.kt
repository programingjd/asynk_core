package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import java.io.Closeable
import java.io.IOException
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousByteChannel
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.FileLock
import kotlin.coroutines.experimental.Continuation

suspend fun AsynchronousFileChannel.acquireLock(shared: Boolean = false,
                                                position: Long = 0L, size: Long = Long.MAX_VALUE): FileLock =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { closeSilently() }
    lock(position, size, shared, it, completion<FileLock>())
  }

suspend fun AsynchronousFileChannel.readTo(buffer: ByteBuffer,
                                           position: Long,
                                           tryToFillBuffer: Boolean = false): Long {
  return if (tryToFillBuffer) {
    var p = position
    while (buffer.remaining() > 0) {
      val n = readOnce(this, buffer, p)
      if (n < 0) break
      p += n
    }
    p - position
  }
  else readOnce(this, buffer, position).toLong()
}

suspend fun AsynchronousFileChannel.writeFrom(buffer: ByteBuffer,
                                              position: Long,
                                              tryToEmptyBuffer: Boolean = false): Long {
  return if (tryToEmptyBuffer) {
    var p = position
    while (buffer.remaining() > 0) {
      val n = writeOnce(this, buffer, p)
      if (n < 0) break
      p += n
    }
    p - position
  }
  else writeOnce(this, buffer, position).toLong()
}

suspend fun AsynchronousServerSocketChannel.connect(): AsynchronousSocketChannel =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { closeSilently() }
    accept(it, completion<AsynchronousSocketChannel>())
  }


suspend fun AsynchronousSocketChannel.connectTo(address: SocketAddress) {
  suspendCancellableCoroutine<Void?> {
    it.invokeOnCancellation { closeSilently() }
    connect(address, it, completion<Void?>())
  }
}

suspend fun AsynchronousByteChannel.readTo(buffer: ByteBuffer,
                                           tryToFillBuffer: Boolean = false): Long {
  return if (tryToFillBuffer) {
    var total = 0L
    while (buffer.remaining() > 0) {
      val n = readOnce(this, buffer)
      if (n < 0) break
      total += n
    }
    total
  }
  else readOnce(this, buffer).toLong()
}

suspend fun AsynchronousByteChannel.writeFrom(buffer: ByteBuffer,
                                              tryToEmptyBuffer: Boolean = false): Long {
  return if (tryToEmptyBuffer) {
    var total = 0L
    while (buffer.remaining() > 0) {
      val n = writeOnce(this, buffer)
      if (n < 0) break
      total += n
    }
    total
  }
  else writeOnce(this, buffer).toLong()
}

private suspend inline fun readOnce(channel: AsynchronousByteChannel,
                                    buffer: ByteBuffer): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.read(buffer, it, completion<Int>())
  }

private suspend inline fun writeOnce(channel: AsynchronousByteChannel,
                                     buffer: ByteBuffer): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.write(buffer, it, completion<Int>())
  }

private suspend inline fun readOnce(channel: AsynchronousFileChannel,
                                    buffer: ByteBuffer,
                                    position: Long): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.read(buffer, position, it, completion<Int>())
  }

private suspend inline fun writeOnce(channel: AsynchronousFileChannel,
                                     buffer: ByteBuffer,
                                     position: Long): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.write(buffer, position, it, completion<Int>())
  }


fun Closeable.closeSilently() {
  try { close() } catch (ignore: IOException) {}
}

@Suppress("UNCHECKED_CAST")
private fun <T> completion() =
  Completion as CompletionHandler<T, Continuation<T>>

private object Completion: CompletionHandler<Any, Continuation<Any?>> {
  override fun completed(result: Any?, attachment: Continuation<Any?>) {
    attachment.resume(result)
  }
  override fun failed(exc: Throwable, attachment: Continuation<Any?>) {
    if (exc is AsynchronousCloseException &&
        attachment is CancellableContinuation && attachment.isCancelled) return
    attachment.resumeWithException(exc)
  }
}
