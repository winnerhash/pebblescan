package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

internal class CameraLuminanceSourceTest {

  @Test
  fun `camera padding and buffer offsets are excluded without consuming the buffer`() {
    val buffer = ByteBuffer.wrap(byteArrayOf(99, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6))
    buffer.position(1)
    val source = CameraLuminanceSource(buffer, 3, 2, 6, 2)
    assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), source.matrix)
    assertEquals(1, buffer.position())
    val rotated = source.rotateCounterClockwise()
    assertEquals(2, rotated.width)
    assertEquals(3, rotated.height)
    assertArrayEquals(byteArrayOf(3, 6, 2, 5, 1, 4), rotated.matrix)
    val upright = rotated.rotateCounterClockwise().rotateCounterClockwise().rotateCounterClockwise()
    assertArrayEquals(source.matrix, upright.matrix)
  }

  @Test
  fun `incomplete camera planes are rejected before decoding`() {
    assertThrows(IllegalArgumentException::class.java) {
      CameraLuminanceSource(ByteBuffer.allocate(5), 3, 2, 3, 1)
    }
  }
}