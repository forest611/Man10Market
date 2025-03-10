package red.man10.man10market.assistant

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import red.man10.man10market.Man10Market
import red.man10.man10market.MarketData
import red.man10.man10market.Market
import red.man10.man10market.Util
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor

/**
 * Man10Market用のAIアシスタント機能を提供するクラス
 * 市場での取引のアドバイスや、価格分析などを行う
 */
class Assistant private constructor() {
    private lateinit var openAI: OpenAI
    private lateinit var config: AssistantConfig

    companion object {
        private var instance: Assistant? = null
        private lateinit var plugin: Man10Market

        fun getInstance(): Assistant {
            return instance ?: synchronized(this) {
                instance ?: Assistant().also { instance = it }
            }
        }

        fun setup(plugin: Man10Market) {
            this.plugin = plugin
        }
    }

    /**
     * OpenAI APIの設定を初期化
     */
    fun initialize(config: AssistantConfig) {
        this.config = config
        this.openAI = OpenAI(config.apiKey)
    }

    /**
     * プレイヤーからの質問に応答し、適切なコマンドを生成する
     * @param player 質問したプレイヤー
     * @param question 質問内容
     * @return 生成されたコマンド（該当する場合）
     */
    fun ask(player: Player, question: String): AssistantCommand? = runBlocking {

            val marketItem = Market.getItemIndex() // アイテムのインデックスを取得

            val allPriceStr = mutableListOf<String>()

            Market.getItemIndex().forEach {
                val price = Market.getPrice(it)
                allPriceStr.add("アイテム名:$it 価格:${price.price}円 売値:${price.ask}円 買値:${price.bid}円")
            }

            val systemPrompt = """あなたはMinecraftサーバー「Man10」の市場アシスタントです。
                プレイヤーの質問に応じて、適切な取引コマンドを生成してください。
                
                取引できるアイテムは以下の通りです。
                この名前はコマンドを生成する際のアイテム名として利用されます。
                $marketItem
                
                現在の市場価格は以下の通りです。
                $allPriceStr
                
                以下のコマンドタイプが利用可能です：
                - MARKET_BUY: 市場価格(成り行き注文)での購入
                - MARKET_SELL: 市場価格(成り行き注文)での売却
                - ORDER_BUY: 指値(価格指定)での買い注文
                - ORDER_SELL: 指値(価格指定)での売り注文
                - ORDER_CANCEL: 注文のキャンセル
                - PRICE_CHECK: 価格確認
                - MARKET_ANALYSIS: 市場全体の分析
                - TREND_ANALYSIS: 特定アイテムの価格トレンド分析
                
                応答は必ず以下のJSON形式で返してください：
                {
                    \"type\": \"コマンドタイプ\",
                    \"action\": \"アクション名\",
                    \"parameters\": {
                        \"item\": \"アイテム名\",
                        \"amount\": 数量,
                        \"price\": 価格
                    },
                    \"description\": \"実行内容の説明\"
                }""".trimIndent()

        try {
            val completion = openAI.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(config.model),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = systemPrompt
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = question
                        )
                    )
                )
            )

            val content = completion.choices.firstOrNull()?.message?.content
            if (content.isNullOrBlank()) {
                Util.msg(player, "§c申し訳ありません。応答の生成に失敗しました。")
                return@runBlocking null
            }

            val command = parseResponse(content)
            if (command == null) {
                Util.msg(player, "§c申し訳ありません。コマンドの生成に失敗しました。")
                return@runBlocking null
            }

            // コマンドの検証
            if (!validateCommand(command)) {
                Util.msg(player, "§c申し訳ありません。無効なコマンドが生成されました。")
                return@runBlocking null
            }

            // コマンドの説明を表示
            player.sendMessage(
                Component.text()
                    .append(Component.text(Util.prefix))
                    .append(Component.text(command.description)
                        .color(NamedTextColor.GREEN))
                    .append(
                        Component.text(" [クリックで実行]")
                            .color(NamedTextColor.GOLD)
                            .clickEvent(ClickEvent.runCommand(command.toExecutableCommand()))
                            .hoverEvent(HoverEvent.showText(Component.text("実行されるコマンド: ${command.toExecutableCommand()}")))
                    )
                    .build()
            )
            command

        } catch (e: Exception) {
            plugin.logger.warning("Failed to generate command: ${e.message}")
            Util.msg(player, "§c申し訳ありません。エラーが発生しました。")
            null
        }
    }

    /**
     * 市場の状況を分析し、アドバイスを提供する
     * @param player アドバイスを求めるプレイヤー
     * @return 市場分析コマンド
     */
    fun analyzeMarket(player: Player): AssistantCommand {
        return AssistantCommand.createMarketAnalysis()
    }

    /**
     * 特定のアイテムの価格トレンドを分析する
     * @param player 分析を求めるプレイヤー
     * @param itemName アイテム名
     * @return トレンド分析コマンド
     */
    fun analyzePriceTrend(player: Player, itemName: String): AssistantCommand {
        return AssistantCommand.createTrendAnalysis(itemName)
    }

    /**
     * AIの応答をAssistantCommandに変換
     */
    private fun parseResponse(response: String): AssistantCommand? {
        return try {
            val assistantResponse = AssistantResponse.fromJson(response)
            if (assistantResponse == null) {
                plugin.logger.warning("Failed to parse response to AssistantResponse\nResponse: $response")
                return null
            }
            
            val command = assistantResponse.toCommand()
            if (command == null) {
                plugin.logger.warning("Failed to convert AssistantResponse to AssistantCommand\nResponse: $response")
                return null
            }
            
            command
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse response: ${e.message}\nResponse: $response")
            null
        }
    }

    /**
     * コマンドの検証を行う
     * @param command 検証するコマンド
     * @param player プレイヤー
     * @return 検証結果
     */
    private fun validateCommand(command: AssistantCommand): Boolean {
        return when (command.type) {
            CommandType.MARKET_BUY, CommandType.MARKET_SELL -> {
                val item = command.parameters["item"] as? String
                val amount = command.parameters["amount"] as? Int
                if (item == null || amount == null || amount <= 0) {
                    return false
                }
                Market.getItemIndex().contains(item)
            }
            CommandType.ORDER_BUY, CommandType.ORDER_SELL -> {
                val item = command.parameters["item"] as? String
                val amount = command.parameters["amount"] as? Int
                val price = command.parameters["price"] as? Double
                if (item == null || amount == null || price == null || amount <= 0 || price <= 0) {
                    return false
                }
                Market.getItemIndex().contains(item)
            }
            CommandType.ORDER_CANCEL -> {
                val orderId = command.parameters["orderId"] as? String
                orderId != null
            }
            CommandType.PRICE_CHECK, CommandType.TREND_ANALYSIS -> {
                val item = command.parameters["item"] as? String ?: return false
                Market.getItemIndex().contains(item)
            }
            CommandType.MARKET_ANALYSIS -> true
        }
    }
}

