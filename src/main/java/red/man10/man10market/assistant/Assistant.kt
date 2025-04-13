package red.man10.man10market.assistant

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.ChatCompletionCreateParams
import com.openai.models.ChatModel
import org.bukkit.entity.Player
import red.man10.man10market.Man10Market
import red.man10.man10market.Util
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.*

/**
 * Man10Market用のAIアシスタント機能を提供するクラス
 * 市場での取引のアドバイスや、価格分析などを行う
 */
class Assistant private constructor() {
    private lateinit var openAI: OpenAIClient
    private lateinit var config: AssistantConfig
    private lateinit var conversationManager: ConversationManager
    private lateinit var taskExecutor: TaskExecutor
    private val gson = Gson()

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
            // 会話マネージャーの初期化
            ConversationManager.setup(plugin)
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
        this.conversationManager = ConversationManager.getInstance()
        this.taskExecutor = TaskExecutor(plugin, this)
    }

    /**
     * 最初のリクエストを送る
     * 
     * @param player 対話中のプレイヤー
     * @param request リクエスト
     */
    fun ask(player: Player, request: String) {
        // プレイヤーに処理開始を通知
        Util.msg(player, "§a§lリクエストを処理しています...")
        
        // 非同期でタスク処理を実行
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // リクエスト内容をタスクに分割
                val taskList = requestToTasks(player, request)
                if (taskList == null || taskList.tasks.isEmpty()) {
                    Util.msg(player, "§c§lリクエストの分析に失敗しました。もう一度お試しください。")
                    return@launch
                }
                
                // タスクの数を通知
                Util.msg(player, "§a§l${taskList.tasks.size}個のタスクに分割しました。順番に実行します...")
                
                // タスクの実行結果を保存するリスト
                val results = mutableListOf<Map<String, Any>>()
                
                // 各タスクを順番に実行
                taskList.tasks.forEachIndexed { index, taskInfo ->
                    val subTask = taskInfo.toSubTask()
                    
                    // 現在実行中のタスクを通知
                    Util.msg(player, "§e§lタスク ${index + 1}/${taskList.tasks.size}: ${subTask.description}")
                    
                    // タスクを実行
                    val result = taskExecutor.executeSubTask(player, subTask)
                    
                    // 結果をリストに追加
                    results.add(
                        mapOf(
                            "task" to subTask.description,
                            "type" to subTask.type.name,
                            "success" to result.success,
                            "message" to result.message,
                            "data" to result.data
                        )
                    )
                    
                    // 実行結果を通知
                    val statusPrefix = if (result.success) "§a§l成功: " else "§c§l失敗: "
                    Util.msg(player, statusPrefix + result.message)
                    
                    // 失敗した場合は中断
                    if (!result.success && subTask.type != TaskType.CONDITION_CHECK) {
                        Util.msg(player, "§c§lタスクの実行に失敗したため、処理を中断します。")
                        return@launch
                    }
                }
                
                // 最終結果のレポートを生成
                if (results.isNotEmpty()) {
                    val reportTask = SubTask(
                        type = TaskType.RESULT_REPORT,
                        description = "実行結果のレポート生成",
                        parameters = mapOf("results" to results)
                    )
                    
                    val reportResult = taskExecutor.executeSubTask(player, reportTask)
                    if (reportResult.success) {
                        Util.msg(player, "§b§l=== 実行結果レポート ===")
                        Util.msg(player, reportResult.message)
                    }
                }
                
            } catch (e: Exception) {
                plugin.logger.warning("Failed to execute tasks: ${e.message}")
                Util.msg(player, "§c§lエラーが発生しました: ${e.message}")
            }
        }
    }

    /**
     * リクエストをタスクリストに変換
     * @return タスクのリスト、または変換に失敗した場合はnull
     */
    private fun requestToTasks(player: Player, request: String): TaskList? {
        val prompt = """
            下記のリクエストを以下のJSONフォーマットに従って分割してください。
            各タスクには適切なタイプを指定し、必要なパラメータを含めてください。
            
            リクエスト:
            ```$request```
            
            タスクタイプ:
            - info_gathering: 情報収集タスク（例：アイテム価格の取得）
            - condition_check: 条件チェックタスク（例：価格が指定値以下か確認）
            - trade_execution: 取引実行タスク（例：アイテムの購入・売却）
            - result_report: 結果レポートタスク
            
            パラメータ詳細:
            1. info_gathering (情報収集)
               - {"item": "アイテム名"} - 特定アイテムの情報を取得
               - {} - 全アイテムの情報を取得
            
            2. condition_check (条件チェック)
               - {"item": "アイテム名", "price": 100} - アイテムの価格が指定値と比較
               - {"item": "アイテム名"} - アイテムの存在確認
            
            3. trade_execution (取引実行) - 必ず「action」パラメータを含めてください
               - 成行買い: {"action": "market_buy", "item": "アイテム名", "amount": 10}
               - 成行売り: {"action": "market_sell", "item": "アイテム名", "amount": 10}
               - 指値買い: {"action": "order_buy", "item": "アイテム名", "amount": 10, "price": 100}
               - 指値売り: {"action": "order_sell", "item": "アイテム名", "amount": 10, "price": 100}
            
            JSONフォーマット:
            ```json
            {
                "tasks": [
                    {
                        "task": "タスク1の説明",
                        "type": "info_gathering",
                        "parameters": {"item": "ダイヤモンド"}
                    },
                    {
                        "task": "タスク2の説明",
                        "type": "condition_check",
                        "parameters": {"item": "ダイヤモンド", "price": 100}
                    },
                    ...
                ]
            }
            ```
            
            JSONのみを返してください。他の説明は不要です。
        """.trimIndent()
        
        // AIにリクエストを送信
        val response = sendRequest(player, prompt, false)
        
        try {
            // JSONレスポンスを抽出
            val jsonPattern = "\\{\\s*\"tasks\"\\s*:\\s*\\[.*?\\]\\s*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonPattern.find(response)
            
            if (jsonMatch != null) {
                val jsonStr = jsonMatch.value
                return TaskList.fromJson(jsonStr)
            } else {
                // 完全なJSONオブジェクトとしてパースを試みる
                return TaskList.fromJson(response)
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse tasks from response: ${e.message}")
            return null
        }
    }

    /**
     * ユーザー/プラグインからのリクエストをAIに投げる関数
     * @param player 対話中のプレイヤー
     * @param prompt プロンプト
     * @param isFromUser リクエスト元がユーザーかプラグインか
     * @return AIの回答
     */
    fun sendRequest(player: Player, prompt: String, isFromUser: Boolean): String {
        try {
            // 会話履歴を取得
            val conversationMessages = if (isFromUser) {
                conversationManager.getConversationHistoryAsMessages(player)
            } else {
                // プラグインからのリクエストの場合は会話履歴を使用しない
                emptyList()
            }
            
            // システムプロンプトの作成
            val systemPrompt = """あなたはMinecraftサーバー「Man10」の市場アシスタントです。
                プレイヤーのプロンプトに応じて、適切な市場情報やアドバイスを提供してください。
                
                プレイヤーからのリクエストに応じて、必要な情報を取得し、適切な市場情報や取引コマンドを生成してください。
                """.trimIndent()
            
            // APIリクエストの作成
            var paramsBuilder = ChatCompletionCreateParams.builder()
                .model(ChatModel.of(config.model))
                .addSystemMessage(systemPrompt)
            
            // 会話履歴を追加
            conversationMessages.forEach { messageAdder ->
                paramsBuilder = messageAdder(paramsBuilder)
            }
            
            // 現在のプロンプトを追加
            paramsBuilder = paramsBuilder.addUserMessage(prompt)
            
            // APIリクエスト実行
            val params = paramsBuilder.temperature(config.temperature).build()
            val chatCompletion = openAI.chat().completions().create(params)
            
            // 応答の取得
            val content = if (chatCompletion.choices().isEmpty()) {
                "応答の生成に失敗しました。"
            } else {
                chatCompletion.choices()[0].message().content().get()
            }
            
            // ユーザーからのリクエストの場合は会話履歴を保存
            if (isFromUser) {
                conversationManager.saveConversation(player, prompt, content)
            }
            
            return content
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send request to AI: ${e.message}")
            return "エラーが発生しました。後で再度お試しください。"
        }
    }
}
