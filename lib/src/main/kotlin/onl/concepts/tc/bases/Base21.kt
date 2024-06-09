package onl.concepts.tc.bases

import onl.concepts.tc.TCErr

/** No vowels (to prevent forming words and because `I` and `O` can be confused
 *  with numbers). Like Base20 but allows `L`.
 */
object Base21 {
    private const val string = "BCDFGHJKLMNPQRSTVWXYZ"
    val characters = string.toCharArray()

    /**
     *  Return the least significant digit of a number encoded in this base,
     *  and the number "shifted" right by one digit in this base.
     */
    fun nibble(i: Int): Pair<Int, Char> {
        val digit = i.mod(21)
        val shifted = i.div(21)

        return Pair(shifted, characters[digit])
    }

    /**
     * Build up the number, assuming the character is a digit being added from
     * the right. The input number is (a) shifted left one digit in this base
     * and then (b) has the value of character in this base added to it.
     */
    fun append(c: Char, n: Int): Int {
        val value = characters.indexOf(c.uppercaseChar())
        if (value < 0) throw TCErr("Invalid character: $c")
        return (n * 21) + value
    }
}
