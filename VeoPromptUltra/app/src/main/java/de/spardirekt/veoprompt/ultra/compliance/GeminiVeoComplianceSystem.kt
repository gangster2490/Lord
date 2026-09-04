package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.FinalPromptValidator
import de.spardirekt.veoprompt.ultra.model.GeminiVeoReport
import de.spardirekt.veoprompt.ultra.model.ProductModel

/**
 * Builds the stored Gemini / VEO submission verdict + checklist.
 * Never writes into veoPrompt.
 */
object GeminiVeoComplianceSystem {

    const val VERDICT_READY = "READY"
    const val VERDICT_SANITIZED = "SANITIZED"
    const val VERDICT_BLOCKED = "BLOCKED"

    fun evaluate(
        findings: List<GeminiVeoPromptSanitizer.Finding>,
        prompt: String,
        productModel: ProductModel
    ): GeminiVeoReport {
        val gemini = findings.filter { it.code.startsWith("GV_") }
        val hardHits = gemini.filter { it.code in GeminiVeoPolicy.HARD_BLOCK_CODES }
        val unrepairedHigh = gemini.filter { it.severity == "HIGH" && !it.repaired && it.code != "GV_SUBMIT" }
        val repairedHits = gemini.filter { it.repaired && it.code != "GV_SUBMIT" }
        val verdict = when {
            hardHits.isNotEmpty() -> VERDICT_BLOCKED
            unrepairedHigh.isNotEmpty() -> VERDICT_BLOCKED
            repairedHits.isNotEmpty() -> VERDICT_SANITIZED
            else -> VERDICT_READY
        }
        val failed = gemini.filter {
            it.code != "GV_SUBMIT" &&
                (!it.repaired || it.code in GeminiVeoPolicy.HARD_BLOCK_CODES)
        }.map { it.code }.toSet()
        val lock = FinalPromptValidator.sectionBody(prompt, "PRODUCT LOCK")
        val productMatch = productModel.visualSignature.isEmpty() ||
            FinalPromptValidator.isProductSpecificLock(lock, productModel)

        val checklist = listOf(
            item("GV_SUBMIT", "Пакет готовится для вставки в Gemini / VEO", true),
            item("GV_NO_MINORS_UNSAFE", "Нет несовершеннолетних в опасном/сексуальном контексте", "GV_NO_MINORS_UNSAFE" !in failed),
            item("GV_NO_SEXUAL", "Нет сексуального контента", "GV_NO_SEXUAL" !in failed),
            item("GV_NO_NUDITY", "Нет наготы", "GV_NO_NUDITY" !in failed),
            item("GV_NO_REAL_PERSON", "Нет лиц знаменитостей и реальных людей", "GV_NO_REAL_PERSON" !in failed),
            item("GV_NO_VIOLENCE", "Нет крови и жестокости", "GV_NO_VIOLENCE" !in failed),
            item("GV_NO_WEAPONS", "Нет оружия и взрывчатки", "GV_NO_WEAPONS" !in failed),
            item("GV_NO_SELF_HARM", "Нет самоповреждения", "GV_NO_SELF_HARM" !in failed),
            item("GV_NO_HATE", "Нет ненависти", "GV_NO_HATE" !in failed),
            item("GV_NO_DRUGS", "Нет наркотиков", "GV_NO_DRUGS" !in failed),
            item("GV_NO_COPYRIGHT_CHAR", "Нет чужих персонажей", "GV_NO_COPYRIGHT_CHAR" !in failed),
            item("GV_NO_CHILD_TALENT", "Нет детей в кадре", "GV_NO_CHILD_TALENT" !in failed),
            item("GV_PRODUCT_LOCK", "PRODUCT LOCK сохранён", productMatch)
        )

        val steps = buildList {
            add("Скопируйте полный veoPrompt в Gemini / VEO. Не вставляйте урезанный текст.")
            add("Не добавляйте знаменитостей, реальных людей или детей в промпт.")
            add("Не добавляйте секс, наготу, кровь, оружие или наркотики.")
            if (verdict == VERDICT_SANITIZED) {
                add("Пакет уже прошёл локальную санацию. Проверьте PRODUCT LOCK перед вставкой.")
            }
            if (verdict == VERDICT_BLOCKED) {
                add(
                    "Не вставляйте в Gemini / VEO, пока не убраны hard-rule нарушения: " +
                        hardHits.plus(unrepairedHigh).distinctBy { it.code }.joinToString { it.code } + "."
                )
            }
        }

        return GeminiVeoReport(
            policyVersion = GeminiVeoPolicy.VERSION,
            verdict = verdict,
            submissionSafe = verdict != VERDICT_BLOCKED,
            findings = gemini.map {
                val mark = if (it.repaired) "исправлено" else it.severity
                "${it.code} · $mark · ${it.message}"
            },
            checklist = checklist,
            publishSteps = steps
        )
    }

    private fun item(code: String, label: String, ok: Boolean): String {
        val mark = if (ok) "OK" else "FAIL"
        return "$mark · $code · $label"
    }
}
