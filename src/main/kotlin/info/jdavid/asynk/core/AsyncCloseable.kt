package info.jdavid.asynk.core

import java.io.Closeable
import java.io.IOException

/**
 * An AsyncCloseable is a source or destination of data that can be closed.
 * The suspending close method is invoked to release resources that the object is holding.
 */
interface AsyncCloseable {
  suspend fun close()
}

/**
 * Executes the given [block] function on this resource and then closes it down correctly whether an exception
 * is thrown or not.
 * @param block a function to process this [AsyncCloseable] resource.
 * @return the result of [block] function invoked on this resource.
 */
suspend inline fun <reified T: AsyncCloseable?, R> T.use(block: (T) -> R): R =
  info.jdavid.asynk.core.internal.use(this, block)

inline fun <reified T : AutoCloseable?, R> T.use(block: (T) -> R): R {
  var exception: Throwable? = null
  try {
    return block(this)
  } catch (e: Throwable) {
    exception = e
    throw e
  } finally {
    when {
      this == null -> {}
      exception == null -> close()
      else ->
        try {
          close()
        } catch (closeException: Throwable) {
          exception.addSuppressed(closeException)
        }
    }
  }
}

fun Closeable?.closeSilently() {
  try { this?.close() } catch (ignore: IOException) {}
}
suspend fun AsyncCloseable?.closeSilently() {
  try { this?.close() } catch (ignore: IOException) {}
}
