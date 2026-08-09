package dev.chardoncs.ezmpv.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            repeatDelayMillis = 3000,
            initialDelayMillis = 3000,
        ),
    )
}
