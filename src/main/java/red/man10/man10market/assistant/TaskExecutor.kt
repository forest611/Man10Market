package red.man10.man10market.assistant

import org.bukkit.entity.Player
import red.man10.man10market.Market
import red.man10.man10market.Util
import red.man10.man10market.Man10Market
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.StringReader

/**
 * タスク実行を担当するクラス
 */
class TaskExecutor(private val plugin: Man10Market, private val assistant: Assistant) {
    private val gson = Gson()

    /**
     * サブタスクを実行
     */
    fun executeSubTask(player: Player, task: SubTask): TaskResult {
        return when (task.type) {
            TaskType.INFO_GATHERING -> executeInfoGathering(player, task)
            TaskType.CONDITION_CHECK -> executeConditionCheck(player, task)
            TaskType.TRADE_EXECUTION -> executeTradeExecution(player, task)
            TaskType.RESULT_REPORT -> executeResultReport(player, task)
        }
    }

    /**
     * 情報収集タスクの実行
     */
    private fun executeInfoGathering(player: Player, task: SubTask): TaskResult {
        val itemName = task.parameters["item"] as? String
        
        if (itemName != null) {
            // アイテムが存在するか確認
            if (!Market.getItemIndex().contains(itemName)) {
                return TaskResult(
                    success = false,
                    message = "アイテム「$itemName」は取引可能なアイテムリストに存在しません。",
                    data = mapOf("item" to itemName)
                )
            }
            
            // アイテム情報を取得
            val price = Market.getPrice(itemName)
            return TaskResult(
                success = true,
                message = "${itemName}の価格情報を取得しました",
                data = mapOf(
                    "item" to itemName,
                    "price" to price.price,
                    "ask" to price.ask,
                    "bid" to price.bid
                )
            )
        } else {
            // 全アイテムの情報を取得
            val allItems = Market.getItemIndex()
            val topItems = allItems.take(5)
            val priceInfo = mutableMapOf<String, Map<String, Double>>()
            
            topItems.forEach { item ->
                val price = Market.getPrice(item)
                priceInfo[item] = mapOf(
                    "price" to price.price,
                    "ask" to price.ask,
                    "bid" to price.bid
                )
            }
            
            return TaskResult(
                success = true,
                message = "主要アイテムの価格情報を取得しました",
                data = mapOf(
                    "items" to topItems,
                    "prices" to priceInfo
                )
            )
        }
    }

