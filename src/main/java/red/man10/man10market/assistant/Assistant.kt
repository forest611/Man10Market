package red.man10.man10market.assistant

import org.bukkit.entity.Player
import red.man10.man10market.Man10Market
import red.man10.man10market.Util
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
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
        Util.msg(player, "リクエスト>> $request")
        
        // Bukkitのスケジューラを使用して非同期処理を実行
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                // リクエスト内容をタスクに分割
                val taskList = requestToTasks(player, request)
                if (taskList == null || taskList.tasks.isEmpty()) {
                    Util.msg(player, "§c§lリクエストの分析に失敗しました。もう一度お試しください。")
                    return@Runnable
                }
                                
                // タスクの実行結果を保存するリスト
                val results = mutableListOf<Map<String, Any>>()
                
                // 実行するタスクリスト
                var remainingTasks = taskList.tasks.toMutableList()
                
                // 全てのタスクが完了するまで繰り返す
                while (remainingTasks.isNotEmpty()) {
                    val taskInfo = remainingTasks.removeAt(0)
                    val subTask = taskInfo.toSubTask()
                    
                    // タスクを実行
                    val result = taskExecutor.executeSubTask(player, subTask)
                    
                    // 結果をリストに追加
                    val resultMap = mapOf(
                        "task" to subTask.description,
                        "type" to subTask.type.name,
                        "success" to result.success,
                        "message" to result.message,
                        "data" to result.data
                    )
                    results.add(resultMap)
                    
                    // 実行結果を通知
                    val statusPrefix = if (result.success) "§a成功: " else "§c失敗: "
                    Util.msg(player, statusPrefix + result.message)
                    
                    // 失敗した場合は中断
                    if (!result.success && subTask.type != TaskType.CONDITION_CHECK) {
                        Util.msg(player, "§cタスクの実行に失敗したため、処理を中断します。")
                        break
                    }
                    
                    // 残りのタスクがある場合、結果に基づいてタスクを再評価
                    if (remainingTasks.isNotEmpty()) {
                        val updatedTasks = reevaluateTasks(player, request, results, remainingTasks)
                        if (updatedTasks != null) {
                            remainingTasks = updatedTasks.toMutableList()
                        }
                    }
                }

                // 実行結果がない場合は終了
                if (results.isEmpty()){
                    return@Runnable
                }
                
                // 最終結果のレポートを生成
                val reportTask = SubTask(
                    type = TaskType.RESULT_REPORT,
                    description = "実行結果のレポート生成",
                    parameters = mapOf("results" to results)
                )
                
                val reportResult = taskExecutor.executeSubTask(player, reportTask)

                // 実行結果を通知
                if (reportResult.success) {
                    Util.msg(player, "§l処理完了\n" + reportResult.message)
                }
            
            } catch (e: Exception) {
                plugin.logger.warning("Failed to execute tasks: ${e.message}")
                Util.msg(player, "§c§lエラーが発生しました: ${e.message}")
            }
        })
    }

    /**
     * 実行結果に基づいて残りのタスクを再評価する
     * @param player プレイヤー
     * @param originalRequest 元のリクエスト
     * @param completedResults 完了したタスクの結果
     * @param remainingTasks 残りのタスク
     * @return 更新されたタスクリスト、または変更がない場合はnull
     */
    private fun reevaluateTasks(player: Player, originalRequest: String, completedResults: List<Map<String, Any>>, remainingTasks: List<TaskInfo>): List<TaskInfo>? {
        val prompt = """
            以下のリクエストと実行結果に基づいて、残りのタスクを再評価してください。
            必要に応じてタスクの追加、削除、変更を行ってください。
            
            元のリクエスト:
            ```$originalRequest```
            
            完了したタスクの結果:
            ${gson.toJson(completedResults)}
            
            残りのタスク:
            ${gson.toJson(remainingTasks)}
            
            残りのタスクを再評価し、以下のJSON形式で返してください。
            必要な変更がない場合は、そのまま残りのタスクを返してください。
            
            ```json
            {
                "tasks": [
                    {
                        "task": "タスクの説明",
                        "type": "info_gathering",
                        "parameters": {"item": "アイテム名"}
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
                val taskList = TaskList.fromJson(jsonStr)
                return taskList?.tasks
            } else {
                // 完全なJSONオブジェクトとしてパースを試みる
                val taskList = TaskList.fromJson(response)
                return taskList?.tasks
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse updated tasks from response: ${e.message}")
            return null
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
