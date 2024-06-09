package onl.concepts.tc.timecodes

import kotlin.test.Test
import kotlin.test.assertEquals

object Year8Test {
    @Test
    fun testDecodeSimple() {
        val encoded = "2024MSZH" // Generated: 2024-06-09T02:16:32Z
        val decoded = Year8.window(encoded)
        val expected = "2024-06-09T02:15Z/02:18Z"

        assertEquals(
            expected,
            decoded,
            "Decoding of $encoded resulted in $decoded, not $expected."
        )
    }
}
