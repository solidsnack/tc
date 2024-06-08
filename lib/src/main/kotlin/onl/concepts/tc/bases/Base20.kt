package onl.concepts.tc.bases

/** No vowels (to prevent forming words and because `I` and `O` can be confused
 *  with numbers) and no `L` (because it is easily confused with numbers).
 */
object Base20 {
    private const val string = "BCDFGHJKMNPQRSTVWXYZ"
    val characters = string.toCharArray()
    val bytes = string.toByteArray()

    fun nibble(i: Long): Pair<Long, Char> {
        val digit = i.mod(20)
        val shifted = i.div(20)

        return Pair(shifted, characters[digit])
    }

    fun nibble(i: Int): Pair<Int, Char> {
        val digit = i.mod(20)
        val shifted = i.div(20)

        return Pair(shifted, characters[digit])
    }
}
