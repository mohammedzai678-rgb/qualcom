package com.deepshield.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepshield.ai.ui.theme.*

/**
 * Bottom navigation bar with 5 primary destinations.
 * Features a neon indicator line on the active item.
 */
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector = icon
)

val bottomNavItems = listOf(
    NavItem("dashboard", "Home", Icons.Rounded.Home, Icons.Rounded.Home),
    NavItem("live_camera", "Live", Icons.Rounded.CameraAlt, Icons.Rounded.CameraAlt),
    NavItem("gallery", "Scan", Icons.Rounded.ImageSearch, Icons.Rounded.ImageSearch),
    NavItem("watermark", "Shield", Icons.Rounded.VerifiedUser, Icons.Rounded.VerifiedUser),
    NavItem("settings", "More", Icons.Rounded.Menu, Icons.Rounded.Menu)
)

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        CyberBlack.copy(alpha = 0.9f),
                        CyberBlack
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .clip(CardShape)
                .background(SurfaceCard.copy(alpha = 0.95f), CardShape)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route

                BottomNavItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) NeonCyan else TextTertiary,
        animationSpec = tween(300),
        label = "navIconColor"
    )

    val bgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.12f else 0f,
        animationSpec = tween(300),
        label = "navBgAlpha"
    )

    Column(
        modifier = Modifier
            .clip(ChipShape)
            .background(NeonCyan.copy(alpha = bgAlpha), ChipShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = if (isSelected) item.activeIcon else item.icon,
            contentDescription = item.label,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )

        Text(
            text = item.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = iconColor
        )
    }
}
