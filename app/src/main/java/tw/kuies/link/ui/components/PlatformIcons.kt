package tw.kuies.link.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tw.kuies.link.R

/** Platforms shown as a *source explanation* on the home screen, never as share buttons. */
enum class SocialPlatform(
    val label: String,
    @param:DrawableRes val iconRes: Int,
) {
    Instagram("Instagram", R.drawable.ic_platform_instagram),
    Threads("Threads", R.drawable.ic_platform_threads),
    Facebook("Facebook", R.drawable.ic_platform_facebook),
}

/**
 * Official brand silhouette rendered monochrome, so the three marks share one visual weight while
 * keeping the shapes people already recognise.
 */
@Composable
fun SocialPlatformIcon(
    platform: SocialPlatform,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(platform.iconRes),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}
