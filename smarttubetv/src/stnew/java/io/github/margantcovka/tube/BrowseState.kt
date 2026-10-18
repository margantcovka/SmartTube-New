package io.github.margantcovka.tube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData

/** Ряд видео. В разделах-сетках весь раздел — один ряд с id [GRID_ROW]. */
class VideoRow(val id: Int, title: String?, val shorts: Boolean) {
    var title by mutableStateOf(title)
    val videos = mutableStateListOf<Video>()
    /** Размер, при котором уже попросили следующую страницу: не просить дважды. */
    var requestedMoreAt = -1

    companion object {
        const val GRID_ROW = Int.MIN_VALUE
    }
}

/**
 * Состояние главной, которое наполняет презентер SmartTube через [NovaBrowseActivity].
 * Правила разбора групп повторяют старые Leanback-экраны (MultipleRowsFragment, VideoGridFragment).
 */
class BrowseState {
    val sections = mutableStateListOf<BrowseSection>()
    var selected by mutableIntStateOf(-1)
    val rows = mutableStateListOf<VideoRow>()
    val settings = mutableStateListOf<SettingsItem>()
    var error by mutableStateOf<ErrorFragmentData?>(null)
    var loading by mutableStateOf(false)
    var focusedVideo by mutableStateOf<Video?>(null)
    /** Растёт, когда презентер просит перевести фокус на содержимое. */
    var focusContentRequests by mutableIntStateOf(0)

    val currentSection: BrowseSection? get() = sections.getOrNull(selected)

    val isGrid: Boolean get() = currentSection?.type.let {
        it == BrowseSection.TYPE_GRID || it == BrowseSection.TYPE_MULTI_GRID || it == BrowseSection.TYPE_SHORTS_GRID
    }

    val isSettings: Boolean get() = currentSection?.type == BrowseSection.TYPE_SETTINGS_GRID

    val isEmpty: Boolean get() = if (isSettings) settings.isEmpty() else rows.all { it.videos.isEmpty() }

    fun clearContent() {
        rows.clear()
        settings.clear()
        error = null
        focusedVideo = null
    }

    fun belongsHere(section: BrowseSection?): Boolean = section == null || section.id == currentSection?.id

    fun apply(group: VideoGroup) {
        if (!belongsHere(group.section)) return
        error = null
        val grid = isGrid
        val rowId = if (grid) VideoRow.GRID_ROW else group.id
        fun row() = rows.firstOrNull { it.id == rowId }

        when (group.action) {
            VideoGroup.ACTION_REPLACE -> if (grid || group.position == -1) rows.clear() else rows.removeAll { it.id == rowId }
            VideoGroup.ACTION_REMOVE -> {
                val gone = group.videos.orEmpty()
                row()?.videos?.removeAll { v -> gone.any { it == v } }
                return
            }
            VideoGroup.ACTION_REMOVE_AUTHOR -> {
                val channels = group.videos.orEmpty().mapNotNull { it.channelId }.toSet()
                rows.forEach { r -> r.videos.removeAll { it.channelId in channels } }
                return
            }
            VideoGroup.ACTION_SYNC -> {
                row()?.let { r ->
                    group.videos.orEmpty().forEach { fresh ->
                        val i = r.videos.indexOfFirst { it == fresh }
                        if (i >= 0) r.videos[i] = fresh
                    }
                }
                return
            }
        }
        if (group.isEmpty) return

        val existing = row()
        if (existing == null) {
            val created = VideoRow(rowId, if (grid) null else group.title, group.isShorts || currentSection?.type == BrowseSection.TYPE_SHORTS_GRID)
            created.videos.addAll(group.videos)
            val at = group.position
            if (at < 0 || at > rows.size) rows.add(created) else rows.add(at, created)
        } else if (group.action == VideoGroup.ACTION_PREPEND) {
            existing.videos.addAll(0, group.videos)
        } else {
            existing.videos.addAll(group.videos)
        }
    }
}
