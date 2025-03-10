package red.man10.man10market.assistant

/**
 * アシスタントが実行可能なコマンドの種類
 */
enum class CommandType {
    MARKET_BUY,        // 市場での購入 (/mce marketbuy)
    MARKET_SELL,       // 市場での売却 (/mce marketsell)
    ORDER_BUY,         // 指値買い注文 (/mce orderbuy)
    ORDER_SELL,        // 指値売り注文 (/mce ordersell)
    ORDER_CANCEL,      // 注文のキャンセル (/mce ordercancel)
    PRICE_CHECK,       // 価格確認 (/mce price)
    MARKET_ANALYSIS,   // 市場分析（複数の統計情報を収集）
    TREND_ANALYSIS     // トレンド分析（特定アイテムの価格推移）
}

/**
 * アシスタントが生成するコマンドのフォーマット
 * @property type コマンドの種類
 * @property action 実行するアクション（実際のコマンド文字列）
 * @property parameters コマンドのパラメータ
 * @property description ユーザーに表示する実行内容の説明
 */
data class AssistantCommand(
    val type: CommandType,
    val action: String,
    val parameters: Map<String, Any>,
    val description: String
) {
    /**
     * コマンドを実行可能な形式に変換
     * @return 実行可能なコマンド文字列
     */
    fun toExecutableCommand(): String {
        return when (type) {
            CommandType.MARKET_BUY -> "/mce marketbuy ${parameters["item"]} ${parameters["amount"]}"
            CommandType.MARKET_SELL -> "/mce marketsell ${parameters["item"]} ${parameters["amount"]}"
            CommandType.ORDER_BUY -> "/mce orderbuy ${parameters["item"]} ${parameters["amount"]} ${parameters["price"]}"
            CommandType.ORDER_SELL -> "/mce ordersell ${parameters["item"]} ${parameters["amount"]} ${parameters["price"]}"
            CommandType.ORDER_CANCEL -> "/mce ordercancel ${parameters["orderId"]}"
            CommandType.PRICE_CHECK -> "/mce price ${parameters["item"]}"
            CommandType.MARKET_ANALYSIS -> "/mce allprice"  // 全体の市場分析用
            CommandType.TREND_ANALYSIS -> "/mce price ${parameters["item"]}"  // 個別アイテムの分析用
        }
    }

    companion object {
        /**
         * 市場購入コマンドを作成
         */
        fun createMarketBuy(item: String, amount: Int): AssistantCommand {
            return AssistantCommand(
                type = CommandType.MARKET_BUY,
                action = "marketbuy",
                parameters = mapOf(
                    "item" to item,
                    "amount" to amount
                ),
                description = "${item}を${amount}個、市場価格で購入します"
            )
        }

        /**
         * 市場売却コマンドを作成
         */
        fun createMarketSell(item: String, amount: Int): AssistantCommand {
            return AssistantCommand(
                type = CommandType.MARKET_SELL,
                action = "marketsell",
                parameters = mapOf(
                    "item" to item,
                    "amount" to amount
                ),
                description = "${item}を${amount}個、市場価格で売却します"
            )
        }

        /**
         * 指値買い注文コマンドを作成
         */
        fun createOrderBuy(item: String, amount: Int, price: Double): AssistantCommand {
            return AssistantCommand(
                type = CommandType.ORDER_BUY,
                action = "orderbuy",
                parameters = mapOf(
                    "item" to item,
                    "amount" to amount,
                    "price" to price
                ),
                description = "${item}を${amount}個、${price}で指値買い注文を出します"
            )
        }

        /**
         * 指値売り注文コマンドを作成
         */
        fun createOrderSell(item: String, amount: Int, price: Double): AssistantCommand {
            return AssistantCommand(
                type = CommandType.ORDER_SELL,
                action = "ordersell",
                parameters = mapOf(
                    "item" to item,
                    "amount" to amount,
                    "price" to price
                ),
                description = "${item}を${amount}個、${price}で指値売り注文を出します"
            )
        }

        /**
         * 注文キャンセルコマンドを作成
         */
        fun createOrderCancel(orderId: String): AssistantCommand {
            return AssistantCommand(
                type = CommandType.ORDER_CANCEL,
                action = "ordercancel",
                parameters = mapOf("orderId" to orderId),
                description = "注文ID: ${orderId}の注文をキャンセルします"
            )
        }

        /**
         * 価格確認コマンドを作成
         */
        fun createPriceCheck(item: String): AssistantCommand {
            return AssistantCommand(
                type = CommandType.PRICE_CHECK,
                action = "price",
                parameters = mapOf("item" to item),
                description = "${item}の現在の市場価格を確認します"
            )
        }

        /**
         * 市場分析コマンドを作成
         */
        fun createMarketAnalysis(): AssistantCommand {
            return AssistantCommand(
                type = CommandType.MARKET_ANALYSIS,
                action = "allprice",
                parameters = mapOf(),
                description = "市場全体の価格状況を分析します"
            )
        }

        /**
         * トレンド分析コマンドを作成
         */
        fun createTrendAnalysis(item: String): AssistantCommand {
            return AssistantCommand(
                type = CommandType.TREND_ANALYSIS,
                action = "price",
                parameters = mapOf("item" to item),
                description = "${item}の価格トレンドを分析します"
            )
        }
    }
}
