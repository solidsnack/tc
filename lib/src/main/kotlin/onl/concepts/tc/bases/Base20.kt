package onl.concepts.tc.bases

import onl.concepts.tc.TCErr

/** No vowels (to prevent forming words and because `I` and `O` can be confused
 *  with numbers) and no `L` (because it is easily confused with numbers).
 */
object Base20 {
    private const val string = "BCDFGHJKMNPQRSTVWXYZ"
    val characters = string.toCharArray()

    /**
     *  Return the least significant digit of a number encoded in this base,
     *  and the number shifted right by one digit in this base.
     */
    fun nibble(i: Int): Pair<Int, Char> {
        val digit = i.mod(20)
        val shifted = i.div(20)

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
        return (n * 20) + value
    }
}
