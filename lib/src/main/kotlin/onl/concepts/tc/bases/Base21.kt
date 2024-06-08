package onl.concepts.tc.bases


/** No vowels (to prevent forming words and because `I` and `O` can be confused
 *  with numbers). Like Base20 but allows `L`.
 */
object Base21 {
    private const val string = "BCDFGHJKLMNPQRSTVWXYZ"
    val characters = string.toCharArray()
    val bytes = string.toByteArray()

    fun nibble(i: Long): Pair<Long, Char> {
        val digit = i.mod(21)
        val shifted = i.div(21)

        return Pair(shifted, characters[digit])
    }

    fun nibble(i: Int): Pair<Int, Char> {
        val digit = i.mod(21)
        val shifted = i.div(21)

        return Pair(shifted, characters[digit])
    }
}
