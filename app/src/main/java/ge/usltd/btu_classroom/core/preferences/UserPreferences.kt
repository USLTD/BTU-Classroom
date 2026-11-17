package ge.usltd.btu_classroom.core.preferences

enum class AppLanguage(val code: String) {
  SYSTEM("system"),
  EN("en"),
  KA("ka");

  companion object {
    fun fromCode(code: String?): AppLanguage =
      entries.firstOrNull { it.code == code } ?: SYSTEM
  }
}

enum class AppThemeMode(val code: String) {
  SYSTEM("system"),
  LIGHT("light"),
  DARK("dark");

  companion object {
    fun fromCode(code: String?): AppThemeMode =
      entries.firstOrNull { it.code == code } ?: SYSTEM
  }
}

enum class SessionPersistenceMode {
  SESSION_ONLY,
  REMEMBER_LOGIN,
}

data class UserPreferences(
  val rememberLogin: Boolean = false,
  val language: AppLanguage = AppLanguage.SYSTEM,
  val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  val dynamicColor: Boolean = false,
  val backgroundSync: Boolean = false,
  val notificationsEnabled: Boolean = false,
) {
  val sessionPersistenceMode: SessionPersistenceMode
    get() = if (rememberLogin) {
      SessionPersistenceMode.REMEMBER_LOGIN
    } else {
      SessionPersistenceMode.SESSION_ONLY
    }
}
