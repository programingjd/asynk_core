package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
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

/**
 * Performs [AsynchronousFileChannel.lock] without blocking a thread and resumes when asynchronous operation
 * completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel and immediately resumes with [CancellationException].
 *
 * @param shared specifies whether a shared lock should be requested or not.
 * @param position the position at which the locked region is to start.
 * @param size the size of the locked region.
 * @return the file lock.
 */
suspend fun AsynchronousFileChannel.asyncLock(shared: Boolean = false,
                                              position: Long = 0L, size: Long = Long.MAX_VALUE): FileLock =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { closeSilently() }
    lock(position, size, shared, it, completion<FileLock>())
  }

/**
 * Performs [AsynchronousFileChannel.read] without blocking a thread and resumes when asynchronous operation
 * completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel and immediately resumes with [CancellationException].
 *
 * @param buffer the buffer into which the bytes are to be transferred.
 * @param position the position to start reading from.
 * @param tryToFillBuffer whether to keep reading until the buffer is full or the end of the file is reached.
 * @return the number of bytes read, or -1 if tryToFillBuffer is false and position is greater or equal to
 * the file size.
 */
suspend fun AsynchronousFileChannel.asyncRead(buffer: ByteBuffer,
                                              position: Long,
                                              tryToFillBuffer: Boolean = false): Long {
  return if (tryToFillBuffer) {
    var p = position
    while (buffer.remaining() > 0) {
      val n = aRead(this, buffer, p)
      if (n < 0) break
      p += n
    }
    p - position
  }
  else aRead(this, buffer, position).toLong()
}

/**
 * Performs [AsynchronousFileChannel.write] without blocking a thread and resumes when asynchronous operation
 * completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel and immediately resumes with [CancellationException].
 *
 * @param buffer the buffer from which the bytes are to be transferred.
 * @param position the position to start writing from.
 * @param tryToEmptyBuffer whether to keep writing until the buffer is empty or not.
 * @return the number of bytes written.
 */
suspend fun AsynchronousFileChannel.asyncWrite(buffer: ByteBuffer,
                                               position: Long,
                                               tryToEmptyBuffer: Boolean = false): Long {
  return if (tryToEmptyBuffer) {
    var p = position
    while (buffer.remaining() > 0) {
      val n = aWrite(this, buffer, p)
      if (n < 0) break
      p += n
    }
    p - position
  }
  else aWrite(this, buffer, position).toLong()
}

/**
 * Performs [AsynchronousServerSocketChannel.accept] without blocking a thread and resumes when asynchronous
 * operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel and immediately resumes with [CancellationException].
 *
 * @return the channel to the accepted connection.
 */
suspend fun AsynchronousServerSocketChannel.asyncAccept(): AsynchronousSocketChannel =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { closeSilently() }
    accept(it, completion<AsynchronousSocketChannel>())
  }

/**
 * Performs [AsynchronousSocketChannel.connect] without blocking a thread and resumes when asynchronous
 * operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel and immediately resumes with [CancellationException].
 */
suspend fun AsynchronousSocketChannel.asyncConnect(address: SocketAddress) {
  suspendCancellableCoroutine<Void?> {
    it.invokeOnCancellation { closeSilently() }
    connect(address, it, completion<Void?>())
  }
}

/**
 * Performs [AsynchronousByteChannel.read] without blocking a thread and resumes when asynchronous
 * operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel and immediately resumes with [CancellationException].
 *
 * @param buffer the buffer into which the bytes are to be transfered.
 * @param tryToFillBuffer whether to keep reading until the buffer is full or the end of stream is reached.
 * @return the number of bytes read, or -1 if tryToFillBuffer is false and the end of stream is reached.
 */
suspend fun AsynchronousByteChannel.asyncRead(buffer: ByteBuffer,
                                              tryToFillBuffer: Boolean = false): Long {
  return if (tryToFillBuffer) {
    var total = 0L
    while (buffer.remaining() > 0) {
      val n = aRead(this, buffer)
      if (n < 0) break
      total += n
    }
    total
  }
  else aRead(this, buffer).toLong()
}

/**
 * Performs [AsynchronousByteChannel.write] without blocking a thread and resumes when asynchronous operation
 * completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting,
 * this function closes the underlying channel* and immediately resumes with [CancellationException].
 *
 * @param buffer the buffer from which the bytes are to be transferred.
 * @param tryToEmptyBuffer whether to keep writing until the buffer is empty or not.
 * @return the number of bytes written.
 */
suspend fun AsynchronousByteChannel.asyncWrite(buffer: ByteBuffer,
                                               tryToEmptyBuffer: Boolean = false): Long {
  return if (tryToEmptyBuffer) {
    var total = 0L
    while (buffer.remaining() > 0) {
      val n = aWrite(this, buffer)
      if (n < 0) break
      total += n
    }
    total
  }
  else aWrite(this, buffer).toLong()
}

private suspend inline fun aRead(channel: AsynchronousByteChannel,
                                 buffer: ByteBuffer): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.read(buffer, it, completion<Int>())
  }

private suspend inline fun aWrite(channel: AsynchronousByteChannel,
                                  buffer: ByteBuffer): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.write(buffer, it, completion<Int>())
  }

private suspend inline fun aRead(channel: AsynchronousFileChannel,
                                 buffer: ByteBuffer,
                                 position: Long): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.read(buffer, position, it, completion<Int>())
  }

private suspend inline fun aWrite(channel: AsynchronousFileChannel,
                                  buffer: ByteBuffer,
                                  position: Long): Int =
  suspendCancellableCoroutine {
    it.invokeOnCancellation { channel.closeSilently() }
    channel.write(buffer, position, it, completion<Int>())
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
