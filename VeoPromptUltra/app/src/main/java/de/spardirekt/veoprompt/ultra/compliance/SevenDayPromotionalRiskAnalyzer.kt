package de.spardirekt.veoprompt.ultra.compliance

/**
 * 7-day TikTok Shop promotional-content risk analyzer.
 *
 * Local rolling window over **Ready** packages. Counts only unrepaired HIGH/MEDIUM
 * promo findings. Failed jobs, INFO, and cleaned (“исправлено”) lines do not
 * inflate live Shop risk. Not official CHR/AHR and not written into veoPrompt.
 *
 * Restriction-day projection follows the public milestone shape
 * (3 / 7 / 14 day shoppable limits) as an owner warning only.
 */
object SevenDayPromotionalRiskAnalyzer {

    const val VERSION = "2026.06-v2"
    const val TITLE = "7-Day TikTok Shop Promotional Content Risk Analyzer"
    const val WINDOW_DAYS = 7
    const val WINDOW_MS = WINDOW_DAYS * 24L * 60L * 60L * 1000L

    val PROMO_CODES = setOf(
        "CL_SUPERLATIVE", "CL_MEDICAL", "CL_UNSUPPORTED",
        "PR_PRICE_UI", "PR_URGENCY", "PR_SYMPATHY", "PR_FORCED_CTA",
        "PR_OFF_PLATFORM", "PR_POLITICAL", "PR_HARMFUL",
        "AIGC_NO_DECEIVE", "AIGC_NO_UNREALISTIC", "AIGC_NO_FALSE_ENDORSE",
        "AIGC_NO_IMPERSONATE", "AIGC_NO_FEAR"
    )

    data class Snapshot(
        val id: String,
        val updatedAt: Long,
        val status: String,
        val hasPackage: Boolean,
        val safetyRisk: String = "LOW",
        val auditItems: List<String> = emptyList(),
        val aigcVerdict: String = "",
        val aigcShopPublishSafe: Boolean = true
    )

    data class Report(
        val windowDays: Int = WINDOW_DAYS,
        val policyVersion: String = VERSION,
        val analyzedCount: Int = 0,
        val highHits: Int = 0,
        val mediumHits: Int = 0,
        val blockedPackages: Int = 0,
        val repeatCodes: List<String> = emptyList(),
        val score: Int = 0,
        val riskLevel: String = "LOW",
        val projectedRestrictionDays: Int = 0,
        val items: List<String> = emptyList(),
        val recommendation: String = ""
    )

    fun analyze(snapshots: List<Snapshot>, nowMs: Long): Report {
        val cutoff = nowMs - WINDOW_MS
        val scoped = snapshots.filter { snap ->
            snap.hasPackage &&
                snap.updatedAt in cutoff..nowMs &&
                snap.status.equals("Ready", true)
        }

        val codeHits = mutableMapOf<String, Int>()
        var highHits = 0
        var mediumHits = 0
        var blocked = 0
        scoped.forEach { snap ->
            if (snap.aigcVerdict.equals("BLOCKED", true)) {
                blocked += 1
            }
            extractCodes(snap.auditItems).forEach { (code, severity) ->
                if (code !in PROMO_CODES) return@forEach
                codeHits[code] = (codeHits[code] ?: 0) + 1
                when (severity) {
                    "HIGH" -> highHits += 1
                    "MEDIUM" -> mediumHits += 1
                }
            }
        }

        val repeats = codeHits.filter { it.value >= 2 }.keys.sorted()
        var score = highHits * 12 + mediumHits * 6 + blocked * 20 + repeats.size * 15
        score = score.coerceIn(0, 100)

        val restriction = when {
            score >= 70 || repeats.size >= 3 -> 14
            score >= 40 || repeats.isNotEmpty() || highHits >= 3 -> 7
            score >= 20 || highHits >= 1 || blocked >= 1 -> 3
            else -> 0
        }
        val level = when {
            restriction >= 14 || score >= 70 -> "CRITICAL"
            restriction >= 7 || score >= 40 -> "HIGH"
            restriction >= 3 || score >= 20 -> "MEDIUM"
            else -> "LOW"
        }

        val items = buildList {
            add("Окно: $WINDOW_DAYS дней · пакетов: ${scoped.size}")
            if (highHits > 0) add("HIGH промо-нарушения: $highHits")
            if (mediumHits > 0) add("MEDIUM промо-нарушения: $mediumHits")
            if (blocked > 0) add("AIGC BLOCKED пакеты: $blocked")
            repeats.forEach { add("Повтор за 7 дней: $it ×${codeHits[it]}") }
        }
        val recommendation = when (level) {
            "CRITICAL" -> "Снизьте плотность промо-нарушений. Повтор тех же формулировок повышает риск 14-дневного ограничения shoppable-контента."
            "HIGH" -> "Повторяющиеся промо-нарушения за 7 дней. Публикуйте только пакеты без urgency/price/medical/AIGC hard-rule hits — иначе риск 7-дневного ограничения."
            "MEDIUM" -> "Есть промо-риск в окне 7 дней. Уберите urgency, цены и недоказуемые эффекты перед публикацией."
            else -> "Промо-риск за 7 дней низкий. Перед публикацией всё равно включите TikTok «AI-generated content»."
        }

        return Report(
            analyzedCount = scoped.size,
            highHits = highHits,
            mediumHits = mediumHits,
            blockedPackages = blocked,
            repeatCodes = repeats,
            score = score,
            riskLevel = level,
            projectedRestrictionDays = restriction,
            items = items,
            recommendation = recommendation
        )
    }

    fun extractCodes(items: List<String>): List<Pair<String, String>> {
        return items.mapNotNull { raw ->
            val parts = raw.split(" · ").map { it.trim() }
            val code = parts.firstOrNull().orEmpty()
            if (code.isBlank()) return@mapNotNull null
            val mark = parts.getOrNull(1).orEmpty()
            if (mark.contains("исправ", ignoreCase = true)) return@mapNotNull null
            val severity = when (mark.uppercase()) {
                "HIGH", "MEDIUM" -> mark.uppercase()
                else -> return@mapNotNull null
            }
            code to severity
        }
    }
}
