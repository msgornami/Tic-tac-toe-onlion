package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    secondary = SecondaryOrange,
    tertiary = AccentBlue,
    background = CosmicDarkBg,
    surface = CarbonSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.Black,
    onSecondary = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = SecondaryOrange,
    tertiary = PrimaryCyan,
    background = LightBg,
    surface = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onPrimary = Color.White,
    onSecondary = Color.White
)

val LocalAppTheme = androidx.compose.runtime.compositionLocalOf { "AUTO" }

@Composable
fun MyApplicationTheme(
    theme: String = "AUTO",
    darkTheme: Boolean = true, // Force modern dark-mode first for gaming aesthetics, or support settings
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    androidx.compose.runtime.CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun FrostedGlassBackground(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    if (theme == "SKETCH") {
        PaperBackground(modifier = modifier, content = content)
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .fillMaxSize()
                .background(CosmicDarkBg)
                .drawBehind {
                    // Top-Left Indigo Radial Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4F46E5).copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(-size.width * 0.1f, -size.height * 0.15f),
                            radius = size.width * 0.85f
                        )
                    )
                    // Bottom-Right Fuchsia Radial Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFD946EF).copy(alpha = 0.22f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 1.1f, size.height * 1.15f),
                            radius = size.width * 0.95f
                        )
                    )
                },
            content = content
        )
    }
}

@Composable
fun PaperBackground(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9F7F1)) // Beautiful creamy sketchbook/parchment paper background
            .drawBehind {
                val h = size.height
                val w = size.width

                // Left notebook pink/red margin line
                val marginX = with(density) { 45.dp.toPx() }
                drawLine(
                    color = Color(0xFFE11D48).copy(alpha = 0.25f), // Hand-ruled soft pink margin
                    start = Offset(marginX, 0f),
                    end = Offset(marginX, h),
                    strokeWidth = 2f
                )

                // Horizontal soft gray sketch lines
                val lineSpacing = with(density) { 26.dp.toPx() }
                var y = with(density) { 55.dp.toPx() }
                while (y < h) {
                    drawLine(
                        color = Color(0xFF475569).copy(alpha = 0.10f), // Soft pencil lead guide lines
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    y += lineSpacing
                }

                // Add small playful hand-drawn pencil doodles in the corners
                val doodleColor = Color(0xFF0F172A).copy(alpha = 0.05f)
                val gridX = w - with(density) { 85.dp.toPx() }
                val gridY = h - with(density) { 100.dp.toPx() }
                val gridSz = with(density) { 45.dp.toPx() }

                // Small pencil tic-tac-toe grid decoration in bottom right
                drawLine(doodleColor, Offset(gridX + gridSz/3, gridY), Offset(gridX + gridSz/3, gridY + gridSz), strokeWidth = 1.5f)
                drawLine(doodleColor, Offset(gridX + 2*gridSz/3, gridY), Offset(gridX + 2*gridSz/3, gridY + gridSz), strokeWidth = 1.5f)
                drawLine(doodleColor, Offset(gridX, gridY + gridSz/3), Offset(gridX + gridSz, gridY + gridSz/3), strokeWidth = 1.5f)
                drawLine(doodleColor, Offset(gridX, gridY + 2*gridSz/3), Offset(gridX + gridSz, gridY + 2*gridSz/3), strokeWidth = 1.5f)
            },
        content = content
    )
}

// Custom Extension modifiers for sketchy pencil appearance
fun androidx.compose.ui.Modifier.sketchBorder(
    color: Color = Color(0xFF1E293B),
    strokeWidth: Float = 2.2f,
    roughness: Float = 4.2f
): androidx.compose.ui.Modifier = this.drawBehind {
    val h = size.height
    val w = size.width
    val random = java.util.Random(42) // Fixed seed based on composition coordinates to avoid dynamic jitter on recompositions

    fun drawSketchySegment(x1: Float, y1: Float, x2: Float, y2: Float) {
        // Draw 2 slightly variant strokes to simulate overlapping pencil trace line
        for (i in 0..1) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(x1, y1)
            val steps = 3
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val px = x1 + (x2 - x1) * t
                val py = y1 + (y2 - y1) * t

                if (step < steps) {
                    val offsetX = (random.nextFloat() - 0.5f) * roughness
                    val offsetY = (random.nextFloat() - 0.5f) * roughness
                    path.lineTo(px + offsetX, py + offsetY)
                } else {
                    path.lineTo(x2, y2)
                }
            }

            drawPath(
                path = path,
                color = color.copy(alpha = if (i == 0) 0.8f else 0.45f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth + (random.nextFloat() - 0.5f) * 0.8f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }

    // Four sides of the cards with charming hand-sketched wobbly overlapping ends
    drawSketchySegment(-4f, 0f, w + 4f, 0f)
    drawSketchySegment(w, -4f, w, h + 4f)
    drawSketchySegment(w + 4f, h, -4f, h)
    drawSketchySegment(0f, h + 4f, 0f, -4f)
}

fun androidx.compose.ui.Modifier.sketchShading(
    color: Color = Color(0xFF1E293B),
    spacing: Float = 16f
): androidx.compose.ui.Modifier = this.drawBehind {
    val h = size.height
    val w = size.width
    var x = 0f
    while (x < w + h) {
        val x1 = x.coerceIn(0f, w)
        val y1 = (x - x1).coerceIn(0f, h)

        val x2 = (x - h).coerceIn(0f, w)
        val y2 = (x - x2).coerceIn(0f, h)

        if (y2 - y1 > 5f) {
            drawLine(
                color = color.copy(alpha = 0.05f), // Very translucent diagonal sketch shading
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 1f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        x += spacing
    }
}

@Composable
fun androidx.compose.ui.Modifier.maybeSketchCard(
    backgroundColor: Color = Color(0xFFFFFDF9), // Warm, slightly brighter sketching card paper
    borderColor: Color = Color(0xFF334155),
    roughness: Float = 4.0f
): androidx.compose.ui.Modifier {
    val theme = LocalAppTheme.current
    return if (theme == "SKETCH") {
        this
            .background(backgroundColor, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .sketchBorder(borderColor, strokeWidth = 2f, roughness = roughness)
            .sketchShading(borderColor, spacing = 18f)
    } else {
        this
    }
}

@Composable
fun androidx.compose.ui.Modifier.maybeSketchBorder(
    color: Color = Color(0xFF334155),
    roughness: Float = 4.0f
): androidx.compose.ui.Modifier {
    val theme = LocalAppTheme.current
    return if (theme == "SKETCH") {
        this.sketchBorder(color, strokeWidth = 2f, roughness = roughness)
    } else {
        this
    }
}

@Composable
fun textColorForTheme(defaultColor: Color = Color.White): Color {
    val theme = LocalAppTheme.current
    return if (theme == "SKETCH") Color(0xFF0F172A) else defaultColor // Crisp slate/blue-black ink color instead of white
}

@Composable
fun subtitleColorForTheme(defaultColor: Color = Color.White.copy(alpha = 0.6f)): Color {
    val theme = LocalAppTheme.current
    return if (theme == "SKETCH") Color(0xFF334155) else defaultColor // Blue-gray ink instead of white alpha
}

@Composable
fun accentColorForTheme(defaultColor: Color): Color {
    val theme = LocalAppTheme.current
    return if (theme == "SKETCH") Color(0xFF2563EB) else defaultColor // Ballpoint pencil blue instead of primary cyan/fuschia/etc
}


