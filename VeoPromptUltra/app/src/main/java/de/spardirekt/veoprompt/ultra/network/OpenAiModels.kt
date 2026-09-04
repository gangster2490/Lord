package de.spardirekt.veoprompt.ultra.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val error: ApiErrorBody? = null
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ChoiceMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChoiceMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo> = emptyList()
)

@Serializable
data class ModelInfo(
    val id: String = ""
)
