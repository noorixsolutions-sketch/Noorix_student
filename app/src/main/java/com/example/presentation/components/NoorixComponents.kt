package com.example.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

/**
 * High-tech cybernetic card with glowing cyan border and subtle dark gradient.
 */
@Composable
fun NoorixCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NeonCyan.copy(alpha = 0.35f),
    borderWidth: Dp = 1.dp,
    containerColor: Color = CyberPanel,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .border(borderWidth, borderColor, shape)
            .shadow(4.dp, shape = shape, spotColor = NeonCyan.copy(alpha = 0.2f)),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(content = content)
    }
}

/**
 * Segmented Glowing Cyber Progress Bar, matching Image 2 "Your Learning Path 68%".
 */
@Composable
fun NoorixSegmentedProgressBar(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    totalSegments: Int = 18,
    activeColor: Color = NeonCyan,
    inactiveColor: Color = CyberPanelBorder
) {
    val filledCount = (progress.coerceIn(0f, 1f) * totalSegments).toInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSegments) {
                val isFilled = i < filledCount
                val segmentAlpha = if (isFilled) 1.0f else 0.25f
                val color = if (isFilled) activeColor else inactiveColor

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = segmentAlpha))
                )
            }
        }
    }
}

/**
 * Minimal Top App Bar with official Noorix Logo and Profile
 */
@Composable
fun NoorixTopBar(
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSystemHudClick: () -> Unit = {}
) {
    Surface(
        color = NearBlackBg,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Noorix Brand Logo
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_noorix_logo),
                        contentDescription = "Noorix Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                // Profile / Settings Entry
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
