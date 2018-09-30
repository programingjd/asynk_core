package info.jdavid.asynk.core.internal

import info.jdavid.asynk.core.AsyncCloseable

suspend inline fun <T: AsyncCloseable?, R> use(c: T, block: (T) -> R): R {
  var exception: Throwable? = null
  try {
    return block(c)
  }
  catch (e: Throwable) {
    exception = e
    throw e
  }
  finally {
    when {
      c == null -> {}
      exception == null -> c.close()
      else ->
        try {
          c.close()
        }
        catch (closeException: Throwable) {
          exception.addSuppressed(closeException)
        }
    }
  }
}
