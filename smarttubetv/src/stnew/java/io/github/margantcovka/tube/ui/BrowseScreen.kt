package io.github.margantcovka.tube.ui

import com.liskovsoft.smartyoutubetv2.tv.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import io.github.margantcovka.tube.BrowseState
import io.github.margantcovka.tube.VideoRow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Что экран сообщает наружу (в Activity, а оттуда в презентер SmartTube). */
interface VideoActions {
    fun onVideoFocused(video: Video)
    fun onVideoClicked(video: Video)
    fun onVideoLongClicked(video: Video)
    fun onNeedMore(row: VideoRow)
}

interface BrowseActions : VideoActions {
    fun onSectionChosen(index: Int)
    fun onSectionLongPressed(index: Int)
    fun onSearch()
}

private const val SEARCH_TAB = -1
/** Отступ от верха ряда до верха его обложек: заголовок ряда + поля под увеличение. */
private val rowHeader = 28.dp + 8.dp + 10.dp
private val gridHeader = 10.dp

private val rowTitleStyle = TextStyle(color = Tokens.textPrimary, fontSize = Tokens.rowTitleSize, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold)
internal val plainStyle = TextStyle(color = Tokens.textSecondary, fontSize = Tokens.heroMetaSize, fontFamily = InterDisplay, fontWeight = FontWeight.Medium)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowseScreen(state: BrowseState, actions: BrowseActions) {
    val density = LocalDensity.current
    val videoSpec = remember(density) { cardSpecFor(density.density, Tokens.cardWidth, 16f / 9f) }
    val shortsSpec = remember(density) { cardSpecFor(density.density, Tokens.shortsWidth, 9f / 16f) }

    val tabRequesters = remember { HashMap<Int, FocusRequester>() }
    fun tabRequester(index: Int) = tabRequesters.getOrPut(index) { FocusRequester() }
    val firstCard = remember { FocusRequester() }

    var contentFocused by remember { mutableStateOf(false) }
    var focusedRow by remember { mutableIntStateOf(0) }
    var focusedTab by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val inFirstRow = !contentFocused || focusedRow == 0

    // Фокус на вкладке выбирает раздел, но с паузой: пока фокус бежит по вкладкам, ничего не грузим.
    LaunchedEffect(focusedTab) {
        val tab = focusedTab
        if (tab < 0 || tab == state.selected) return@LaunchedEffect
        delay(Tokens.sectionDebounceMs)
        actions.onSectionChosen(tab)
    }
    // Первый показ: фокус на выбранную вкладку, пока презентер не попросил другое.
    val hasSections = state.sections.isNotEmpty()
    LaunchedEffect(hasSections) {
        if (!hasSections) return@LaunchedEffect
        withFrameNanos { }
        if (!contentFocused) runCatching { tabRequester(state.selected.coerceAtLeast(0)).requestFocus() }
    }
    LaunchedEffect(state.focusContentRequests) {
        if (state.focusContentRequests == 0) return@LaunchedEffect
        repeat(20) { // содержимое может ещё грузиться
            withFrameNanos { }
            if (runCatching { firstCard.requestFocus() }.isSuccess && contentFocused) return@LaunchedEffect
            delay(100)
        }
    }

    // Каждый крупный кусок экрана — свой слой: прокрутка рядов не перерисовывает вкладки и фон.
    // Оверскролл на пульте не нужен, а рисуется каждый кадр.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Box(Modifier.fillMaxSize()) {
            Backdrop(state.focusedVideo, videoSpec, visible = inFirstRow)
            Column(Modifier.fillMaxSize()) {
                TopBar(state, actions, ::tabRequester, onTabFocused = { focusedTab = it })
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onFocusChanged { contentFocused = it.hasFocus }
                        .onPreviewKeyEvent { event ->
                            // Back из содержимого возвращает на вкладки, а не закрывает приложение.
                            if (event.key != Key.Back || !contentFocused) return@onPreviewKeyEvent false
                            if (event.type == KeyEventType.KeyUp) runCatching { tabRequester(state.selected.coerceAtLeast(0)).requestFocus() }
                            true
                        },
                ) {
                    val error = state.error
                    when {
                        error != null -> Message(error.message, error.actionText) { error.onAction() }
                        state.isSettings -> SettingsGrid(state.settings, firstCard)
                        state.isGrid -> VideoGrid(state, videoSpec, shortsSpec, actions, firstCard, inFirstRow) { focusedRow = it }
                        else -> VideoRows(state.rows, state.focusedVideo, state.currentSection?.id, videoSpec, shortsSpec, actions, firstCard, inFirstRow) { focusedRow = it }
                    }
                    if (state.loading && state.isEmpty && error == null) {
                        BasicText(stringResource(R.string.nova_loading), style = plainStyle, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

internal fun cardSpecFor(density: Float, width: Dp, aspect: Float): CardSpec {
    val w = (width.value * density * Tokens.focusedScale).roundToInt()
    val h = (w / aspect).roundToInt()
    return Images.cardSpec(w, h, Tokens.cardCorner.value * density * Tokens.focusedScale, Tokens.shadowBlur.value * density)
}

@Composable
private fun TopBar(state: BrowseState, actions: BrowseActions, tabRequester: (Int) -> FocusRequester, onTabFocused: (Int) -> Unit) {
    val tabs = buildList {
        add(SEARCH_TAB)
        addAll(state.sections.indices)
    }
    @OptIn(ExperimentalComposeUiApi::class)
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(Tokens.topBarHeight).graphicsLayer().focusRestorer(),
        contentPadding = PaddingValues(horizontal = Tokens.sidePadding - 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs.size) { i ->
            val index = tabs[i]
            val title = if (index == SEARCH_TAB) stringResource(R.string.nova_search) else state.sections[index].title.orEmpty()
            Tab(
                title = title,
                selected = index == state.selected,
                onFocused = { onTabFocused(index) },
                onClick = { if (index == SEARCH_TAB) actions.onSearch() else actions.onSectionChosen(index) },
                onLongClick = { if (index != SEARCH_TAB) actions.onSectionLongPressed(index) },
                modifier = Modifier.focusRequester(tabRequester(index)),
            )
        }
    }
}

@Composable
private fun Tab(title: String, selected: Boolean, onFocused: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        focused -> Tokens.rowFocused
        selected -> Tokens.tabSelected
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val color = when {
        focused -> Tokens.rowTextFocused
        selected -> Tokens.textPrimary
        else -> Tokens.textSecondary
    }
    BasicText(
        title,
        style = TextStyle(color = color, fontSize = Tokens.tabSize, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .remoteClick(onClick, onLongClick)
            .focusable()
            .background(fill, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/**
 * Название и автор видео в фокусе над первым рядом — как top shelf в лаунчере. Текст раскладывается
 * в фоне (CardTexts.hero) и только рисуется: раскладка двух крупных строк на UI-потоке при каждом
 * шаге фокуса съедала кадр. Пока новый текст не готов, остаётся прежний.
 */
@Composable
internal fun Hero(video: Video?, padded: Boolean = true) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { (LocalConfiguration.current.screenWidthDp.dp - Tokens.sidePadding * 2).roundToPx() }
    var text by remember { mutableStateOf<CardText?>(null) }
    LaunchedEffect(video, widthPx) {
        text = video?.let { CardTexts.hero(context, it, widthPx) }
    }
    Spacer(
        Modifier.fillMaxWidth().height(Tokens.heroHeight).padding(horizontal = if (padded) Tokens.sidePadding else 0.dp).drawBehind {
            val t = text ?: return@drawBehind
            var bottom = size.height - 18.dp.toPx()
            t.meta?.let {
                bottom -= it.size.height
                drawText(it, color = Tokens.textSecondary, topLeft = Offset(0f, bottom))
                bottom -= 6.dp.toPx()
            }
            drawText(t.title, color = Tokens.textPrimary, topLeft = Offset(0f, bottom - t.title.size.height))
        },
    )
}

/** Тексты карточек раскладываются в фоне, как только приходят данные, — до того как ряд появится на экране. */
@Composable
private fun PrefetchTexts(rows: List<VideoRow>, widthPx: Int, shortsWidthPx: Int = widthPx) {
    val context = LocalContext.current
    val total = rows.sumOf { it.videos.size }
    LaunchedEffect(rows.size, total, widthPx) {
        for (row in rows.toList()) for (video in row.videos.toList()) {
            CardTexts.measure(context, video, if (row.shorts) shortsWidthPx else widthPx)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun VideoRows(
    rows: List<VideoRow>,
    focusedVideo: Video?,
    resetKey: Any?,
    videoSpec: CardSpec,
    shortsSpec: CardSpec,
    actions: VideoActions,
    firstCard: FocusRequester,
    inFirstRow: Boolean,
    onRowFocused: (Int) -> Unit,
) {
    // Ряд рядом с экраном собирается заранее, в паузах между нажатиями, а ушедшие вверх ряды
    // остаются собранными: переход на соседний ряд не собирает десяток карточек за один кадр.
    val listState = rememberLazyListState()
    LaunchedEffect(resetKey) { listState.scrollToItem(0) }
    val density = LocalDensity.current
    val firstRow by rememberUpdatedState(inFirstRow)
    val vertical = remember(density) {
        AnchoredSpec { if (firstRow) with(density) { (Tokens.heroHeight + rowHeader).toPx() } else with(density) { (Tokens.scrolledRowTop + rowHeader).toPx() } }
    }
    val horizontal = remember(density) { AnchoredSpec { with(density) { Tokens.sidePadding.toPx() } } }
    PrefetchTexts(rows, with(density) { Tokens.cardWidth.roundToPx() }, with(density) { Tokens.shortsWidth.roundToPx() })

    CompositionLocalProvider(LocalBringIntoViewSpec provides vertical) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().graphicsLayer(),
            contentPadding = PaddingValues(bottom = 320.dp),
            verticalArrangement = Arrangement.spacedBy(Tokens.rowGap),
        ) {
            item(key = "hero", contentType = "hero") { Hero(focusedVideo) }
            itemsIndexed(rows, key = { _, row -> row.id }, contentType = { _, row -> if (row.shorts) "shorts" else "row" }) { rowIndex, row ->
                val spec = if (row.shorts) shortsSpec else videoSpec
                val width = if (row.shorts) Tokens.shortsWidth else Tokens.cardWidth
                val aspect = if (row.shorts) 9f / 16f else 16f / 9f
                Column(Modifier.fillMaxWidth().graphicsLayer()) {
                    BasicText(
                        row.title.orEmpty(),
                        style = rowTitleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Tokens.sidePadding).height(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    // Обычный прокручиваемый ряд, а не LazyRow: в ленивом ряду каждая карточка — отдельная
                    // композиция, и новый ряд собирался 16–32 мс. Карточки — лёгкие узлы, обложки грузятся
                    // только у видимых и ближайших (loadLimit), так что память не растёт с длиной ряда.
                    val scroll = rememberScrollState()
                    val textWidthPx = with(density) { width.roundToPx() }
                    val pitchPx = with(density) { (width + Tokens.cardGap).toPx() }
                    val viewportPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                    val loadLimit = remember(scroll, pitchPx) { derivedStateOf { ((scroll.value + viewportPx) / pitchPx).toInt() + 2 } }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides horizontal) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .focusRestorer()
                                .horizontalScroll(scroll)
                                .padding(horizontal = Tokens.sidePadding, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(Tokens.cardGap),
                        ) {
                            row.videos.forEachIndexed { i, video ->
                                key(i) {
                                    VideoCard(
                                        video = video,
                                        spec = spec,
                                        aspect = aspect,
                                        textWidthPx = textWidthPx,
                                        index = i,
                                        loadLimit = loadLimit,
                                        onFocused = {
                                            onRowFocused(rowIndex)
                                            actions.onVideoFocused(video)
                                            if (i >= row.videos.size - 6) actions.onNeedMore(row)
                                        },
                                        onClick = { actions.onVideoClicked(video) },
                                        onLongClick = { actions.onVideoLongClicked(video) },
                                        modifier = Modifier.width(width).then(if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstCard) else Modifier),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGrid(
    state: BrowseState,
    videoSpec: CardSpec,
    shortsSpec: CardSpec,
    actions: BrowseActions,
    firstCard: FocusRequester,
    inFirstRow: Boolean,
    onRowFocused: (Int) -> Unit,
) {
    val row = state.rows.firstOrNull() ?: return
    val columns = if (row.shorts) Tokens.shortsColumns else Tokens.gridColumns
    val spec = if (row.shorts) shortsSpec else videoSpec
    val aspect = if (row.shorts) 9f / 16f else 16f / 9f
    val gridState = rememberLazyGridState(cacheWindow = LazyLayoutCacheWindow(ahead = 300.dp, behind = 300.dp))
    val section = state.currentSection?.id
    LaunchedEffect(section) { gridState.scrollToItem(0) }
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val textWidthPx = with(density) { ((screenWidth - Tokens.sidePadding * 2 - Tokens.cardGap * (columns - 1)) / columns).roundToPx() }
    PrefetchTexts(state.rows, textWidthPx)
    val firstRow by rememberUpdatedState(inFirstRow)
    val vertical = remember(density) {
        AnchoredSpec { if (firstRow) with(density) { (Tokens.heroHeight + gridHeader).toPx() } else with(density) { (Tokens.scrolledRowTop + gridHeader).toPx() } }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides vertical) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize().graphicsLayer(),
            contentPadding = PaddingValues(start = Tokens.sidePadding, end = Tokens.sidePadding, top = 0.dp, bottom = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(Tokens.cardGap),
            verticalArrangement = Arrangement.spacedBy(Tokens.rowGap),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }, contentType = "hero") {
                // В сетке боковые поля уже даёт contentPadding.
                Hero(state.focusedVideo, padded = false)
            }
            itemsIndexed(row.videos, key = { i, _ -> i }, contentType = { _, _ -> "card" }) { i, video ->
                VideoCard(
                    video = video,
                    spec = spec,
                    aspect = aspect,
                    textWidthPx = textWidthPx,
                    onFocused = {
                        onRowFocused(i / columns)
                        actions.onVideoFocused(video)
                        if (i >= row.videos.size - 2 * columns) actions.onNeedMore(row)
                    },
                    onClick = { actions.onVideoClicked(video) },
                    onLongClick = { actions.onVideoLongClicked(video) },
                    modifier = Modifier.padding(top = gridHeader).then(if (i == 0) Modifier.focusRequester(firstCard) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun SettingsGrid(items: List<SettingsItem>, firstItem: FocusRequester) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Tokens.sidePadding - 14.dp, end = Tokens.sidePadding, top = 24.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items) { i, item ->
            FocusRow(
                onClick = { item.onClick?.run() },
                modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstItem) else Modifier),
            ) { color ->
                BasicText(item.title.orEmpty(), style = TextStyle(color = color, fontSize = Tokens.heroMetaSize, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun Message(text: String?, action: String?, onAction: () -> Unit) {
    val button = remember { FocusRequester() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BasicText(text.orEmpty(), style = plainStyle)
        if (!action.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Row { FocusRow(onClick = onAction, modifier = Modifier.focusRequester(button)) { color -> BasicText(action, style = plainStyle.copy(color = color)) } }
        }
    }
}

/** Фон всего экрана: размытая обложка видео в фокусе, смешивается на CPU в одну картинку 64×36. */
@Composable
internal fun Backdrop(video: Video?, spec: CardSpec, visible: Boolean) {
    val context = LocalContext.current
    val renderer = remember { ShelfRenderer(Tokens.background.toArgb()) }
    val fade = remember { Animatable(1f) }
    val visibility = animateFloatAsState(if (visible) 1f else 0f, tween(Tokens.shelfFadeMs, easing = LinearEasing), label = "shelf")
    val key = video?.let { Images.thumbKey(it, spec) }
    LaunchedEffect(key) {
        delay(150) // пока фокус бежит по ряду, фон не пересчитываем
        val thumb = key?.let { Images.cachedThumb(it) } ?: video?.let { Images.thumb(context, it, spec) }
        val pixels = if (key != null && thumb != null) Images.shelf(key, thumb) else null
        renderer.retarget(pixels)
        fade.snapTo(0f)
        fade.animateTo(1f, tween(Tokens.backdropFadeMs, easing = LinearEasing))
    }
    Spacer(
        Modifier.fillMaxSize().graphicsLayer().drawBehind {
            drawImage(
                renderer.render(fade.value, visibility.value),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        },
    )
}

/**
 * Прокрутка к элементу в фокусе: его передний край встаёт на [target] пикселей от начала —
 * ряды и карточки не «плавают», как в tvOS. Стандартное поведение на TV держит фокус на 30%.
 */
@OptIn(ExperimentalFoundationApi::class)
private class AnchoredSpec(private val target: () -> Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - target()
}
