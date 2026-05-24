package com.deepshield.ai.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// DeepShield AI — Cyber Dark Color Palette
// Premium cybersecurity aesthetic with neon accents
// ============================================================

// Primary Backgrounds
val CyberBlack = Color(0xFF0A0A0F)
val SurfaceDark = Color(0xFF121218)
val SurfaceCard = Color(0xFF1A1A24)
val SurfaceElevated = Color(0xFF22222E)
val SurfaceOverlay = Color(0xFF2A2A38)

// Primary Brand Gradient Colors
val DeepPurple = Color(0xFF7C3AED)
val DeepPurpleLight = Color(0xFF9F67FF)
val DeepPurpleDark = Color(0xFF5B21B6)
val NeonCyan = Color(0xFF00D4FF)
val NeonCyanLight = Color(0xFF67E8F9)
val NeonCyanDark = Color(0xFF0891B2)

// Accent Colors
val NeonGreen = Color(0xFF00FF88)
val NeonGreenDark = Color(0xFF00CC6A)
val ElectricBlue = Color(0xFF3B82F6)
val ElectricBlueDark = Color(0xFF1D4ED8)

// Status Colors
val DangerRed = Color(0xFFFF3366)
val DangerRedDark = Color(0xFFCC0033)
val DangerRedLight = Color(0xFFFF6B8A)
val WarningOrange = Color(0xFFFF6B35)
val WarningOrangeDark = Color(0xFFCC5500)
val WarningAmber = Color(0xFFFFBB33)
val SuccessGreen = Color(0xFF00FF88)
val SuccessGreenDark = Color(0xFF00CC6A)

// Heatmap Colors
val HeatmapHigh = Color(0xFFFF0000)      // Red — highly suspicious
val HeatmapMedium = Color(0xFFFFAA00)    // Yellow/Orange — moderate
val HeatmapLow = Color(0xFF0066FF)       // Blue — low suspicion
val HeatmapSafe = Color(0xFF00FF88)      // Green — verified safe

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xB3FFFFFF)     // 70% white
val TextTertiary = Color(0x66FFFFFF)      // 40% white
val TextDisabled = Color(0x33FFFFFF)      // 20% white

// Glass / Frosted Elements
val GlassWhite = Color(0x08FFFFFF)       // 3% white
val GlassBorder = Color(0x14FFFFFF)      // 8% white
val GlassBorderHover = Color(0x29FFFFFF) // 16% white
val GlassHighlight = Color(0x1AFFFFFF)   // 10% white

// Glow / Shadow Colors (for neon effects)
val PurpleGlow = Color(0x407C3AED)       // 25% purple
val CyanGlow = Color(0x4000D4FF)         // 25% cyan
val RedGlow = Color(0x40FF3366)          // 25% red
val GreenGlow = Color(0x4000FF88)        // 25% green

// Shield Status Colors
val ShieldAuthentic = NeonGreen
val ShieldSuspicious = WarningOrange
val ShieldDeepfake = DangerRed
val ShieldUnknown = TextTertiary

// Chart Colors
val ChartLine1 = NeonCyan
val ChartLine2 = DeepPurple
val ChartLine3 = NeonGreen
val ChartLine4 = WarningOrange
val ChartFill = Color(0x1A00D4FF)        // 10% cyan fill
