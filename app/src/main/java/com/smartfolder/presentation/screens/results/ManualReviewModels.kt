package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.SuggestionItem
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ManualReviewFilter(val label: String) {
    ALL("All"),
    DUPLICATES("Duplicates"),
    VISUAL_GROUPS("Visual Groups"),
    LARGE_FILES("Large Files")
}

enum class ManualReviewSort(val label: String) {
    DUPLICATES("Duplicates"),
    VISUAL_GROUPS("Visual Groups"),
    BATCHES("Batches"),
    NEWEST("Newest"),
    NAME("Name"),
    LARGEST("Largest")
}

data class ManualReviewSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val suggestions: List<SuggestionItem>
)

sealed interface ManualReviewGridEntry {
    data class Header(val section: ManualReviewSection) : ManualReviewGridEntry
    data class ImageItem(val suggestion: SuggestionItem) : ManualReviewGridEntry
}

internal object ManualReviewOrganizer {
    private const val BATCH_GAP_MS = 10 * 60 * 1000L
    private const val LARGE_FILE_FALLBACK_BYTES = 2L * 1024L * 1024L

    data class Result(
        val visibleSuggestions: List<SuggestionItem>,
        val sections: List<ManualReviewSection>,
        val gridEntries: List<ManualReviewGridEntry>,
        val duplicateGroupCount: Int,
        val visualGroupCount: Int,
        val batchCount: Int,
        val largeFileCount: Int,
        val visibleDuplicateGroupCount: Int,
        val visibleVisualGroupCount: Int,
        val visibleBatchCount: Int
    )

    fun organize(
        suggestions: List<SuggestionItem>,
        query: String,
        filter: ManualReviewFilter,
        sort: ManualReviewSort,
        duplicateGroupKeys: Map<Long, String>,
        visualGroupKeys: Map<Long, String>
    ): Result {
        val duplicateGroups = buildGroupsByKey(suggestions, duplicateGroupKeys)
        val visualGroups = buildVisualGroups(suggestions, visualGroupKeys)
        val batchGroups = buildBatchGroups(suggestions)
        val largeFileThreshold = resolveLargeFileThreshold(suggestions)

        val queryFiltered = suggestions.filter { suggestion ->
            query.isBlank() || suggestion.image.displayName.contains(query, ignoreCase = true)
        }

        val filtered = when (filter) {
            ManualReviewFilter.ALL -> queryFiltered
            ManualReviewFilter.DUPLICATES -> queryFiltered.filter { suggestion ->
                (duplicateGroups[duplicateGroupKeys[suggestion.image.id]]?.size ?: 0) > 1
            }
            ManualReviewFilter.VISUAL_GROUPS -> queryFiltered.filter { suggestion ->
                (visualGroups[visualGroupKeys[suggestion.image.id]]?.size ?: 0) > 1
            }
            ManualReviewFilter.LARGE_FILES -> queryFiltered.filter { suggestion ->
                suggestion.image.sizeBytes >= largeFileThreshold
            }
        }

        if (filtered.isEmpty()) {
            return Result(
                visibleSuggestions = emptyList(),
                sections = emptyList(),
                gridEntries = emptyList(),
                duplicateGroupCount = duplicateGroups.values.count { it.size > 1 },
                visualGroupCount = visualGroups.values.count { it.size > 1 },
                batchCount = batchGroups.count { it.size > 1 },
                largeFileCount = suggestions.count { it.image.sizeBytes >= largeFileThreshold },
                visibleDuplicateGroupCount = 0,
                visibleVisualGroupCount = 0,
                visibleBatchCount = 0
            )
        }

        val filteredDuplicateGroups = buildGroupsByKey(filtered, duplicateGroupKeys)
        val filteredVisualGroups = buildVisualGroups(filtered, visualGroupKeys)
        val filteredBatchGroups = buildBatchGroups(filtered)

        val sections = when (sort) {
            ManualReviewSort.DUPLICATES -> buildGroupedSections(
                titlePrefix = "Duplicate Set",
                emptyTitle = "Unique images",
                suggestions = filtered,
                groupKeys = duplicateGroupKeys
            )
            ManualReviewSort.VISUAL_GROUPS -> buildVisualSections(filtered, visualGroupKeys)
            ManualReviewSort.BATCHES -> buildBatchSections(filtered)
            ManualReviewSort.NEWEST -> listOf(
                singleSection("newest", "Newest first", filtered.sortedByDescending { it.image.lastModified })
            )
            ManualReviewSort.NAME -> listOf(
                singleSection("name", "Alphabetical", filtered.sortedBy { it.image.displayName.lowercase(Locale.ROOT) })
            )
            ManualReviewSort.LARGEST -> listOf(
                singleSection("largest", "Largest files first", filtered.sortedByDescending { it.image.sizeBytes })
            )
        }

        val gridEntries = sections.flatMap { section ->
            buildList {
                add(ManualReviewGridEntry.Header(section))
                section.suggestions.forEach { add(ManualReviewGridEntry.ImageItem(it)) }
            }
        }

        return Result(
            visibleSuggestions = sections.flatMap { it.suggestions },
            sections = sections,
            gridEntries = gridEntries,
            duplicateGroupCount = duplicateGroups.values.count { it.size > 1 },
            visualGroupCount = visualGroups.values.count { it.size > 1 },
            batchCount = batchGroups.count { it.size > 1 },
            largeFileCount = suggestions.count { it.image.sizeBytes >= largeFileThreshold },
            visibleDuplicateGroupCount = filteredDuplicateGroups.values.count { it.size > 1 },
            visibleVisualGroupCount = filteredVisualGroups.values.count { it.size > 1 },
            visibleBatchCount = filteredBatchGroups.count { it.size > 1 }
        )
    }

