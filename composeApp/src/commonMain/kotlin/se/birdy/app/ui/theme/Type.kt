package se.birdy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Serif placeholder for Crimson Pro — Plan 3 will swap to the real font asset.
private val DisplaySerif = FontFamily.Serif
private val UiSans = FontFamily.SansSerif

val BirdyTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = DisplaySerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 36.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = DisplaySerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                lineHeight = 30.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = DisplaySerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 26.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = DisplaySerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = UiSans,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = UiSans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = UiSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
            ),
    )
