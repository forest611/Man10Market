package red.man10.man10market.assistant

import com.google.gson.Gson

/**
 * AIアシスタントからの応答をパースするためのデータクラス
 */
data class AssistantResponse(
    val type: String,
    val action: String,
    val parameters: Parameters,
    val description: String
) {
    data class Parameters(
        val item: String? = null,
        val amount: Int? = null,
        val price: Double? = null,
        val orderId: String? = null
    )

    companion object {
        private val gson = Gson()

        /**
         * JSON文字列からAssistantResponseを生成
         */
        fun fromJson(json: String): AssistantResponse? {
            return try {
                gson.fromJson(json, AssistantResponse::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * AssistantResponseをAssistantCommandに変換
     */
    fun toCommand(): AssistantCommand? {
        return when (type) {
            "MARKET_BUY" -> parameters.item?.let { item ->
                parameters.amount?.let { amount ->
                    AssistantCommand.createMarketBuy(item, amount)
                }
            }
            "MARKET_SELL" -> parameters.item?.let { item ->
                parameters.amount?.let { amount ->
                    AssistantCommand.createMarketSell(item, amount)
                }
            }
            "ORDER_BUY" -> parameters.item?.let { item ->
                parameters.amount?.let { amount ->
                    parameters.price?.let { price ->
                        AssistantCommand.createOrderBuy(item, amount, price)
                    }
                }
            }
            "ORDER_SELL" -> parameters.item?.let { item ->
                parameters.amount?.let { amount ->
                    parameters.price?.let { price ->
                        AssistantCommand.createOrderSell(item, amount, price)
                    }
                }
            }
            "ORDER_CANCEL" -> parameters.orderId?.let { orderId ->
                AssistantCommand.createOrderCancel(orderId)
            }
            "PRICE_CHECK" -> parameters.item?.let { item ->
                AssistantCommand.createPriceCheck(item)
            }
            "MARKET_ANALYSIS" -> AssistantCommand.createMarketAnalysis()
            "TREND_ANALYSIS" -> parameters.item?.let { item ->
                AssistantCommand.createTrendAnalysis(item)
            }
            else -> null
        }
    }
}
