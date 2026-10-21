package tw.kuies.link.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Platforms shown as a *source explanation* on the home screen, never as share buttons. */
enum class SocialPlatform(val label: String) {
    Instagram("Instagram"),
    Threads("Threads"),
    Facebook("Facebook"),
}

/**
 * Monochrome brand marks drawn with Canvas so they match the app's line-icon family and stay
 * legible in both themes without shipping brand assets.
 */
@Composable
fun SocialPlatformIcon(
    platform: SocialPlatform,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = w * 0.075f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        when (platform) {
            SocialPlatform.Instagram -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.13f, h * 0.13f),
                    size = Size(w * 0.74f, h * 0.74f),
                    cornerRadius = CornerRadius(w * 0.22f),
                    style = stroke,
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.17f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = stroke,
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.042f,
                    center = Offset(w * 0.71f, h * 0.29f),
                )
            }

            SocialPlatform.Threads -> {
                val loop = Path().apply {
                    moveTo(w * 0.62f, h * 0.12f)
                    cubicTo(w * 0.36f, h * 0.10f, w * 0.22f, h * 0.30f, w * 0.23f, h * 0.52f)
                    cubicTo(w * 0.24f, h * 0.76f, w * 0.45f, h * 0.89f, w * 0.64f, h * 0.84f)
                    cubicTo(w * 0.79f, h * 0.80f, w * 0.84f, h * 0.65f, w * 0.79f, h * 0.53f)
                    cubicTo(w * 0.74f, h * 0.39f, w * 0.58f, h * 0.34f, w * 0.46f, h * 0.40f)
                    cubicTo(w * 0.36f, h * 0.45f, w * 0.34f, h * 0.58f, w * 0.42f, h * 0.65f)
                    cubicTo(w * 0.50f, h * 0.72f, w * 0.62f, h * 0.67f, w * 0.64f, h * 0.57f)
                    cubicTo(w * 0.65f, h * 0.49f, w * 0.60f, h * 0.43f, w * 0.54f, h * 0.41f)
                }
                drawPath(loop, tint, style = stroke)
            }

            SocialPlatform.Facebook -> {
                drawCircle(tint, radius = w * 0.38f, style = stroke)
                val f = Path().apply {
                    moveTo(w * 0.55f, h * 0.80f)
                    lineTo(w * 0.55f, h * 0.47f)
                    moveTo(w * 0.55f, h * 0.47f)
                    cubicTo(w * 0.55f, h * 0.31f, w * 0.62f, h * 0.24f, w * 0.74f, h * 0.24f)
                    moveTo(w * 0.40f, h * 0.53f)
                    lineTo(w * 0.67f, h * 0.53f)
                }
                drawPath(f, tint, style = stroke)
            }
        }
    }
}
