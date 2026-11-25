package com.openlauncher.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun launcherTypography(bold: Boolean, scale: Float): Typography {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    return Typography(
        displayLarge   = TextStyle(fontWeight = FontWeight.Bold,   fontSize = (64 * scale).sp),
        displayMedium  = TextStyle(fontWeight = FontWeight.Bold,   fontSize = (52 * scale).sp),
        headlineLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (36 * scale).sp),
        headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (32 * scale).sp),
        headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (28 * scale).sp),
        titleLarge     = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (24 * scale).sp),
        titleMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = (16 * scale).sp),
        titleSmall     = TextStyle(fontWeight = FontWeight.Medium, fontSize = (14 * scale).sp),
        bodyLarge      = TextStyle(fontWeight = weight,            fontSize = (16 * scale).sp),
        bodyMedium     = TextStyle(fontWeight = weight,            fontSize = (14 * scale).sp),
        bodySmall      = TextStyle(fontWeight = weight,            fontSize = (12 * scale).sp),
        labelLarge     = TextStyle(fontWeight = FontWeight.Medium, fontSize = (14 * scale).sp),
        labelMedium    = TextStyle(fontWeight = FontWeight.Medium, fontSize = (12 * scale).sp),
        labelSmall     = TextStyle(fontWeight = FontWeight.Medium, fontSize = (11 * scale).sp),
    )
}
