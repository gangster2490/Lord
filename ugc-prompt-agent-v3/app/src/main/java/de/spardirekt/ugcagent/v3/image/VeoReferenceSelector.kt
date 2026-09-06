package de.spardirekt.ugcagent.v3.image

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

object VeoReferenceSelector {
    const val MAX_REFS = 5

    data class Candidate(
        val id: String,
        val index: Int,
        val width: Int,
        val height: Int,
        val compressedBytes: Long,
        val filename: String = "",
        val tags: List<String> = emptyList(),
        val reasons: String = "",
        val marketplaceUiOverProduct: Boolean = false,
        val identityComponentsVisible: Boolean = true,
    )

    data class Pick(
        val id: String,
        val role: String,
        val label: String,
    )

    fun select(
        images: List<Candidate>,
        firstFrameId: String? = null,
        consistency: JSONObject? = null,
    ): List<Pick> {
        if (images.isEmpty()) return emptyList()
        val conflicting = intSet(consistency?.optJSONArray("conflicting_image_indices"))
        val dominant = intSet(consistency?.optJSONArray("dominant_product_indices"))
        val duplicateGroups = parseDuplicateGroups(consistency?.optJSONArray("duplicate_groups"))
        val medianMin = images.map { min(it.width, it.height) }.sorted().let { sides ->
            sides[sides.size / 2].toDouble().coerceAtLeast(1.0)
        }
        val hasCleanPhoto = images.any { candidate ->
            !isTextOnly(candidate) &&
                !isInstructionPage(candidate) &&
                !isPackagingOnly(candidate) &&
                !isHeavilyCropped(candidate, medianMin) &&
                candidate.index !in conflicting
        }

        fun eligible(strict: Boolean): List<Candidate> {
            val filtered = images.filter { candidate ->
                if (isTextOnly(candidate) || isInstructionPage(candidate)) return@filter false
                if (candidate.index in conflicting) return@filter false
                if (strict && hasCleanPhoto && isPackagingOnly(candidate)) return@filter false
                if (strict && isHeavilyCropped(candidate, medianMin)) return@filter false
                true
            }
            return dropDuplicates(filtered, duplicateGroups, firstFrameId)
        }

        var pool = eligible(strict = true)
        if (pool.size < minOf(3, images.size)) pool = eligible(strict = false)
        if (pool.isEmpty()) pool = dropDuplicates(images, duplicateGroups, firstFrameId).ifEmpty { images }

        val preferredPool = if (dominant.isNotEmpty()) {
            pool.filter { it.index in dominant }.ifEmpty { pool }
        } else {
            pool
        }
        val first = preferredPool.firstOrNull { it.id == firstFrameId }
            ?: preferredPool.maxByOrNull { firstFrameScore(it) }
            ?: pool.maxByOrNull { firstFrameScore(it) }
            ?: images.first()

        val supports = pool
            .filter { it.id != first.id }
            .sortedByDescending { supportScore(it, first) }
            .take(MAX_REFS - 1)

        return listOf(Pick(first.id, "FIRST_FRAME", "FIRST FRAME")) +
            supports.mapIndexed { index, candidate ->
                Pick(candidate.id, "SUPPORT_${index + 1}", "SUPPORT ${index + 1}")
            }
    }

    fun firstFrameScore(candidate: Candidate): Double =
        FirstFrameHeuristics.score(candidate.width, candidate.height, candidate.compressedBytes)

    private fun supportScore(candidate: Candidate, first: Candidate): Double {
        val base = firstFrameScore(candidate)
        val sameAspect = aspectBucket(candidate) == aspectBucket(first)
        val diversity = if (sameAspect) 0.0 else 1.5
        val closeUpBonus = if (isUsefulIdentityCloseUp(candidate, first)) 8.0 else 0.0
        return base + diversity + closeUpBonus
    }

    private fun isUsefulIdentityCloseUp(candidate: Candidate, first: Candidate): Boolean {
        val minSide = min(candidate.width, candidate.height)
        val firstMin = min(first.width, first.height)
        return candidate.compressedBytes >= 80_000L &&
            minSide < (firstMin * 0.55).toInt() &&
            candidate.identityComponentsVisible &&
            !isTextOnly(candidate)
    }

