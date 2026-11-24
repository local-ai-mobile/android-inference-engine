package io.github.localai.inference.api

data class InferenceChatMessage(
    val role: ChatRole,
    val text: String,
    val extractedContext: String = "",
    val attachmentUris: List<String> = emptyList()
) {
    val isUser: Boolean get() = role == ChatRole.USER
    val isAssistant: Boolean get() = role == ChatRole.ASSISTANT
    val isSystem: Boolean get() = role == ChatRole.SYSTEM
    val isTool: Boolean get() = role == ChatRole.TOOL
}
