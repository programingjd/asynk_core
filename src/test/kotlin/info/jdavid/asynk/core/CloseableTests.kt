package info.jdavid.asynk.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CloseableTests {

  class TestCloseable: AsyncCloseable {
    var closed = false
      private set
    override suspend fun close() {
      closed = true
    }
    fun throwing(): Unit = throw RuntimeException()
    fun notThrowing() {}
  }

  @Test
  fun testNoExceptionThrown() {
    runBlocking {
      TestCloseable().apply {
        use {
          notThrowing()
        }
        assertTrue(closed)
      }
    }
  }

  @Test
  fun testExceptionThrown() {
    runBlocking {
      TestCloseable().apply {
        try {
          use {
            throwing()
          }
          fail("Exception should have been thrown.")
        }
        catch (e: RuntimeException) {
          assertTrue(closed)
        }
      }
    }
  }

}