    private fun isTextOnly(candidate: Candidate): Boolean {
        if (!candidate.identityComponentsVisible) return true
        if (candidate.marketplaceUiOverProduct) return true
        if (FirstFrameHeuristics.looksLikeScreenshot(candidate.width, candidate.height, candidate.compressedBytes)) return true
        val blob = blob(candidate)
        return blob.contains("text-only") ||
            blob.contains("description page") ||
            blob.contains("size card") ||
            blob.contains("infographic")
    }

    private fun isInstructionPage(candidate: Candidate): Boolean {
        val blob = blob(candidate)
        return blob.contains("instruction") ||
            blob.contains("manual") ||
            blob.contains("safety page") ||
            blob.contains("how to use")
    }

    private fun isPackagingOnly(candidate: Candidate): Boolean {
        val blob = blob(candidate)
        val packaging = blob.contains("packaging") ||
            blob.contains("package") ||
            Regex("\\b(pack|box|carton)\\b").containsMatchIn(blob)
        val unpacked = blob.contains("unbox") || blob.contains("unpacked") || blob.contains("product photo")
        return packaging && !unpacked
    }

    private fun isHeavilyCropped(candidate: Candidate, medianMin: Double): Boolean {
        val minSide = min(candidate.width, candidate.height).toDouble()
        val maxSide = max(candidate.width, candidate.height).toDouble().coerceAtLeast(1.0)
        val extreme = minSide / maxSide < 0.42
        val small = minSide < 0.45 * medianMin
        if (!extreme && !small) return false
        val usefulCloseUp = candidate.compressedBytes >= 80_000L && minSide >= 220 && candidate.identityComponentsVisible
        return !usefulCloseUp
    }

    private fun dropDuplicates(
        images: List<Candidate>,
        groups: List<Set<Int>>,
        keepId: String?,
    ): List<Candidate> {
        val ranked = images.sortedWith(
            compareByDescending<Candidate> { it.id == keepId }.thenByDescending { firstFrameScore(it) },
        )
        val byBucket = linkedMapOf<String, Candidate>()
        ranked.forEach { candidate ->
            val key = duplicateBucket(candidate)
            if (!byBucket.containsKey(key)) byBucket[key] = candidate
        }
        val unique = byBucket.values.toList()
        if (groups.isEmpty()) return unique
        val chosen = mutableListOf<Candidate>()
        val used = mutableSetOf<Int>()
        unique.sortedWith(
            compareByDescending<Candidate> { it.id == keepId }.thenByDescending { firstFrameScore(it) },
        ).forEach { candidate ->
            val group = groups.firstOrNull { candidate.index in it }
            if (group != null) {
                if (group.any { it in used }) return@forEach
                group.forEach { used += it }
            }
            chosen += candidate
            used += candidate.index
        }
        return chosen
    }

    fun duplicateBucket(candidate: Candidate): String =
        "${candidate.width}x${candidate.height}:${candidate.compressedBytes / 8_000L}"

    private fun aspectBucket(candidate: Candidate): Int {
        val w = candidate.width.coerceAtLeast(1).toDouble()
        val h = candidate.height.coerceAtLeast(1).toDouble()
        return ((max(w, h) / min(w, h)) * 10).toInt()
    }

    private fun blob(candidate: Candidate): String =
        (candidate.filename + " " + candidate.tags.joinToString(" ") + " " + candidate.reasons).lowercase()

    private fun intSet(arr: JSONArray?): Set<Int> {
        if (arr == null) return emptySet()
        return (0 until arr.length()).mapNotNull { idx ->
            if (arr.isNull(idx)) null else arr.optInt(idx, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        }.toSet()
    }

    private fun parseDuplicateGroups(arr: JSONArray?): List<Set<Int>> {
        if (arr == null) return emptyList()
        val groups = mutableListOf<Set<Int>>()
        for (i in 0 until arr.length()) {
            val group = arr.optJSONArray(i) ?: continue
            val indices = intSet(group)
            if (indices.size >= 2) groups += indices
        }
        return groups
    }
}
