package ge.usltd.btu_classroom.core.auth

/** Rejects values that could escape a single Cookie header value. */
object SessionCookieValidator {
  fun isValid(value: String?): Boolean =
    !value.isNullOrBlank() && value.length <= 4096 && value.all { character ->
      character.code in 0x21..0x7e && character !in ";,\"\\"
    }
}