    /**
     * 条件チェックタスクの実行
     * 条件チェックはAIに任せるため、必要な情報を収集してAIに送信
     */
    private fun executeConditionCheck(player: Player, task: SubTask): TaskResult {
        val condition = task.description
        val itemName = task.parameters["item"] as? String
        val targetPrice = task.parameters["price"] as? Double
        
        // 条件チェックに必要な情報を収集
        val contextInfo = mutableMapOf<String, Any>()
        
        if (itemName != null) {
            val price = Market.getPrice(itemName)
            contextInfo["item"] = itemName
            contextInfo["current_price"] = price.price
            contextInfo["ask"] = price.ask
            contextInfo["bid"] = price.bid
        }
        
        if (targetPrice != null) {
            contextInfo["target_price"] = targetPrice
        }
        
        // 条件チェックをAIに依頼
        val prompt = """
            以下の条件が満たされているかどうかを評価し、JSONで結果を返してください。
            
            条件: $condition
            
            コンテキスト情報:
            ${gson.toJson(contextInfo)}
            
            結果は以下のJSON形式で返してください:
            {
                "satisfied": true/false,
                "reason": "条件が満たされている/いない理由"
            }
        """.trimIndent()
        
        val response = assistant.sendRequest(player, prompt, false)
        
        try {
            // レスポンスからJSON部分を抽出する試み
            val jsonPattern = "\\{[\\s\\S]*?\\}"
            val regex = Regex(jsonPattern)
            val matchResult = regex.find(response)
            
            if (matchResult != null) {
                val jsonStr = matchResult.value
                
                // 抽出したJSONをパース
                val jsonElement = JsonParser.parseString(jsonStr)
                if (jsonElement.isJsonObject) {
                    val jsonObject = jsonElement.asJsonObject
                    val satisfied = jsonObject.get("satisfied")?.asBoolean ?: false
                    val reason = jsonObject.get("reason")?.asString ?: "理由が提供されていません"
                    
                    return TaskResult(
                        success = true,
                        message = reason,
                        data = mapOf(
                            "satisfied" to satisfied,
                            "reason" to reason,
                            "context" to contextInfo
                        )
                    )
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse condition check response: ${e.message}")
        }
        
        return TaskResult(
            success = false,
            message = "条件チェックの結果をパースできませんでした",
            data = mapOf("response" to response)
        )
    }

    /**
     * 取引実行タスクの実行
     */
    private fun executeTradeExecution(player: Player, task: SubTask): TaskResult {
        val action = task.parameters["action"] as? String ?: return TaskResult(
            success = false,
            message = "アクションが指定されていません"
        )
        
        val itemName = task.parameters["item"] as? String ?: return TaskResult(
            success = false,
            message = "アイテムが指定されていません"
        )
        
        val amount = (task.parameters["amount"] as? Number)?.toInt() ?: return TaskResult(
            success = false,
            message = "数量が指定されていません"
        )
        
        // アイテムが存在するか確認
        if (!Market.getItemIndex().contains(itemName)) {
            return TaskResult(
                success = false,
                message = "アイテム「$itemName」は取引可能なアイテムリストに存在しません。",
                data = mapOf("item" to itemName)
            )
        }
        
        return when (action.lowercase()) {
            "market_buy" -> {
                // 成行買い注文
                Market.sendMarketBuy(player.uniqueId, itemName, amount)
                // 成功とみなす
                val result = true
                if (result) {
                    TaskResult(
                        success = true,
                        message = "${itemName}を${amount}個、市場価格で購入しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "action" to "market_buy"
                        )
                    )
                } else {
                    TaskResult(
                        success = false,
                        message = "${itemName}の購入に失敗しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "action" to "market_buy"
                        )
                    )
                }
            }
            "market_sell" -> {
                // 成行売り注文
                Market.sendMarketSell(player.uniqueId, itemName, amount)
                // 成功とみなす
                val result = true
                if (result) {
                    TaskResult(
                        success = true,
                        message = "${itemName}を${amount}個、市場価格で売却しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "action" to "market_sell"
                        )
                    )
                } else {
                    TaskResult(
                        success = false,
                        message = "${itemName}の売却に失敗しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "action" to "market_sell"
                        )
                    )
                }
            }
            "order_buy" -> {
                // 指値買い注文
                val price = (task.parameters["price"] as? Number)?.toDouble() ?: return TaskResult(
                    success = false,
                    message = "価格が指定されていません"
                )
                
                Market.sendOrderBuy(player.uniqueId, itemName, amount, price)
                // 成功とみなす
                val result = true
                if (result) {
                    TaskResult(
                        success = true,
                        message = "${itemName}を${amount}個、${price}円で指値買い注文を出しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "price" to price,
                            "action" to "order_buy"
                        )
                    )
                } else {
                    TaskResult(
                        success = false,
                        message = "指値買い注文の発行に失敗しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "price" to price,
                            "action" to "order_buy"
                        )
                    )
                }
            }
            "order_sell" -> {
                // 指値売り注文
                val price = (task.parameters["price"] as? Number)?.toDouble() ?: return TaskResult(
                    success = false,
                    message = "価格が指定されていません"
                )
                
                Market.sendOrderSell(player.uniqueId, itemName, amount, price)
                // 成功とみなす
                val result = true
                if (result) {
                    TaskResult(
                        success = true,
                        message = "${itemName}を${amount}個、${price}円で指値売り注文を出しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "price" to price,
                            "action" to "order_sell"
                        )
                    )
                } else {
                    TaskResult(
                        success = false,
                        message = "指値売り注文の発行に失敗しました",
                        data = mapOf(
                            "item" to itemName,
                            "amount" to amount,
                            "price" to price,
                            "action" to "order_sell"
                        )
                    )
                }
            }
            else -> TaskResult(
                success = false,
                message = "未知のアクション「$action」が指定されました",
                data = mapOf("action" to action)
            )
        }
    }

    /**
     * 結果レポートタスクの実行
     */
    private fun executeResultReport(player: Player, task: SubTask): TaskResult {
        @Suppress("UNCHECKED_CAST")
        val results = task.parameters["results"] as? List<Map<String, Any>> ?: return TaskResult(
            success = false,
            message = "結果データが指定されていません"
        )
        
        // 結果をAIに送信してレポートを生成
        val prompt = """
            以下の実行結果を分析し、ユーザーにわかりやすく説明してください。
            
            実行結果:
            ${gson.toJson(results)}
            
            - 説明は簡潔に、各タスク一文で結果を説明してください
            - マークダウン記法は使用しないでください（*, #, - などの記号を使わない）
        """.trimIndent()
        
        val response = assistant.sendRequest(player, prompt, false)
        
        return TaskResult(
            success = true,
            message = response,
            data = mapOf("report" to response)
        )
    }
}
