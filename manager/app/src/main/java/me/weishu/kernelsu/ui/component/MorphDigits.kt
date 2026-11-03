package me.weishu.kernelsu.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

@Composable
fun MorphDigits(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
) {
    val previous = remember { mutableStateOf(value) }
    val rising = value.length > previous.value.length ||
        (value.length == previous.value.length && value > previous.value)
    LaunchedEffect(value) { previous.value = value }
    Row(modifier = modifier.semantics { contentDescription = value }) {
        value.forEachIndexed { index, digit ->
            key(index) {
                AnimatedContent(
                    targetState = digit,
                    transitionSpec = {
                        val enter = slideInVertically(tween(220)) { height -> if (rising) height else -height } +
                            fadeIn(tween(220))
                        val exit = slideOutVertically(tween(220)) { height -> if (rising) -height else height } +
                            fadeOut(tween(220))
                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    label = "morphDigit",
                ) { target ->
                    Text(text = target.toString(), style = style, color = color, maxLines = 1)
                }
            }
        }
    }
}
