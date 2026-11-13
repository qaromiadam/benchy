package com.benchy.X.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.benchy.X.R
import com.benchy.X.locale.AutoText
import com.benchy.X.ui.theme.JetBrainsMonoFontFamily
import com.benchy.X.ui.theme.LocalBenchyLayoutMetrics

@Composable
fun BenchyCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val metrics = LocalBenchyLayoutMetrics.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding ?: PaddingValues(metrics.cardPadding)),
            content = content
        )
    }
}

@Composable
fun BenchyEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val ringColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val pad = w * 0.14f
        drawArc(
            color = ringColor.copy(alpha = 0.9f),
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = Size(w - pad * 2, h - pad * 2),
            style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        )

        val pulsePath = Path().apply {
            moveTo(w * 0.18f, h * 0.54f)
            lineTo(w * 0.34f, h * 0.54f)
            lineTo(w * 0.43f, h * 0.32f)
            lineTo(w * 0.53f, h * 0.70f)
            lineTo(w * 0.62f, h * 0.43f)
            lineTo(w * 0.69f, h * 0.54f)
            lineTo(w * 0.82f, h * 0.54f)
        }
        drawPath(
            path = pulsePath,
            color = accentColor,
            style = Stroke(
                width = w * 0.068f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(
            color = accentColor,
            radius = w * 0.055f,
            center = Offset(w * 0.5f, h * 0.78f)
        )
    }
}

@Composable
fun AnimatedScoreRing(
    score: Int,
    tierColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    subtitle: String = "%"
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f) * 260f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "scoreRingSweep"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = this.size.width * 0.105f
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(tierColor.copy(alpha = 0.75f), tierColor, tierColor)
                ),
                startAngle = 140f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score$subtitle",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = tierColor
            )
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun BatteryOptExemptionDialog(
    bodyResId: Int,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.BatterySaver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            AutoText(
                resId = R.string.battery_opt_title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            AutoText(
                resId = bodyResId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    runCatching { context.startActivity(intent) }
                    onContinue()
                },
                modifier = Modifier.testTag("battery_opt_allow_btn")
            ) {
                AutoText(resId = R.string.battery_opt_allow)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.testTag("battery_opt_continue_btn")
            ) {
                AutoText(resId = R.string.battery_opt_continue_anyway)
            }
        }
    )
}
