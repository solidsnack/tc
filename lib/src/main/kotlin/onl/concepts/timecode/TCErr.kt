package onl.concepts.timecode

open class TCErr(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
