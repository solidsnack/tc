package onl.concepts.tc

open class TCErr(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause)
