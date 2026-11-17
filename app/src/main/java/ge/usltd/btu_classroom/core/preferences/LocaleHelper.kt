package ge.usltd.btu_classroom.core.preferences

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import java.util.Locale

object LocaleHelper {

  fun resolveLocale(language: AppLanguage, systemConfiguration: Configuration): Locale {
    return when (language) {
      AppLanguage.EN -> Locale.ENGLISH
      AppLanguage.KA -> Locale.forLanguageTag("ka")
      AppLanguage.SYSTEM -> systemConfiguration.locales.get(0) ?: Locale.getDefault()
    }
  }

  fun createLocalizedContext(baseContext: Context, language: AppLanguage): Context {
    if (language == AppLanguage.SYSTEM) {
      return baseContext
    }
    val targetLocale = resolveLocale(language, baseContext.resources.configuration)
    val config = Configuration(baseContext.resources.configuration)
    config.setLocales(LocaleList(targetLocale))
    return ContextThemeWrapper(baseContext, baseContext.theme).apply {
      applyOverrideConfiguration(config)
    }
  }
}
