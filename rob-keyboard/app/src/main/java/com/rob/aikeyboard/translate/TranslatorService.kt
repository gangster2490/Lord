package com.rob.aikeyboard.translate

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.rob.aikeyboard.text.Language
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device translation via ML Kit. Each language model (~30 MB) is downloaded once on request;
 * afterwards translation works fully offline and no text leaves the phone.
 */
class TranslatorService(@Suppress("unused") private val context: Context) {
    private val models = RemoteModelManager.getInstance()

    private fun mlKit(language: Language) = when (language) {
        Language.RU -> TranslateLanguage.RUSSIAN
        Language.DE -> TranslateLanguage.GERMAN
        Language.EN -> TranslateLanguage.ENGLISH
    }

    suspend fun downloadedLanguages(): Set<Language> = suspendCancellableCoroutine { cont ->
        models.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { set ->
                val codes = set.map { it.language }.toSet()
                cont.resume(Language.entries.filter { mlKit(it) in codes }.toSet())
            }
            .addOnFailureListener { cont.resume(emptySet()) }
    }

    /** English is ML Kit's pivot language and is always required. */
    fun requiredModels(source: Language, target: Language): Set<Language> = setOf(source, target, Language.EN)

    suspend fun download(language: Language, wifiOnly: Boolean) = suspendCancellableCoroutine { cont ->
        val conditions = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        models.download(TranslateRemoteModel.Builder(mlKit(language)).build(), conditions)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    suspend fun delete(language: Language) = suspendCancellableCoroutine { cont ->
        models.deleteDownloadedModel(TranslateRemoteModel.Builder(mlKit(language)).build())
            .addOnCompleteListener { cont.resume(Unit) }
    }

    fun deleteAllModels() {
        Language.entries.forEach {
            models.deleteDownloadedModel(TranslateRemoteModel.Builder(mlKit(it)).build())
        }
    }

    /** Translates offline. Fails if a needed model has not been downloaded yet. */
    suspend fun translate(text: String, source: Language, target: Language): String {
        if (source == target) return text
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(mlKit(source)).setTargetLanguage(mlKit(target)).build(),
        )
        try {
            return suspendCancellableCoroutine { cont ->
                translator.translate(text)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            translator.close()
        }
    }
}
