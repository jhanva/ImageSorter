package com.smartfolder.presentation.screens.results

import com.smartfolder.domain.model.SuggestionItem
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ManualReviewFilter(val label: String) {
    ALL("All"),
    NAME_GROUPS("Name Groups"),
    LARGE_FILES("Large Files")
}

enum class ManualReviewSort(val label: String) {
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
        val nameGroupCount: Int,
        val batchCount: Int,
        val largeFileCount: Int,
        val visibleNameGroupCount: Int,
        val visibleBatchCount: Int
    )

    fun organize(
        suggestions: List<SuggestionItem>,
        query: String,
        filter: ManualReviewFilter,
        sort: ManualReviewSort
    ): Result {
        val nameGroups = buildNameGroups(suggestions)
        val batchGroups = buildBatchGroups(suggestions)
        val largeFileThreshold = resolveLargeFileThreshold(suggestions)

        val queryFiltered = suggestions.filter { suggestion ->
            query.isBlank() || suggestion.image.displayName.contains(query, ignoreCase = true)
        }

        val filtered = when (filter) {
            ManualReviewFilter.ALL -> queryFiltered
            ManualReviewFilter.NAME_GROUPS -> queryFiltered.filter { suggestion ->
                val key = normalizeNameGroupKey(suggestion.image.displayName)
                key.isNotBlank() && (nameGroups[key]?.size ?: 0) > 1
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
                nameGroupCount = nameGroups.values.count { it.size > 1 },
                batchCount = batchGroups.count { it.size > 1 },
                largeFileCount = suggestions.count { it.image.sizeBytes >= largeFileThreshold },
                visibleNameGroupCount = 0,
                visibleBatchCount = 0
            )
        }

        val filteredNameGroups = buildNameGroups(filtered)
        val filteredBatchGroups = buildBatchGroups(filtered)

        val sections = when (sort) {
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
            nameGroupCount = nameGroups.values.count { it.size > 1 },
            batchCount = batchGroups.count { it.size > 1 },
            largeFileCount = suggestions.count { it.image.sizeBytes >= largeFileThreshold },
            visibleNameGroupCount = filteredNameGroups.values.count { it.size > 1 },
            visibleBatchCount = filteredBatchGroups.count { it.size > 1 }
        )
    }

    fun selectBestInNameGroups(suggestions: List<SuggestionItem>): Set<Long> {
        return buildNameGroups(suggestions)
            .values
            .filter { it.size > 1 }
            .mapNotNull { chooseBestCandidate(it)?.image?.id }
            .toSet()
    }

    fun selectBatchLeads(suggestions: List<SuggestionItem>): Set<Long> {
        return buildBatchGroups(suggestions)
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

    private fun buildNameGroups(suggestions: List<SuggestionItem>): Map<String, List<SuggestionItem>> {
        return suggestions.groupBy { normalizeNameGroupKey(it.image.displayName) }
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
        return suggestions.maxWithOrNull(
            compareBy<SuggestionItem> { it.image.sizeBytes }
                .thenBy { it.image.lastModified }
                .thenBy { it.image.displayName.lowercase(Locale.ROOT) }
        )
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
