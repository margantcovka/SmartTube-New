package io.github.margantcovka.tube.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liskovsoft.smartyoutubetv2.tv.R

/** Дизайн-токены в духе лаунчера. Экран 1080p на Android TV — это 960×540 dp. */
object Tokens {
    val background = Color(0xFF0B0B0D)
    val textPrimary = Color(0xFFF2F2F7)
    val textSecondary = Color(0x99EBEBF5)
    val cardTitle = Color(0xDDEBEBF5)
    val placeholder = Color(0xFF1C1C1E)

    val sidePadding = 48.dp
    val topBarHeight = 76.dp
    /** Высота заголовка над первым рядом: название и автор видео в фокусе. */
    val heroHeight = 150.dp
    /** Где встаёт ряд в фокусе, когда первый ряд уехал вверх. */
    val scrolledRowTop = 4.dp

    val cardWidth = 200.dp
    val shortsWidth = 120.dp
    val cardGap = 20.dp
    val rowGap = 22.dp
    val cardCorner = 10.dp
    val shadowBlur = 12.dp
    const val focusedScale = 1.08f
    const val gridColumns = 4
    const val shortsColumns = 6

    val heroTitleSize = 30.sp
    val heroMetaSize = 15.sp
    val rowTitleSize = 17.sp
    val cardTitleSize = 13.sp
    val cardMetaSize = 12.sp
    val tabSize = 16.sp

    const val focusMs = 160
    const val backdropFadeMs = 400
    const val shelfFadeMs = 300
    /** Пауза перед загрузкой раздела, пока фокус бегает по вкладкам. */
    const val sectionDebounceMs = 350L

    val rowFocused = Color(0xFFF2F2F7)
    val rowText = Color(0xFFF2F2F7)
    val rowTextFocused = Color(0xFF111113)
    val tabSelected = Color(0x33FFFFFF)
}

val InterDisplay = FontFamily(
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
)