    fun selectBestInDuplicateGroups(
        suggestions: List<SuggestionItem>,
        duplicateGroupKeys: Map<Long, String>
    ): Set<Long> {
        return buildGroupsByKey(suggestions, duplicateGroupKeys)
            .values
            .filter { it.size > 1 }
            .mapNotNull { chooseBestCandidate(it)?.image?.id }
            .toSet()
    }

    fun selectBestInVisualGroups(
        suggestions: List<SuggestionItem>,
        visualGroupKeys: Map<Long, String>
    ): Set<Long> {
        return buildVisualGroups(suggestions, visualGroupKeys)
            .values
            .filter { it.size > 1 }
            .mapNotNull { chooseBestCandidate(it)?.image?.id }
            .toSet()
    }

    private fun singleSection(key: String, title: String, suggestions: List<SuggestionItem>): ManualReviewSection {
        return ManualReviewSection(
            key = key,
            title = title,
            subtitle = "${suggestions.size} image(s)",
            suggestions = suggestions
        )
    }

    private fun buildVisualSections(
        suggestions: List<SuggestionItem>,
        visualGroupKeys: Map<Long, String>
    ): List<ManualReviewSection> = buildGroupedSections(
        titlePrefix = "Visual Group",
        emptyTitle = "Ungrouped images",
        suggestions = suggestions,
        groupKeys = visualGroupKeys
    )

    private fun buildBatchSections(suggestions: List<SuggestionItem>): List<ManualReviewSection> {
        val groups = buildBatchGroups(suggestions)
        val sections = mutableListOf<ManualReviewSection>()
        val singles = mutableListOf<SuggestionItem>()
        groups.forEachIndexed { index, group ->
            if (group.size > 1) {
                sections.add(
                    ManualReviewSection(
                        key = "batch-$index",
                        title = "Batch ${index + 1}",
                        subtitle = "${group.size} image(s) - ${formatBatchSpan(group)}",
                        suggestions = group.sortedByDescending { it.image.lastModified }
                    )
                )
            } else {
                singles += group
            }
        }
        if (singles.isNotEmpty()) {
            sections.add(
                ManualReviewSection(
                    key = "batch-singles",
                    title = "Single images",
                    subtitle = "${singles.size} image(s)",
                    suggestions = singles.sortedByDescending { it.image.lastModified }
                )
            )
        }
        return sections
    }

    private fun buildVisualGroups(
        suggestions: List<SuggestionItem>,
        visualGroupKeys: Map<Long, String>
    ): Map<String, List<SuggestionItem>> = buildGroupsByKey(suggestions, visualGroupKeys)

    private fun buildGroupsByKey(
        suggestions: List<SuggestionItem>,
        groupKeys: Map<Long, String>
    ): Map<String, List<SuggestionItem>> {
        return suggestions
            .mapNotNull { suggestion ->
                groupKeys[suggestion.image.id]?.let { key -> key to suggestion }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )
    }

