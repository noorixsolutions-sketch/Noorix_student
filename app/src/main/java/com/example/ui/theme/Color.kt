package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// MINIMAL DARK & CALM TEAL DESIGN SYSTEM
// Focused, distraction-free environment for O-Level study
// ==========================================

// Surfaces & Backgrounds
val NearBlackBg = Color(0xFF090D12)           // Pure dark background
val NearBlackSurface = Color(0xFF111722)      // Subtle card surface
val NearBlackElevated = Color(0xFF17202D)     // Active focus card
val SubtleBorder = Color(0xFF1E2A38)          // Thin quiet card border
val SubtleBorderActive = Color(0xFF2E4156)    // Focused card border

// Single Teal Accent (Used sparingly for active focus points)
val TealAccent = Color(0xFF00D4C5)            // Luminous clear teal
val TealAccentMuted = Color(0xFF004D47)       // Low-contrast teal
val TealAccentSubtle = Color(0x1F00D4C5)      // 12% opacity teal highlight

// Feedback Accents
val StepCorrect = Color(0xFF2DD4BF)           // Calm mint/teal check
val StepIssue = Color(0xFFFF6B6B)             // Gentle coral for needs-attention

// Typography Hierarchy
val TextPrimary = Color(0xFFF1F5F9)          // Clean readable white
val TextSecondary = Color(0xFF94A3B8)        // Soft muted slate
val TextMuted = Color(0xFF64748B)            // Subdued captions and helpers

// Compatibility aliases to prevent breakage in any legacy references
val CyberDarkBg = NearBlackBg
val CyberPanel = NearBlackSurface
val CyberPanelElevated = NearBlackElevated
val CyberPanelBorder = SubtleBorder
val CyberBorder = SubtleBorder
val CyberBorderGlow = TealAccent
val NeonCyan = TealAccent
val NeonCyanGlow = TealAccent
val NeonCyanDim = TealAccentMuted
val CyanAccent = TealAccent
val CyanGlow = TealAccent
val CyberGreen = StepCorrect
val CyberGold = Color(0xFFFFB020)
val GoldAccent = CyberGold
val GoldSubtle = Color(0xFFFFB020)
val CyberRed = StepIssue
val CyberBlue = Color(0xFF38BDF8)
val CyberTextPrimary = TextPrimary
val CyberTextSecondary = TextSecondary
val CyberTextMuted = TextMuted
val OffWhite = TextPrimary
val SlateBorder = SubtleBorder
val Navy900 = NearBlackBg
val Navy800 = NearBlackSurface
val Navy700 = NearBlackElevated
val SurfaceDark = NearBlackSurface
val CardDark = NearBlackElevated
val SageGreen = StepCorrect
val CrimsonRed = StepIssue
val SlateGray = TextSecondary
