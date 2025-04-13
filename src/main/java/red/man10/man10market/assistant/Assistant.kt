package red.man10.man10market.assistant

import org.bukkit.entity.Player
import red.man10.man10market.Man10Market
import red.man10.man10market.Util
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
// コルーチンは使用しないため削除
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Man10Market用のAIアシスタント機能を提供するクラス
 * 市場での取引のアドバイスや、価格分析などを行う
 */
class Assistant private constructor() {
    private lateinit var httpClient: OkHttpClient
    private lateinit var config: AssistantConfig
    private lateinit var conversationManager: ConversationManager
    private lateinit var taskExecutor: TaskExecutor
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    companion object {
        private var instance: Assistant? = null
        private lateinit var plugin: Man10Market

        fun getInstance(): Assistant {
            return instance ?: synchronized(this) {
                instance ?: Assistant().also { instance = it }
            }
        }

        fun setup(plugin: Man10Market, config: AssistantConfig) {
            this.plugin = plugin
            this.instance = Assistant()
            this.instance!!.initialize(config)
            // 会話マネージャーの初期化
            ConversationManager.setup(plugin)
        }
    }

    /**
     * OpenAI APIの設定を初期化
     */
    fun initialize(config: AssistantConfig) {
        this.config = config
        this.httpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
        
        // Bukkitのスケジューラを使用して非同期処理を実行
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                // リクエスト内容をタスクに分割
                val taskList = requestToTasks(player, request)
                if (taskList == null || taskList.tasks.isEmpty()) {
                    Util.msg(player, "§c§lリクエストの分析に失敗しました。もう一度お試しください。")
                    return@Runnable
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
                        return@Runnable
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
        })
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
            // メッセージ配列の作成
            val messagesArray = createMessagesArray(player, prompt, isFromUser)
            
            // APIリクエストのボディを作成
            val requestBody = createRequestBody(messagesArray)
            
            // HTTPリクエストを実行
            val content = executeRequest(requestBody)
            
            // ユーザーからのリクエストの場合は会話履歴を保存
            if (isFromUser) {
                conversationManager.saveConversation(player, prompt, content)
            }
            
            return content
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send request to AI: ${e.message}")
            e.printStackTrace()
            return "エラーが発生しました。後で再度お試しください。"
        }
    }
    
    /**
     * メッセージ配列を作成する
     */
    private fun createMessagesArray(player: Player, prompt: String, isFromUser: Boolean): JsonArray {
        val messagesArray = JsonArray()
        
        // システムプロンプトの作成と追加
        val systemPrompt = """あなたはMinecraftサーバー「Man10」の市場アシスタントです。
            プレイヤーのプロンプトに応じて、適切な市場情報やアドバイスを提供してください。
            
            プレイヤーからのリクエストに応じて、必要な情報を取得し、適切な市場情報や取引コマンドを生成してください。
            """.trimIndent()
        
        addMessageToArray(messagesArray, "system", systemPrompt)
        
        // 会話履歴を取得して追加
        if (isFromUser) {
            val history = conversationManager.getConversationHistory(player)
            if (history.isNotEmpty()) {
                // 古い順に並べ替えて追加
                history.reversed().forEach { conversation ->
                    addMessageToArray(messagesArray, "user", conversation.message)
                    addMessageToArray(messagesArray, "assistant", conversation.response)
                }
            }
        }
        
        // 現在のプロンプトを追加
        addMessageToArray(messagesArray, "user", prompt)
        
        return messagesArray
    }
    
    /**
     * メッセージを配列に追加するヘルパー関数
     */
    private fun addMessageToArray(messagesArray: JsonArray, role: String, content: String) {
        val message = JsonObject()
        message.addProperty("role", role)
        message.addProperty("content", content)
        messagesArray.add(message)
    }
    
    /**
     * APIリクエストのボディを作成する
     */
    private fun createRequestBody(messagesArray: JsonArray): JsonObject {
        val requestBody = JsonObject()
        requestBody.addProperty("model", config.model)
        requestBody.add("messages", messagesArray)
        requestBody.addProperty("temperature", config.temperature)
        requestBody.addProperty("max_tokens", config.maxTokens)
        return requestBody
    }
    
    /**
     * HTTPリクエストを実行する
     */
    private fun executeRequest(requestBody: JsonObject): String {
        // APIキーをトリムして余分な空白を削除
        val cleanApiKey = config.apiKey.trim()
        
        // HTTPリクエストを作成
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $cleanApiKey")
            .addHeader("Content-Type", "application/json")
            .post(gson.toJson(requestBody).toRequestBody(JSON))
            .build()
        
        // 同期的にリクエストを実行
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("APIリクエストが失敗しました: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: throw IOException("APIレスポンスが空です")
            return parseResponse(responseBody)
        }
    }
    
    /**
     * APIレスポンスを解析する
     */
    private fun parseResponse(responseBody: String): String {
        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
        
        // 応答の取得
        val choices = jsonResponse.getAsJsonArray("choices")
        return if (choices == null || choices.size() == 0) {
            "応答の生成に失敗しました。"
        } else {
            val message = choices.get(0).asJsonObject.getAsJsonObject("message")
            message.get("content").asString
        }
    }
}
