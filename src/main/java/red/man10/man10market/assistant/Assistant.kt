package red.man10.man10market.assistant

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import org.bukkit.entity.Player
import red.man10.man10market.Man10Market
import red.man10.man10market.MarketData
import red.man10.man10market.Market
import red.man10.man10market.Util
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit

/**
 * Man10Market用のAIアシスタント機能を提供するクラス
 * 市場での取引のアドバイスや、価格分析などを行う
 */
class Assistant private constructor() {
    private lateinit var openAI: OpenAIClient
    private lateinit var config: AssistantConfig

    companion object {
        private var instance: Assistant? = null
        private lateinit var plugin: Man10Market

        fun getInstance(): Assistant {
            return instance ?: synchronized(this) {
                instance ?: Assistant().also { instance = it }
            }
        }

        fun setup(plugin: Man10Market, apiKey : String) {
            this.plugin = plugin
            this.instance = Assistant()
            this.instance!!.initialize(AssistantConfig(apiKey))
        }
    }

    /**
     * OpenAI APIの設定を初期化
     */
    fun initialize(config: AssistantConfig) {
        this.config = config
        this.openAI = OpenAIOkHttpClient.builder()
            .apiKey(config.apiKey)
            .build()
    }

    /**
     * プレイヤーからの質問に応答し、適切なコマンドを生成する
     * @param player 質問したプレイヤー
     * @param question 質問内容
     * @return 生成されたコマンド（該当する場合）
     */
    fun ask(player: Player, question: String) {

            val marketItem = Market.getItemIndex() // アイテムのインデックスを取得

            val allPriceStr = mutableListOf<String>()

            Market.getItemIndex().forEach {
                val price = Market.getPrice(it)
                allPriceStr.add("アイテム名:$it 価格:${price.price}円 売値:${price.ask}円 買値:${price.bid}円")
            }

            val systemPrompt = """あなたはMinecraftサーバー「Man10」の市場アシスタントです。
                プレイヤーのプロンプトに応じて、適切な取引コマンドを生成してください。
                
                取引できるアイテムは以下の通りです。
                この名前はコマンドを生成する際のアイテム名として利用されます。
                $marketItem
                
                現在の市場価格は以下の通りです。
                価格指定成行注文の際には、この価格を参考にしてください。
                $allPriceStr
                
                以下のコマンドタイプが利用可能です：
                - MARKET_BUY: 市場価格(成り行き注文)での購入
                - MARKET_SELL: 市場価格(成り行き注文)での売却
                - ORDER_BUY: 指値(価格指定)での買い注文
                - ORDER_SELL: 指値(価格指定)での売り注文
                - ORDER_CANCEL: 注文のキャンセル
                - PRICE_CHECK: 価格確認
                - MESSAGE: メッセージの送信(プレイヤーのリクエストが対応できない場合)
                
                MESSAGEは現在価格を伝えたり、市場の状況を説明するために使用してください。
                
                応答は必ず以下のJSON形式で返してください
                Jsonも平文で、マークダウンなどで囲まないでください。
                {
                    \"type\": \"コマンドタイプ\",
                    \"action\": \"アクション名\",
                    \"parameters\": {
                        \"item\": \"アイテム名\",
                        \"amount\": 数量,
                        \"price\": 価格
                    },
                    \"description\": \"実行内容の説明\"
                }
                
                Jsonが生成できない場合は、エラーメッセージを返してください。
                """.trimIndent()

        try {
            // OpenAI APIリクエストの作成
            val params = ChatCompletionCreateParams.builder()
                .model(ChatModel.of(config.model))
                .addSystemMessage(systemPrompt)
                .addUserMessage(question)
                .temperature(config.temperature)
                .build()

            // APIリクエストの実行
            val chatCompletion = openAI.chat().completions().create(params)

            // 応答の取得
            val content = if (chatCompletion.choices().isEmpty()) null else chatCompletion.choices()[0].message().content().get()
            if (content == null) {
                Util.msg(player, "§c申し訳ありません。応答の生成に失敗しました。")
                return
            }

//            Bukkit.getLogger().info("Assistant response: $content")

            val command = parseResponse(content)
            if (command == null) {
                Util.msg(player, "§cコマンドの生成に失敗しました。")
                Util.msg(player, "§c$content")
                return
            }

            // コマンドの検証
            if (!validateCommand(command)) {
                Util.msg(player, "§c申し訳ありません。無効なコマンドが生成されました。")
                return
            }

            // メッセージの場合はそのまま表示
            if (command.type == CommandType.MESSAGE) {
                Util.msg(player, command.description)
                return
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
        } catch (e: Exception) {
            plugin.logger.warning("Failed to generate command: ${e.message}")
            Util.msg(player, "§c申し訳ありません。エラーが発生しました。")
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
            CommandType.MESSAGE -> true
        }
    }
}

