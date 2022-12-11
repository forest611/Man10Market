package red.man10.man10market

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10market.Util.format
import red.man10.man10market.Util.msg

object Command :CommandExecutor{

    const val OP = ""
    const val USER = ""
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (args.isEmpty()){

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
            }


        }


    }
}