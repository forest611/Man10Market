package red.man10.man10market.assistant

/**
 * AIアシスタントの設定
 */
data class AssistantConfig(
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val temperature: Double = 0.7,
    val maxTokens: Long = 32768
)
