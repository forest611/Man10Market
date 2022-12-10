package red.man10.man10market

import org.bukkit.Bukkit
import red.man10.man10bank.MySQLManager
import red.man10.man10market.Util.msg
import java.util.*
import java.util.concurrent.LinkedBlockingQueue


/**
 * 取引処理
 */
object Market {

    private val transactionQueue = LinkedBlockingQueue<(MySQLManager)->Unit>()
    private var transactionThread = Thread{ transaction() }


    init {
        runTransactionQueue()
    }


    //登録されているアイテムの識別名を取得
    fun asyncGetItemIndex(mysql:MySQLManager): List<String> {

        val list = mutableListOf<String>()

        return list
    }

    //取引アイテムかどうか
    fun asyncIsMarketItem(mysql: MySQLManager,item: String):Boolean{
        return asyncGetItemIndex(mysql).contains(item)
    }

    //アイテムの現在価格(AskとBid)を取得する
    fun asyncGetPrice(mysql: MySQLManager,item:String): PriceData? {

        val ask: Double
        val bid: Double

        val list = asyncGetOrderList(mysql,item)?:return null

        if (list.isEmpty()){
            return PriceData(item,0.0,0.0)
        }

        ask = list.filter { f -> f.sell }.minOf { m -> m.price }//売り指値の最安値
        bid = list.filter { f -> f.buy }.maxOf { m -> m.price }//買い指値の最高値

        return PriceData(item,ask,bid)
    }

    //指値注文を取得する
    @Synchronized
    fun asyncGetOrderList(mysql: MySQLManager,item:String): List<OrderData>? {

        if (!asyncIsMarketItem(mysql, item))
            return null

        return emptyList()
    }


    //成り行き注文
    fun sendMarketBuy(uuid:UUID,item:String,lot: Int){

        transactionQueue.add {

            val p = Bukkit.getPlayer(uuid)

            if (!asyncIsMarketItem(it,item)){
                return@add
            }




        }
    }

    fun sendMarketSell(uuid: UUID,item: String,lot: Int){

        transactionQueue.add {

            val p = Bukkit.getPlayer(uuid)

            if (!asyncIsMarketItem(it,item)){
                return@add
            }

        }

    }

    //指値
    fun sendOrderBuy(uuid: UUID,item: String,lot:Int,price:Double){

        transactionQueue.add {

            val p = Bukkit.getOfflinePlayer(uuid)

            if (!asyncIsMarketItem(it,item)){
                msg(p.player,"§c§l登録されていないアイテムです")
                return@add
            }

            if (lot<=0){
                msg(p.player,"§c§l注文数を1個以上にしてください")

                return@add
            }

            if (price<=0.0){
                msg(p.player,"§c§l値段を1円以上にしてください")

                return@add
            }

            val nowPrice = asyncGetPrice(it,item)

            if (nowPrice == null){
                msg(p.player,"§c§l登録されていないアイテムです")

                return@add
            }

            //売値より高い指値入れられない
            if (price>nowPrice.ask){
                msg(p.player,"§c§l売値より安い値段に設定してください")

                return@add
            }

            it.execute("INSERT INTO order_table (player, uuid, item_id, price, buy, sell, lot, entry_date) " +
                    "VALUES ('${p.name}', '${uuid}', '${item}', ${price}, 1, 0, ${lot}, DEFAULT)")

            msg(p.player,"§b§l指値買§e§lを発注しました")
        }

    }

    fun sendOrderSell(uuid: UUID,item: String,lot:Int,price: Double){

        transactionQueue.add {

            val p = Bukkit.getOfflinePlayer(uuid)

            if (!asyncIsMarketItem(it,item)){
                msg(p.player,"§c§l登録されていないアイテムです")
                return@add
            }

            if (lot<=0){
                msg(p.player,"§c§l注文数を1個以上にしてください")

                return@add
            }

            if (price<=0.0){
                msg(p.player,"§c§l値段を1円以上にしてください")

                return@add
            }

            val nowPrice = asyncGetPrice(it,item)

            if (nowPrice == null){
                msg(p.player,"§c§l登録されていないアイテムです")

                return@add
            }

            //買値より安い指値を入れれない
            if (price<nowPrice.bid){
                msg(p.player,"§c§l買値より高い値段に設定してください")

                return@add
            }

            it.execute("INSERT INTO order_table (player, uuid, item_id, price, buy, sell, lot, entry_date) " +
                    "VALUES ('${p.name}', '${uuid}', '${item}', ${price}, 0, 1, ${lot}, DEFAULT)")

            msg(p.player,"§c§l指値売§e§lを発注しました")
        }

    }

    //指値の削除
    fun deleteOrder(uuid: UUID,id:Int){

    }

    //成りが入った時に指値の個数の更新をする
    fun updateOrder(){

    }




    /////////////////////////////////
    //注文処理を順番に捌いていくキュー
    //////////////////////////////////
    fun runTransactionQueue(){

        //すでに起動していたら止める
        interruptTransactionQueue()

        transactionThread = Thread{ transaction() }
        transactionThread.start()
    }
    fun interruptTransactionQueue(){
        if (transactionThread.isAlive)
            transactionThread.interrupt()
    }

    private fun transaction(){

        val mysql = MySQLManager(Man10Market.instance,"Man10MarketQueue")

        while (true){
            try {

                val action = transactionQueue.take()
                action.invoke(mysql)

            }catch (e:InterruptedException){
                Bukkit.getLogger().info("取引スレッドを停止しました")
                break
            } catch (_:Exception){

            }
        }

    }

    data class OrderData(
        var uuid:UUID,
        var orderID:Int,
        var item: String,
        var price: Double,
        var lot : Int,
        var buy:Boolean,
        var sell:Boolean,

    )

    data class PriceData(
        var item:String,
        var ask:Double,
        var bid:Double,
        var price :Double = ask+bid/2
    )

}