    private fun buildGroupedSections(
        titlePrefix: String,
        emptyTitle: String,
        suggestions: List<SuggestionItem>,
        groupKeys: Map<Long, String>
    ): List<ManualReviewSection> {
        val groups = buildGroupsByKey(suggestions, groupKeys)
        val sections = groups.entries
            .sortedByDescending { it.value.size }
            .mapIndexed { index, entry ->
                ManualReviewSection(
                    key = entry.key,
                    title = "$titlePrefix ${index + 1}",
                    subtitle = "${entry.value.size} image(s)",
                    suggestions = entry.value.sortedWith(bestCandidateComparator().reversed())
                )
            }
            .toMutableList()

        val groupedIds = groups.values.flatten().mapTo(hashSetOf()) { it.image.id }
        val singles = suggestions
            .filter { it.image.id !in groupedIds }
            .sortedByDescending { it.image.lastModified }
        if (singles.isNotEmpty()) {
            sections.add(
                ManualReviewSection(
                    key = "${titlePrefix.lowercase(Locale.ROOT)}-singles",
                    title = emptyTitle,
                    subtitle = "${singles.size} image(s)",
                    suggestions = singles
                )
            )
        }

        return sections
    }

    private fun buildBatchGroups(suggestions: List<SuggestionItem>): List<List<SuggestionItem>> {
        if (suggestions.isEmpty()) return emptyList()
        val sorted = suggestions.sortedByDescending { it.image.lastModified }
        val groups = mutableListOf<MutableList<SuggestionItem>>()
        sorted.forEach { suggestion ->
            val current = groups.lastOrNull()
            val previous = current?.lastOrNull()
            if (previous == null || (previous.image.lastModified - suggestion.image.lastModified) > BATCH_GAP_MS) {
                groups += mutableListOf(suggestion)
            } else {
                current += suggestion
            }
        }
        return groups
    }

    private fun resolveLargeFileThreshold(suggestions: List<SuggestionItem>): Long {
        if (suggestions.isEmpty()) return LARGE_FILE_FALLBACK_BYTES
        val sorted = suggestions.map { it.image.sizeBytes }.sortedDescending()
        val percentileIndex = max(0, min(sorted.lastIndex, sorted.size / 4))
        return max(sorted[percentileIndex], LARGE_FILE_FALLBACK_BYTES)
    }

    private fun formatBatchSpan(group: List<SuggestionItem>): String {
        val newest = group.maxOfOrNull { it.image.lastModified } ?: 0L
        val oldest = group.minOfOrNull { it.image.lastModified } ?: newest
        val minutes = ((newest - oldest) / 60000L).toInt()
        return if (minutes <= 0) "same minute" else "$minutes min span"
    }

    private fun chooseBestCandidate(suggestions: List<SuggestionItem>): SuggestionItem? {
        return suggestions.maxWithOrNull(bestCandidateComparator())
    }

    private fun bestCandidateComparator() = compareBy<SuggestionItem> { it.image.sizeBytes }
        .thenBy { preferredFormatScore(it.image.displayName) }
        .thenBy { displayNameQualityScore(it.image.displayName) }
        .thenBy { it.image.lastModified }
        .thenBy { it.image.displayName.lowercase(Locale.ROOT) }

    private fun preferredFormatScore(displayName: String): Int {
        return when (displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> 3
            "webp" -> 2
            "jpg", "jpeg" -> 1
            else -> 0
        }
    }

    private fun displayNameQualityScore(displayName: String): Int {
        val lowercaseName = displayName.lowercase(Locale.ROOT)
        var score = 0
        if (!Regex("\\[[^\\]]*]").containsMatchIn(lowercaseName)) score += 1
        if (!Regex("\\([^)]*\\)").containsMatchIn(lowercaseName)) score += 1
        if (!Regex("\\b\\d{3,5}x\\d{3,5}\\b").containsMatchIn(lowercaseName)) score += 1
        if (!Regex("\\b(copy|edited|edit|crop|sample)\\b").containsMatchIn(lowercaseName)) score += 2
        if (normalizeNameGroupKey(displayName).split(' ').count { it.isNotBlank() } >= 2) score += 1
        return score
    }

    internal fun normalizeNameGroupKey(displayName: String): String {
        val base = displayName.substringBeforeLast('.')
            .lowercase(Locale.ROOT)
            .replace(Regex("\\[[^\\]]*]"), " ")
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\b\\d{3,5}x\\d{3,5}\\b"), " ")
            .replace(Regex("[-_.]+"), " ")
            .replace(Regex("\\b(copy|edited|edit|crop|sample|wallpaper)\\b"), " ")
            .replace(Regex("\\b\\d+\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val tokens = base.split(' ')
            .filter { it.length >= 3 }
            .take(4)

        return tokens.joinToString(" ")
    }
}
