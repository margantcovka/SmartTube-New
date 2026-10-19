package io.github.margantcovka.tube.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width

/**
 * Что показывает панель плеера. Заполняет NovaPlaybackActivity из PlaybackFragment, пока панель
 * открыта; сами кнопки и управление остаются у фрагмента, а здесь только рисуется.
 */
class PlayerOverlayState {
    var visible by mutableStateOf(false)
    var title by mutableStateOf<String?>(null)
    var author by mutableStateOf<String?>(null)
    var positionMs by mutableLongStateOf(0)
    var durationMs by mutableLongStateOf(0)
    /** Куда перематывают прямо сейчас, или -1. */
    var seekMs by mutableLongStateOf(-1)
    var playing by mutableStateOf(false)
    /** Границы прогресс-бара Leanback на экране (px): панель рисуется ровно на его месте. */
    var barLeft by mutableFloatStateOf(0f)
    var barTop by mutableFloatStateOf(0f)
    var barWidth by mutableFloatStateOf(0f)
    var barHeight by mutableFloatStateOf(0f)
    /** Верх ряда кнопок: над ним встаёт название. */
    var dockTop by mutableFloatStateOf(0f)
}

private val titleStyle = TextStyle(color = Tokens.textPrimary, fontSize = Tokens.heroTitleSize, fontFamily = InterDisplay, fontWeight = FontWeight.Bold)
private val authorStyle = TextStyle(color = Tokens.textSecondary, fontSize = Tokens.heroMetaSize, fontFamily = InterDisplay, fontWeight = FontWeight.Medium)
private val timeStyle = TextStyle(color = Tokens.textSecondary, fontSize = Tokens.heroMetaSize, fontFamily = InterDisplay, fontWeight = FontWeight.Medium)

private val trackColor = Color(0x33FFFFFF)
private val fillColor = Tokens.textPrimary

internal fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun PlayerOverlay(state: PlayerOverlayState) {
    if (!state.visible) return
    val density = LocalDensity.current
    val shown = if (state.seekMs >= 0) state.seekMs else state.positionMs
    val fraction = if (state.durationMs > 0) (shown.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val barWidthDp = with(density) { state.barWidth.toDp() }
    val barHeightDp = with(density) { state.barHeight.toDp() }

    Box(Modifier.fillMaxSize()) {
        // Название и автор над рядом кнопок.
        Box(
            Modifier
                .offset { IntOffset(state.barLeft.toInt(), 0) }
                .width(barWidthDp)
                .offset { IntOffset(0, (state.dockTop - 96.dp.toPx()).toInt().coerceAtLeast(0)) },
        ) {
            androidx.compose.foundation.layout.Column {
                BasicText(state.title.orEmpty(), style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!state.author.isNullOrBlank()) BasicText(state.author.orEmpty(), style = authorStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Прогресс: на месте скрытого бара Leanback (он живой и принимает перемотку, но не рисуется).
        Box(
            Modifier
                .offset { IntOffset(state.barLeft.toInt(), state.barTop.toInt()) }
                .width(barWidthDp)
        ) {
            Box(
                Modifier
                    .width(barWidthDp)
                    .drawBehind {
                        val h = 6.dp.toPx()
                        val y = (size.height.coerceAtLeast(h) - h) / 2f
                        val radius = CornerRadius(h / 2f)
                        drawRoundRect(trackColor, Offset(0f, y), Size(size.width, h), radius)
                        drawRoundRect(fillColor, Offset(0f, y), Size(size.width * fraction, h), radius)
                        if (state.seekMs >= 0) drawCircle(fillColor, 9.dp.toPx(), Offset(size.width * fraction, y + h / 2f))
                    }
                    .padding(vertical = barHeightDp / 2),
            )
        }
        // Время под баром справа одной строкой: слева там ряд вторичных кнопок.
        Box(
            Modifier
                .offset { IntOffset(state.barLeft.toInt(), (state.barTop + state.barHeight).toInt()) }
                .width(barWidthDp),
        ) {
            BasicText("${formatTime(shown)} / ${formatTime(state.durationMs)}", style = timeStyle, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd))
        }
    }
}
