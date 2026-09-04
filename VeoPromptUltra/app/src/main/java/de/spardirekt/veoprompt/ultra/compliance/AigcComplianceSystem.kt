package de.spardirekt.veoprompt.ultra.compliance

import de.spardirekt.veoprompt.ultra.generation.FinalPromptValidator
import de.spardirekt.veoprompt.ultra.model.AigcReport
import de.spardirekt.veoprompt.ultra.model.ProductModel

/**
 * TikTok Shop AIGC compliance system.
 * Builds a stored publish verdict + checklist. Never writes into veoPrompt.
 */
object AigcComplianceSystem {

    const val VERDICT_DISCLOSE = "DISCLOSE_REQUIRED"
    const val VERDICT_BLOCKED = "BLOCKED"

    data class InputFinding(
        val code: String,
        val severity: String,
        val message: String
    )

    fun evaluate(
        findings: List<InputFinding>,
        prompt: String,
        productModel: ProductModel
    ): AigcReport {
        val aigc = findings.filter { it.code.startsWith("AIGC_") }
        val hardHits = aigc.filter { it.code != "AIGC_DISCLOSE" && it.severity == "HIGH" }
        val verdict = if (hardHits.isEmpty()) VERDICT_DISCLOSE else VERDICT_BLOCKED
        val failed = aigc.map { it.code }.toSet()
        val lock = FinalPromptValidator.sectionBody(prompt, "PRODUCT LOCK")
        val productMatch = productModel.visualSignature.isEmpty() ||
            FinalPromptValidator.isProductSpecificLock(lock, productModel)

        val checklist = listOf(
            item("AIGC_DISCLOSE", "Пометить ролик как AI-generated", true),
            item("AIGC_NO_DECEIVE", "Не выдавать VEO за живую съёмку", "AIGC_NO_DECEIVE" !in failed),
            item("AIGC_NO_IMPERSONATE", "Нет имперсонации и клонов голоса", "AIGC_NO_IMPERSONATE" !in failed),
            item("AIGC_NO_FALSE_ENDORSE", "Нет фальшивых экспертов", "AIGC_NO_FALSE_ENDORSE" !in failed),
            item("AIGC_NO_PRODUCT_ALTER", "Товар не изменён", "AIGC_NO_PRODUCT_ALTER" !in failed),
            item("AIGC_NO_UNREALISTIC", "Нет мгновенных чудо-результатов", "AIGC_NO_UNREALISTIC" !in failed),
            item("AIGC_NO_FAKE_FEATURES", "Нет выдуманных визуальных фич", "AIGC_NO_FAKE_FEATURES" !in failed),
            item("AIGC_NO_FEAR", "Нет страх-визуала", "AIGC_NO_FEAR" !in failed),
            item("AIGC_NO_IP", "Нет чужого IP", "AIGC_NO_IP" !in failed),
            item("AIGC_PRODUCT_MATCH", "Товар совпадает с фото", productMatch && "AIGC_PRODUCT_MATCH" !in failed)
        )

        val steps = buildList {
            add("Скопируйте полный veoPrompt в Gemini / VEO. Не публикуйте урезанный текст.")
            add("После генерации видео включите в TikTok переключатель «AI-generated content».")
            add("Не пишите, что ролик снят вживую или «не AI».")
            add("Товар в кадре должен совпадать с фотографиями листинга.")
            if (verdict == VERDICT_BLOCKED) {
                add("Не публикуйте в TikTok Shop, пока не убраны AIGC hard-rule нарушения: ${hardHits.joinToString { it.code }}.")
            }
        }

        return AigcReport(
            policyVersion = AigcHardRules.VERSION,
            verdict = verdict,
            disclosureRequired = true,
            shopPublishSafe = verdict != VERDICT_BLOCKED,
            findings = aigc.map { "${it.code} · ${it.severity} · ${it.message}" },
            checklist = checklist,
            publishSteps = steps
        )
    }

    private fun item(code: String, label: String, ok: Boolean): String {
        val mark = if (ok) "OK" else "FAIL"
        return "$mark · $code · $label"
    }
}
