package com.deepshield.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================
// DeepShield AI — Shape System
// Rounded corners for the glassmorphism aesthetic
// ============================================================

val DeepShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Custom shape tokens
val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(12.dp)
val ChipShape = RoundedCornerShape(8.dp)
val BadgeShape = RoundedCornerShape(6.dp)
val BottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val DialogShape = RoundedCornerShape(20.dp)
val GaugeShape = RoundedCornerShape(50) // Full circle
val PillShape = RoundedCornerShape(50) // Pill shape for tags
