package onl.concepts.tc.timecodes

import java.time.Instant

import io.github.oshai.kotlinlogging.KotlinLogging

import onl.concepts.tc.PositionInYear
import onl.concepts.tc.bases.Base20
import onl.concepts.tc.bases.Base21

/**
 * Provides an eight character code every three minutes. The final four
 * characters are all alphabetical, and represent a value in the close
 * interval [0, 176399] encoded in a mixed base:
 * base-20 base-20 base-21 base-21.
 */
object TimeCode8 {
    fun of(t: Instant): String {
        val position = PositionInYear.of(t)
        var windowOf3Minutes = position.seconds() / 180

        logger.info { "Notional index in year of $t: $windowOf3Minutes"}

        val (rem1, digit4) = Base21.nibble(windowOf3Minutes)
        val (rem2, digit3) = Base21.nibble(rem1)
        val (rem3, digit2) = Base20.nibble(rem2)
        val (rem4, digit1) = Base20.nibble(rem3)

        assert(rem4 == 0) {
            "There should be nothing left at the end of this operation."
        }

        return "${position.year()}$digit1$digit2$digit3$digit4"
    }

    private val logger = KotlinLogging.logger {}
}
