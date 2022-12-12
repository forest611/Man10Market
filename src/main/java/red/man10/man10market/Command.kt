package red.man10.man10market

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10market.Util.format
import red.man10.man10market.Util.msg
import red.man10.man10market.Util.prefix
import red.man10.man10market.menu.MainMenu

object Command :CommandExecutor{

    const val OP = "man10market.op"
    private const val USER = "man10market.user"
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (args.isEmpty()){
            if (sender !is Player)return false
            if (!sender.hasPermission(USER))return false
            MainMenu(sender,0).open()
            return true
        }

        when(args[0]){

            "price" ->{

                if (sender !is Player)return false
                if (!sender.hasPermission(USER))return false

                if (args.size!=2){
                    msg(sender,"§c§l/mce price <銘柄名>")
                    return true
                }

                showOrderBook(sender,args[1])

                return true
            }

            "marketbuy" ->{

                if (sender !is Player)return false
                if (!sender.hasPermission(USER))return false

                if (args.size!=3){
                    msg(sender,"§c§l/mce marketbuy <銘柄名> <個数>")
                    return true
                }

                val item = args[1]
                val lot = args[2].toIntOrNull()

                if (lot==null){
                    msg(sender,"§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendMarketBuy(sender.uniqueId,item,lot)

            }

            "marketsell" ->{

                if (sender !is Player)return false
                if (!sender.hasPermission(USER))return false

                if (args.size!=3){
                    msg(sender,"§c§l/mce marketsell <銘柄名> <個数>")
                    return true
                }

                val item = args[1]
                val lot = args[2].toIntOrNull()

                if (lot==null){
                    msg(sender,"§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendMarketSell(sender.uniqueId,item,lot)
            }

            "orderbuy" ->{

                if (sender !is Player)return false
                if (!sender.hasPermission(USER))return false

                if (args.size!=4){
                    msg(sender,"§c§l/mce orderbuy <銘柄名> <単価> <個数>")
                    return true
                }

                val item = args[1]
                val price = args[2].toDoubleOrNull()
                val lot = args[3].toIntOrNull()

                if (price==null){
                    msg(sender,"§c§l金額は数字で入力してください")
                    return true
                }

                if (lot==null){
                    msg(sender,"§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendOrderBuy(sender.uniqueId,item,lot,price)

                return true
            }

            "ordersell" ->{

                if (sender !is Player)return false
                if (!sender.hasPermission(USER))return false

                if (args.size!=4){
                    msg(sender,"§c§l/mce ordersell <銘柄名> <単価> <個数>")
                    return true
                }

                val item = args[1]
                val price = args[2].toDoubleOrNull()
                val lot = args[3].toIntOrNull()

                if (price==null){
                    msg(sender,"§c§l金額は数字で入力してください")
                    return true
                }

                if (lot==null){
                    msg(sender,"§c§l個数は数字で入力してください")
                    return true
                }

                Market.sendOrderSell(sender.uniqueId,item,lot,price)

                return true
            }

        }

        return false
    }

    fun showOrderBook(p:Player,item:String){

        Market.getOrderList(item){

            if (it==null){
                msg(p,"存在しない銘柄です")
                return@getOrderList
            }

            val sell = it.filter { f -> f.sell }.groupBy { g -> g.price }.toMutableMap()
            val buy = it.filter { f -> f.buy }.groupBy { g -> g.price }.toMutableMap()

            var showMarketBuy = false
            var showMarketSell = false

            msg(p,"§a§l==========[ 注文状況: $item ]==========")
            msg(p,String.format("§b§l%5s    %5s    %5s","売数量" ,"値段","買数量"))

            if (sell.isEmpty()){
                msg(p,String.format("§a§l%8s    %4s","","売注文なし"))
            }else{

                val prices = sell.keys.sorted().reversed()

                for (orderPrice in prices){
                    val lot = sell[orderPrice]!!.sumOf { s -> s.lot }
                    val color = if (orderPrice == prices.last()) "§a§l" else "§e§l"
                    msg(p,String.format("${color}%8d    %8s",lot,format(orderPrice)))
                }

                showMarketBuy = true
            }

            if (buy.isEmpty()){
                msg(p,String.format("§c§l           %4s    %8s","買注文なし",""))
            }else{

                val prices = buy.keys.sorted().reversed()

                for (orderPrice in prices){
                    val lot = buy[orderPrice]!!.sumOf { s -> s.lot }

                    val color = if ((orderPrice == prices.first())) "§c§l" else "§e§l"

                    msg(p,String.format("$color           %8s    %8d",format(orderPrice),lot))
                }

                showMarketSell = true
            }

            val totalBuy = it.filter { f->f.buy }.sumOf { s->s.lot }
            val totalSell = it.filter { f->f.sell }.sumOf { s->s.lot }

            msg(p,"§f成り行き注文(現在価格で取引をする)")
            if (showMarketSell){
                p.sendMessage(text("$prefix   §a§n[成行売り注文]§f ${ totalBuy }個まで売却可能")
                    .clickEvent(ClickEvent.suggestCommand("/mce marketsell $item "))
                    .hoverEvent(HoverEvent.showText(text("§6§l/mce marketsell $item <個数>"))))
            }
            if (showMarketBuy){
                p.sendMessage(text("$prefix   §c§n[成行買い注文]§f ${ totalSell }個まで購入可能")
                    .clickEvent(ClickEvent.suggestCommand("/mce marketbuy $item "))
                    .hoverEvent(HoverEvent.showText(text("§6§l/mce marketbuy $item <個数>"))))
            }
            msg(p,"§f指値注文(価格を指定して注文を予約する)")
            p.sendMessage(text("$prefix   §a§n[指値売り注文]")
                .clickEvent(ClickEvent.suggestCommand("/mce ordersell $item "))
                .hoverEvent(HoverEvent.showText(text("§6§l/mce ordersell $item <購入単価> <個数>"))))

            p.sendMessage(text("$prefix   §c§n[指値買い注文]")
                .clickEvent(ClickEvent.suggestCommand("/mce orderbuy $item "))
                .hoverEvent(HoverEvent.showText(text("§6§l/mce orderbuy $item <購入単価> <個数>"))))


        }


    }
}