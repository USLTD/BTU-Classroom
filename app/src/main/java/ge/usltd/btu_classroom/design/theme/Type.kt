package ge.usltd.btu_classroom.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun campusStyle(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
  TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
  )

val AppTypography = Typography(
  displayLarge = campusStyle(56, 64, FontWeight.Bold),
  displayMedium = campusStyle(44, 52, FontWeight.Bold),
  displaySmall = campusStyle(36, 44, FontWeight.Bold),
  headlineLarge = campusStyle(32, 40, FontWeight.Bold),
  headlineMedium = campusStyle(28, 36, FontWeight.Bold),
  headlineSmall = campusStyle(24, 32, FontWeight.Bold),
  titleLarge = campusStyle(22, 28, FontWeight.SemiBold),
  titleMedium = campusStyle(18, 24, FontWeight.SemiBold),
  titleSmall = campusStyle(14, 20, FontWeight.SemiBold),
  bodyLarge = campusStyle(16, 24),
  bodyMedium = campusStyle(14, 20),
  bodySmall = campusStyle(13, 20),
  labelLarge = campusStyle(14, 20, FontWeight.SemiBold),
  labelMedium = campusStyle(12, 16, FontWeight.Medium),
  labelSmall = campusStyle(12, 16),
)
