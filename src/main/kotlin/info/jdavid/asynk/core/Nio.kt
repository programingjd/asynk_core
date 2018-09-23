package info.jdavid.asynk.core

import kotlinx.coroutines.experimental.CancellableContinuation
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
import kotlin.coroutines.experimental.suspendCoroutine

suspend fun AsynchronousFileChannel.acquireLock(shared: Boolean = false,
                                                position: Long = 0L, size: Long = Long.MAX_VALUE): FileLock =
  suspendCancellableCoroutine { lock(position, size, shared, it, completion<FileLock>()) }

suspend fun AsynchronousFileChannel.readTo(buffer: ByteBuffer,
                                           position: Long): Int =
  suspendCancellableCoroutine { read(buffer, position, it, completion<Int>()) }

suspend fun AsynchronousFileChannel.writeFrom(buffer: ByteBuffer,
                                              position: Long): Int =
  suspendCancellableCoroutine {
    write(buffer, position, it, completion<Int>())
  }


suspend fun AsynchronousServerSocketChannel.connect(): AsynchronousSocketChannel =
  suspendCoroutine { accept(it, completion<AsynchronousSocketChannel>()) }


suspend fun AsynchronousSocketChannel.connectTo(address: SocketAddress) {
  suspendCoroutine<Void?> { connect(address, it, completion<Void?>()) }
}

suspend fun AsynchronousByteChannel.readTo(buffer: ByteBuffer): Int =
  suspendCancellableCoroutine { read(buffer, it, completion<Int>()) }

suspend fun AsynchronousByteChannel.writeFrom(buffer: ByteBuffer): Int =
  suspendCancellableCoroutine { write(buffer, it, completion<Int>()) }


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
