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
 * Top App Bar with official Noorix Logo, Search, Streak and Profile
 */
@Composable
fun NoorixTopBar(
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSystemHudClick: () -> Unit = {}
) {
    Surface(
        color = CyberDarkBg,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Noorix Brand Logo + Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSystemHudClick() }
                        .padding(4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_noorix_logo),
                        contentDescription = "Noorix Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, NeonCyan, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NOORIX",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonCyan,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "LEARNING SOLUTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextSecondary,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Action controls: Streak + System HUD + User Profile
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Streak Badge (🔥 12 Days)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CyberPanelElevated,
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🔥", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "12d",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberGold
                            )
                        }
                    }

                    // System HUD inspector button
                    IconButton(
                        onClick = onSystemHudClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "System Security HUD",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // User Profile chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = CyberPanelElevated,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onProfileClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_student_avatar),
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ali",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberTextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
