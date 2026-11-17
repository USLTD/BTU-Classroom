package ge.usltd.btu_classroom.design.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import ge.usltd.btu_classroom.core.preferences.AppThemeMode

private val DarkColorScheme = darkColorScheme(
  primary = BtuPrimaryDark,
  onPrimary = BtuOnPrimaryDark,
  primaryContainer = BtuContainerDark,
  onPrimaryContainer = BtuOnContainerDark,
  secondary = IndigoSecondaryDark,
  onSecondary = IndigoOnSecondaryDark,
  secondaryContainer = IndigoContainerDark,
  onSecondaryContainer = IndigoOnContainerDark,
  tertiary = AmberTertiaryDark,
  onTertiary = AmberOnTertiaryDark,
  tertiaryContainer = AmberContainerDark,
  onTertiaryContainer = AmberOnContainerDark,
  background = BackgroundDark,
  onBackground = OnSurfaceDark,
  surface = SurfaceDark,
  onSurface = OnSurfaceDark,
  surfaceVariant = SurfaceVariantDark,
  onSurfaceVariant = OnSurfaceVariantDark,
  outline = OutlineDark,
  outlineVariant = Color(0xFF51404A),
  surfaceTint = BtuPrimaryDark,
  surfaceContainerLowest = BackgroundDark,
  surfaceContainerLow = SurfaceDark,
  surfaceContainer = Color(0xFF281B24),
  surfaceContainerHigh = Color(0xFF33252E),
  surfaceContainerHighest = Color(0xFF3E3039),
)

private val LightColorScheme = lightColorScheme(
  primary = BtuPrimaryLight,
  onPrimary = BtuOnPrimaryLight,
  primaryContainer = BtuContainerLight,
  onPrimaryContainer = BtuOnContainerLight,
  secondary = IndigoSecondaryLight,
  onSecondary = IndigoOnSecondaryLight,
  secondaryContainer = IndigoContainerLight,
  onSecondaryContainer = IndigoOnContainerLight,
  tertiary = AmberTertiaryLight,
  onTertiary = AmberOnTertiaryLight,
  tertiaryContainer = AmberContainerLight,
  onTertiaryContainer = AmberOnContainerLight,
  background = BackgroundLight,
  onBackground = OnSurfaceLight,
  surface = SurfaceLight,
  onSurface = OnSurfaceLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = OnSurfaceVariantLight,
  outline = OutlineLight,
  outlineVariant = Color(0xFFDCC2CF),
  surfaceTint = BtuPrimaryLight,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFFFF0F5),
  surfaceContainer = Color(0xFFFBE9F1),
  surfaceContainerHigh = Color(0xFFF5E3EB),
  surfaceContainerHighest = Color(0xFFEFDEE6),
)

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun BtucmTheme(
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    AppThemeMode.LIGHT -> false
    AppThemeMode.DARK -> true
  }

  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      view.context.findActivity()?.let { activity ->
        WindowCompat.getInsetsController(activity.window, view).apply {
          isAppearanceLightStatusBars = !darkTheme
          isAppearanceLightNavigationBars = !darkTheme
        }
      }
    }
  }

  CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
    MaterialExpressiveTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      motionScheme = MotionScheme.expressive(),
      content = content,
    )
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
