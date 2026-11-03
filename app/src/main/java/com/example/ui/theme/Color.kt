package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Frosted Glass Vibe Palette
val DeepSlateBg = Color(0xFF020617)       // Deep slate background (#020617)
val GlassBg = Color(0x0DFFFFFF)           // Semi-transparent White (5%)
val GlassBgUp = Color(0x14FFFFFF)         // Semi-transparent White (8%)
val GlassBorder = Color(0x1AFFFFFF)       // Glass Outline White (10%)

val GlowIndigo = Color(0xFF4F46E5)        // Indigo-600 (#4F46E5)
val GlowFuchsia = Color(0xFFD946EF)       // Fuchsia-500 (#D946EF)
val AccentIndigoLight = Color(0xFF818CF8) // Indigo-400 (#818CF8)
val AccentFuchsiaLight = Color(0xFFE879F9) // Fuchsia-400 (#E879F9)

// Map existing variables so the entire app updates seamlessly
val PrimaryCyan = AccentIndigoLight
val SecondaryOrange = AccentFuchsiaLight
val AccentBlue = GlowIndigo

val CosmicDarkBg = DeepSlateBg
val CarbonSurface = GlassBg
val CarbonSurfaceUp = GlassBgUp
val NeonBorder = GlassBorder

// Light Theme Alternates (Optional fallback)
val LightBg = Color(0xFFF1F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0F172A)

