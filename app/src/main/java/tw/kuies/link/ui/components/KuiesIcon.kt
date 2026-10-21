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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One line-icon family drawn with Compose Canvas: consistent stroke weight, round caps and no
 * third-party icon dependency. Every icon is decorative unless a content description is supplied.
 */
enum class KuiesIconKind {
    Link,
    LinkOff,
    CheckCircle,
    Info,
    Copy,
    Share,
    Settings,
    Refresh,
    ChevronDown,
    ChevronUp,
    ArrowBack,
    Shield,
    Moon,
    Close,
}

@Composable
fun KuiesIcon(
    kind: KuiesIconKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(
            width = w * 0.075f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke.width, StrokeCap.Round)

        when (kind) {
            KuiesIconKind.Link -> rotate(-45f) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.08f, h * 0.36f),
                    size = Size(w * 0.42f, h * 0.28f),
                    cornerRadius = CornerRadius(h * 0.14f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.50f, h * 0.36f),
                    size = Size(w * 0.42f, h * 0.28f),
                    cornerRadius = CornerRadius(h * 0.14f),
                    style = stroke,
                )
            }

            KuiesIconKind.LinkOff -> {
                rotate(-45f) {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.08f, h * 0.36f),
                        size = Size(w * 0.42f, h * 0.28f),
                        cornerRadius = CornerRadius(h * 0.14f),
                        style = stroke,
                    )
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(w * 0.50f, h * 0.36f),
                        size = Size(w * 0.42f, h * 0.28f),
                        cornerRadius = CornerRadius(h * 0.14f),
                        style = stroke,
                    )
                }
                line(0.16f, 0.16f, 0.84f, 0.84f)
            }

            KuiesIconKind.CheckCircle -> {
                drawCircle(tint, radius = w * 0.40f, style = stroke)
                val check = Path().apply {
                    moveTo(w * 0.30f, h * 0.51f)
                    lineTo(w * 0.44f, h * 0.65f)
                    lineTo(w * 0.71f, h * 0.36f)
                }
                drawPath(check, tint, style = stroke)
            }

            KuiesIconKind.Info -> {
                drawCircle(tint, radius = w * 0.40f, style = stroke)
                drawCircle(tint, radius = w * 0.035f, center = Offset(w * 0.5f, h * 0.31f))
                line(0.5f, 0.46f, 0.5f, 0.71f)
            }

            KuiesIconKind.Copy -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.32f, h * 0.12f),
                    size = Size(w * 0.44f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = stroke,
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, h * 0.28f),
                    size = Size(w * 0.44f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = stroke,
                )
            }

            KuiesIconKind.Share -> {
                val a = Offset(w * 0.24f, h * 0.5f)
                val b = Offset(w * 0.74f, h * 0.25f)
                val c = Offset(w * 0.74f, h * 0.75f)
                drawLine(tint, a, b, stroke.width, StrokeCap.Round)
                drawLine(tint, a, c, stroke.width, StrokeCap.Round)
                drawCircle(tint, radius = w * 0.10f, center = a, style = stroke)
                drawCircle(tint, radius = w * 0.10f, center = b, style = stroke)
                drawCircle(tint, radius = w * 0.10f, center = c, style = stroke)
            }

            KuiesIconKind.Settings -> {
                repeat(8) { index ->
                    rotate(index * 45f) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(w * 0.44f, h * 0.02f),
                            size = Size(w * 0.12f, h * 0.20f),
                            cornerRadius = CornerRadius(w * 0.04f),
                        )
                    }
                }
                drawCircle(tint, radius = w * 0.20f, style = Stroke(width = w * 0.075f))
            }

            KuiesIconKind.Refresh -> {
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.18f),
                    size = Size(w * 0.64f, h * 0.64f),
                    style = stroke,
                )
                line(0.66f, 0.10f, 0.86f, 0.22f)
                line(0.86f, 0.22f, 0.76f, 0.40f)
            }

            KuiesIconKind.ChevronDown -> {
                line(0.28f, 0.42f, 0.5f, 0.63f)
                line(0.5f, 0.63f, 0.72f, 0.42f)
            }

            KuiesIconKind.ChevronUp -> {
                line(0.28f, 0.58f, 0.5f, 0.37f)
                line(0.5f, 0.37f, 0.72f, 0.58f)
            }

            KuiesIconKind.ArrowBack -> {
                line(0.72f, 0.5f, 0.29f, 0.5f)
                line(0.44f, 0.34f, 0.28f, 0.5f)
                line(0.28f, 0.5f, 0.44f, 0.66f)
            }

            KuiesIconKind.Shield -> {
                val shield = Path().apply {
                    moveTo(w * 0.5f, h * 0.10f)
                    cubicTo(w * 0.63f, h * 0.16f, w * 0.72f, h * 0.18f, w * 0.80f, h * 0.20f)
                    lineTo(w * 0.80f, h * 0.48f)
                    cubicTo(w * 0.80f, h * 0.68f, w * 0.65f, h * 0.82f, w * 0.5f, h * 0.90f)
                    cubicTo(w * 0.35f, h * 0.82f, w * 0.20f, h * 0.68f, w * 0.20f, h * 0.48f)
                    lineTo(w * 0.20f, h * 0.20f)
                    cubicTo(w * 0.28f, h * 0.18f, w * 0.37f, h * 0.16f, w * 0.5f, h * 0.10f)
                    close()
                }
                drawPath(shield, tint, style = stroke)
            }

            KuiesIconKind.Moon -> {
                val moon = Path().apply {
                    moveTo(w * 0.64f, h * 0.12f)
                    cubicTo(w * 0.38f, h * 0.18f, w * 0.28f, h * 0.46f, w * 0.38f, h * 0.68f)
                    cubicTo(w * 0.48f, h * 0.86f, w * 0.70f, h * 0.90f, w * 0.83f, h * 0.82f)
                    cubicTo(w * 0.62f, h * 0.79f, w * 0.47f, h * 0.61f, w * 0.47f, h * 0.42f)
                    cubicTo(w * 0.47f, h * 0.27f, w * 0.54f, h * 0.18f, w * 0.64f, h * 0.12f)
                    close()
                }
                drawPath(moon, tint, style = stroke)
            }

            KuiesIconKind.Close -> {
                line(0.28f, 0.28f, 0.72f, 0.72f)
                line(0.72f, 0.28f, 0.28f, 0.72f)
            }
        }
    }
}
