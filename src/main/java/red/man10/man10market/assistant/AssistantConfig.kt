package red.man10.man10market.assistant

import com.aallam.openai.api.http.Timeout
import kotlin.time.Duration.Companion.seconds

/**
 * AIアシスタントの設定
 */
data class AssistantConfig(
    val apiKey: String,
    val model: String = "gpt-4o",
    val temperature: Double = 0.7,
    val maxTokens: Int = 50000
)
