package ge.usltd.btu_classroom.core.auth

class LocalCleanupException : Exception(MESSAGE) {
  companion object {
    const val MESSAGE = "Some local data could not be removed. Please retry clearing local data."
  }
}
