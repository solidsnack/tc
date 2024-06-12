package onl.concepts.tc.timecodes

interface TimeCode<Descriptor> {
    fun encode(d: Descriptor): String

    fun decode(s: String): Result<Descriptor>
}
