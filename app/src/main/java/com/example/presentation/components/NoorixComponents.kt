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
 * Modern Noorix Card with subtle border and dark elevated container.
 */
@Composable
fun NoorixCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SubtleBorder,
    borderWidth: Dp = 1.dp,
    containerColor: Color = NearBlackSurface,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.border(borderWidth, borderColor, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(content = content)
    }
}

/**
 * Segmented Progress Bar for mastery and study milestones.
 */
@Composable
fun NoorixSegmentedProgressBar(
    progress: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    totalSegments: Int = 16,
    activeColor: Color = TealAccent,
    inactiveColor: Color = SubtleBorder
) {
    val filledCount = (progress.coerceIn(0f, 1f) * totalSegments).toInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(NearBlackBg)
            .border(1.dp, SubtleBorder, RoundedCornerShape(7.dp))
            .padding(horizontal = 3.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSegments) {
                val isFilled = i < filledCount
                val color = if (isFilled) activeColor else inactiveColor

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}

/**
 * Clean Top App Bar with official Noorix Logo and quick navigation actions.
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Noorix Brand Logo & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_noorix_logo),
                        contentDescription = "Noorix Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "NOORIX LEARNING",
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Cambridge O-Level Runtime",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                // Profile / Settings Action
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings & AI Engine",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
