package red.man10.man10market

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.yaml.snakeyaml.error.Mark
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10market.Man10Market.Companion.instance
import red.man10.man10market.Man10Market.Companion.isMarketOpen
import red.man10.man10market.Util.format
import red.man10.man10market.Util.msg
import red.man10.man10market.Util.prefix
import red.man10.man10market.map.PriceMap
import red.man10.man10market.menu.BuyMenu
import red.man10.man10market.menu.MainMenu
import red.man10.man10market.menu.SellMenu
import red.man10.man10market.assistant.Assistant
import java.text.SimpleDateFormat
import java.util.*

object Command : CommandExecutor {

    const val OP = "market.op"
    const val USER = "market.user"
    const val ASSISTANT = "market.assistant"
    private val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm")


    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label != "mce")return false

        if (!isMarketOpen && !sender.hasPermission(OP)) {
            msg(sender as Player, "§c§l現在取引所は閉場しております")
            return true
        }

        if (args.isEmpty()) {
            if (sender !is Player) return false
            if (!sender.hasPermission(USER)) {
                msg(sender,"§c§lマーケットにアクセスする権限がありません")
                return false
            }
            MainMenu(sender, 0).open()
            return true
        }

        when (args[0]) {

            "op" -> {

                if (!sender.hasPermission(OP)) return false

                //OPヘルプ
                if (args.size == 1) {

                    sender.sendMessage("§l/mce op reload : Reload market system.")
                    sender.sendMessage("§l/mce op on : Open market to public.")
                    sender.sendMessage("§l/mce op off : Close market to public.")
                    sender.sendMessage("§l/mce showorder <uuid> : Show orders you select.")

                    return true
                }

                when (args[1]) {

                    "reload" -> {

                        sender.sendMessage("§l市場システムのリロード開始")

                        isMarketOpen = false

                        Market.interruptTransactionQueue()
                        sender.sendMessage("§l注文執行システムの停止")

                        instance.loadMarketConfig()
                        sender.sendMessage("§lConfigの読み込み完了")

                        Market.runTransactionQueue()
                        sender.sendMessage("§l注文執行システムの再開")

                        Man10Market.setupAssistant()
                        sender.sendMessage("§lAIアシスタントの再起動")

                        isMarketOpen = true

                        sender.sendMessage("§l市場システムのリロード完了")

                        return true
                    }

                    "off" -> {
                        sender.sendMessage("§l市場クローズ")
                        isMarketOpen = false
                    }

                    "on" -> {
                        sender.sendMessage("§l市場オープン")
                        isMarketOpen = true
                    }

                    "showorder" -> {

                        val uuid = UUID.fromString(args[2])
                        Market.getUserOrderList(uuid) { orders ->

                            orders.forEach {
                                val color = if (it.buy) "§a§l買" else "§c§l売"
                                val info = text("$prefix$color 単価:${format(it.price)} 個数:${it.lot}")
                                    .hoverEvent(HoverEvent.showText(text("§a§l注文日時:${sdf.format(it.date)}")))
                                val cancel =
                                    text(" §f§l[X]").clickEvent(ClickEvent.runCommand("/mce ordercancel ${it.orderID}"))
                                sender.sendMessage(info.append(cancel))
                            }

                            val totalBuy = orders.filter { it.buy }.sumOf { it.price * it.lot }
                            val totalSell = orders.filter { it.sell }.sumOf { it.price * it.lot }

                            sender.sendMessage("§l指値買い総額:${format(totalBuy)}")
                            sender.sendMessage("§l指値売り総額:${format(totalSell)}")
                        }

                        return true
                    }

                    "map" -> {

                        if (sender !is Player) return false

                        PriceMap.getPriceMap(sender, args[2])

                    }

                }

            }

            "price" -> {

                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) {
                    msg(sender,"§c§lマーケットにアクセスする権限がありません")
                    return false
                }

                if (args.size != 2) {
                    msg(sender, "§c§l/mce price <銘柄名>")
                    return true
                }

                showOrderBook(sender, args[1])

                return true
            }

            "allprice"->{

                Market.addJob {

                    Market.getItemIndex().forEach {
                        val price = Market.getPrice(it)
                        sender.sendMessage("$it 価格:${price.price}円 売値:${price.ask}円 買値:${price.bid}円")
                    }
                }

                MarketData.asyncWritePriceDataToCSV()
            }

            "marketbuy" -> {

                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) return false

                if (args.size != 3) {
                    msg(sender, "§c§l/mce marketbuy <銘柄名> <個数>")
                    return true
                }

                val item = args[1]
                val lot = args[2].toIntOrNull()

                if (lot == null) {
                    msg(sender, "§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendMarketBuy(sender.uniqueId, item, lot)

            }

            "gpt" -> {
                if (sender !is Player) return false
                if (!sender.hasPermission(ASSISTANT)) return false

                if (args.size < 2) {
                    msg(sender, "§c§l[開発中機能]/mce gpt <質問内容>")
                    return true
                }

                msg(sender, "§aアシスタントの応答を待っています...§k§lX")

                val question = args.drop(1).joinToString(" ")
                Assistant.getInstance().ask(sender, question)
                return true
            }

            "marketsell" -> {

                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) return false

                if (args.size != 3) {
                    msg(sender, "§c§l/mce marketsell <銘柄名> <個数>")
                    return true
                }

                val item = args[1]
                val lot = args[2].toIntOrNull()

                if (lot == null) {
                    msg(sender, "§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendMarketSell(sender.uniqueId, item, lot)
            }

            "orderbuy" -> {

                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) return false

                if (args.size != 4) {
                    msg(sender, "§c§l/mce orderbuy <銘柄名> <単価> <個数>")
                    return true
                }

                val item = args[1]
                val price = args[2].toDoubleOrNull()
                val lot = args[3].toIntOrNull()

                if (price == null) {
                    msg(sender, "§c§l金額は数字で入力してください")
                    return true
                }

                if (lot == null) {
                    msg(sender, "§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendOrderBuy(sender.uniqueId, item, lot, price)

                return true
            }

            "ordersell" -> {

                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) return false

                if (args.size != 4) {
                    msg(sender, "§c§l/mce ordersell <銘柄名> <単価> <個数>")
                    return true
                }

                val item = args[1]
                val price = args[2].toDoubleOrNull()
                val lot = args[3].toIntOrNull()

                if (price == null) {
                    msg(sender, "§c§l金額は数字で入力してください")
                    return true
                }

                if (lot == null) {
                    msg(sender, "§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendOrderSell(sender.uniqueId, item, lot, price)

                return true
            }

            "ordercancel" -> {
                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) return false

                if (args.size != 2) {
                    msg(sender, "§c§l/mce ordercancel <注文ID>")
                    return true
                }

                val id = args[1].toIntOrNull()

                if (id == null) {
                    msg(sender, "§c§lIDを数字で入力してください")
                    return true
                }

                val uuid: UUID? = if (sender.hasPermission(OP)) null else sender.uniqueId

                Market.cancelOrder(uuid, id)

                return true
            }

            "showorder" -> {
                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) return false

                Market.getUserOrderList(sender.uniqueId) { orders ->

                    orders.forEach {
                        val color = if (it.buy) "§a§l買" else "§c§l売"
                        val info = text("$prefix$color 単価:${format(it.price)} 個数:${it.lot}")
                            .hoverEvent(HoverEvent.showText(text("§a§l注文日時:${sdf.format(it.date)}")))
                        val cancel =
                            text(" §f§l[X]").clickEvent(ClickEvent.runCommand("/mce ordercancel ${it.orderID}"))
                        sender.sendMessage(info.append(cancel))
                    }

                    val totalBuy = orders.filter { it.buy }.sumOf { it.price * it.lot }
                    val totalSell = orders.filter { it.sell }.sumOf { it.price * it.lot }

                    val estate = MarketData.getItemEstate(sender.uniqueId)

                    msg(sender, "=============================")
                    msg(sender, "§c指値買い総額(注文をとってる金額):§l${format(totalBuy)}円")
                    msg(sender, "§a指値売り総額(売れたらもらえる総額):§l${format(totalSell)}円")
                    msg(sender, "")
                    msg(sender, "§b§l所有アイテムの評価額:${format(estate)}円")

                }

                return true
            }

            "smallboard" -> {
                if (!sender.hasPermission(USER)) return false
                showSmallOrderBook(sender, args[1])
            }

            "sellmenu" ->{
                if (sender !is Player) return false
                if (!sender.hasPermission(USER)) {
                    msg(sender,"§c§lマーケットにアクセスする権限がありません")
                    return false
                }
                SellMenu(sender).open()
                return true
            }

            "buymenu" ->{ //mce buymenu <id>
                if (sender !is Player) return false

                if (args.size != 2){
                    return true
                }

                if (!sender.hasPermission(USER)) {
                    msg(sender,"§c§lマーケットにアクセスする権限がありません")
                    return false
                }

                BuyMenu(sender,args[1]).open()
                return true

            }


        }

        return false
    }

    private fun showSmallOrderBook(sender: CommandSender, item: String) {

        Market.getOrderList(item) { order ->

            if (order == null) {
                sender.sendMessage("存在しない銘柄です")
                return@getOrderList
            }

            val sell = order.filter { f -> f.sell }.groupBy { g -> g.price }.toMutableMap()
            val buy = order.filter { f -> f.buy }.groupBy { g -> g.price }.toMutableMap()

            sender.sendMessage("§a§l==========[ オーダーブック : $item ]==========")
            sender.sendMessage(String.format("§b§l%5s    %5s    %5s", "売数量", "値段", "買数量"))

            if (sell.isEmpty()) {
                sender.sendMessage(String.format("§a§l%8s    %4s", "", "売注文なし"))
            } else {

                val prices = sell.keys.sorted().reversed()

                var over = 0
                //注文個数が5個以上の場合
                if (prices.size > 5) {
                    val overPrice = prices[prices.size - 5]
                    sell.filter { it.key > overPrice }.forEach { f -> f.value.forEach { over += it.lot } }
                    sell.filter { it.key > overPrice }.forEach { sell.remove(it.key) }
                    sender.sendMessage(String.format("§e§l%8d    %8s           ", over, "OVER"))
                }

                for (orderPrice in prices) {
                    val lot = sell[orderPrice]?.sumOf { s -> s.lot } ?: continue
                    val color = if (orderPrice == prices.last()) "§a§l" else "§e§l"
                    sender.sendMessage(String.format("${color}%8d    %8s           ", lot, format(orderPrice)))
                }

            }

            if (buy.isEmpty()) {
                sender.sendMessage(String.format("§c§l           %4s    %8s", "買注文なし", ""))
            } else {

                val prices = buy.keys.sorted().reversed()

                var under = 0

                if (prices.size > 5) {
                    val underPrice = prices[4]
                    buy.filter { it.key < underPrice }.forEach { f -> f.value.forEach { under += it.lot } }
                    buy.filter { it.key < underPrice }.forEach { buy.remove(it.key) }
                }

                for (orderPrice in prices) {
                    val lot = buy[orderPrice]?.sumOf { s -> s.lot } ?: continue

                    val color = if ((orderPrice == prices.first())) "§c§l" else "§e§l"

                    sender.sendMessage(String.format("$color           %8s    %8d", format(orderPrice), lot))
                }

                if (prices.size > 5) {
                    sender.sendMessage(String.format("§e§l           %8s    %8d", "UNDER", under))
                }

            }
        }

    }

    private fun showOrderBook(p: Player, item: String) {

        Market.getOrderList(item) { order ->

            if (order == null) {
                msg(p, "存在しない銘柄です")
                return@getOrderList
            }

            val sell = order.filter { f -> f.sell }.groupBy { g -> g.price }.toMutableMap()
            val buy = order.filter { f -> f.buy }.groupBy { g -> g.price }.toMutableMap()

            var showMarketBuy = false
            var showMarketSell = false

            msg(p, "§a§l==========[ 注文状況: $item ]==========")
            msg(p, String.format("§b§l%5s    %5s    %5s", "売数量", "値段", "買数量"))

            if (sell.isEmpty()) {
                msg(p, String.format("§a§l%8s    %4s", "", "売注文なし"))
            } else {

                val prices = sell.keys.sorted().reversed()

                for (orderPrice in prices) {
                    val lot = sell[orderPrice]!!.sumOf { s -> s.lot }
                    val color = if (orderPrice == prices.last()) "§a§l" else "§e§l"
                    msg(p, String.format("${color}%8d    %8s", lot, format(orderPrice)))
                }

                showMarketBuy = true
            }

            if (buy.isEmpty()) {
                msg(p, String.format("§c§l           %4s    %8s", "買注文なし", ""))
            } else {

                val prices = buy.keys.sorted().reversed()

                for (orderPrice in prices) {
                    val lot = buy[orderPrice]!!.sumOf { s -> s.lot }

                    val color = if ((orderPrice == prices.first())) "§c§l" else "§e§l"

                    msg(p, String.format("$color           %8s    %8d", format(orderPrice), lot))
                }

                showMarketSell = true
            }

            val totalBuy = order.filter { f -> f.buy }.sumOf { s -> s.lot }
            val totalSell = order.filter { f -> f.sell }.sumOf { s -> s.lot }

            msg(p, "§f成り行き注文(現在価格で取引をする)")
            if (showMarketBuy) {
                p.sendMessage(
                    text("$prefix   §a§n[成行買い注文]§f ${totalSell}個まで購入可能")
                        .clickEvent(ClickEvent.suggestCommand("/mce marketbuy $item "))
                        .hoverEvent(HoverEvent.showText(text("§6§l/mce marketbuy $item <個数>")))
                )
            }
            if (showMarketSell) {
                p.sendMessage(
                    text("$prefix   §c§n[成行売り注文]§f ${totalBuy}個まで売却可能")
                        .clickEvent(ClickEvent.suggestCommand("/mce marketsell $item "))
                        .hoverEvent(HoverEvent.showText(text("§6§l/mce marketsell $item <個数>")))
                )
            }
            msg(p, "§f指値注文(価格を指定して注文を予約する)")

            p.sendMessage(
                text("$prefix   §a§n[指値買い注文]")
                    .clickEvent(ClickEvent.suggestCommand("/mce orderbuy $item "))
                    .hoverEvent(HoverEvent.showText(text("§6§l/mce orderbuy $item <購入単価> <個数>")))
            )
            p.sendMessage(
                text("$prefix   §c§n[指値売り注文]")
                    .clickEvent(ClickEvent.suggestCommand("/mce ordersell $item "))
                    .hoverEvent(HoverEvent.showText(text("§6§l/mce ordersell $item <購入単価> <個数>")))
            )

            p.sendMessage(text("$prefix §b§l§n[クリックしてチャートを開く]")
                .clickEvent(ClickEvent.openUrl("https://man10.red/mce/?mode=day&id=${Market.getItemNumber(item)}"))
                .hoverEvent(HoverEvent.showText(text("§6§lWebブラウザで日足のチャートをみる"))))



//            val yesterday = MarketData.getYesterdayOHLC(item)
//            val percentage = MarketData.getPercentageChange(item) * 100
//            val marketValue = MarketData.getMarketValue(item)
//            val percentageText =
//                "§b§l前日比:${if (percentage > 0.0) "§a§l" else if (percentage < 0.0) "§c§l" else "§f§l"}${
//                    format(
//                        percentage,
//                        2
//                    )
//                }%"
//
//            msg(p, "")
//            p.sendMessage(
//                text("$prefix$percentageText")
//                    .hoverEvent(
//                        HoverEvent.showText(
//                            text(
//                                "§e§l前日データ\n" +
//                                        "§e§l始値:${format(yesterday.open)}\n" +
//                                        "§e§l高値:${format(yesterday.high)}\n" +
//                                        "§e§l安値:${format(yesterday.low)}\n" +
//                                        "§e§l終値:${format(yesterday.close)}\n" +
//                                        "§e§l出来高:${yesterday.volume}個\n" +
//                                        "§e§l時価総額:${format(marketValue)}円"
//                            )
//                        )
//                    )
//            )

            msg(p, "")

            val yourOrder = order.filter { it.uuid == p.uniqueId }

            if (yourOrder.isEmpty())
                return@getOrderList
            msg(p, "§b§l==========[ 指値注文 ]==========")

            yourOrder.forEach {
                val color = if (it.buy) "§a§l買" else "§c§l売"
                val info = text("$prefix$color 単価:${format(it.price)} 個数:${it.lot}")
                    .hoverEvent(HoverEvent.showText(text("§a§l注文日時:${sdf.format(it.date)}")))
                val cancel = text(" §f§l[X]").clickEvent(ClickEvent.runCommand("/mce ordercancel ${it.orderID}"))
                p.sendMessage(info.append(cancel))
            }


        }


    }
}