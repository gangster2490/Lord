package de.tiktokshop.buchhaltung.ai

import android.net.Uri
import de.tiktokshop.buchhaltung.scan.TransactionCandidate

/**
 * Austauschbarer Fallback, wenn die lokale ML-Kit-Erkennung unsicher ist oder zu wenige
 * Transaktionen gefunden hat. Implementierungen rufen NIE direkt einen AI-Anbieter (OpenAI/
 * Gemini/Claude) auf - immer über den eigenen Backend-Proxy, damit kein API-Key im APK
 * landet (siehe [BackendAiVisionProvider]).
 */
interface AiVisionProvider {
    /**
     * Analysiert einen Screenshot/Beleg. Liefert [Result.success] mit einer (ggf. leeren)
     * Liste an [TransactionCandidate]s, oder [Result.failure], wenn der Dienst nicht
     * erreichbar/konfiguriert ist - niemals erfundene Werte.
     */
    suspend fun analyzeReceipt(imageUri: Uri): Result<List<TransactionCandidate>>
}

/** No-Op-Implementierung, solange kein Backend konfiguriert ist - fällt einfach auf OCR-only zurück. */
object DisabledAiVisionProvider : AiVisionProvider {
    override suspend fun analyzeReceipt(imageUri: Uri): Result<List<TransactionCandidate>> =
        Result.failure(IllegalStateException("Kein AI-Vision-Backend konfiguriert."))
}
