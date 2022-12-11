package red.man10.man10market

import org.bukkit.Bukkit
import red.man10.man10bank.MySQLManager
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10market.Man10Market.Companion.bankAPI
import red.man10.man10market.Util.format
import red.man10.man10market.Util.msg
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.floor


/**
 * 取引処理
 */
object Market {

    private val transactionQueue = LinkedBlockingQueue<()->Unit>()
    private var transactionThread = Thread{ transaction() }
    private val mysql = MySQLManager(Man10Market.instance,"Man10MarketQueue")

    init {
        runTransactionQueue()
    }


    //登録されているアイテムの識別名を取得
    private fun getItemIndex(): List<String> {
        return ItemBankAPI.getItemIndexList()
    }

    //取引アイテムかどうか
    private fun isMarketItem(item: String):Boolean{
        return getItemIndex().contains(item)
    }

    //アイテムの現在価格(AskとBid)を取得する
    private fun asyncGetPrice(item:String): PriceData? {

        val ask: Double
        val bid: Double

        val list = asyncGetOrderList(item)?:return null

        if (list.isEmpty()){
            return PriceData(item,0.0,0.0)
        }

        val sell = list.filter { f -> f.sell }
        val buy = list.filter { f -> f.buy }

        ask = if (sell.isEmpty()) 0.0 else sell.minOf { it.price }
        bid = if (buy.isEmpty()) 0.0 else buy.maxOf { it.price }

        return PriceData(item,ask,bid)
    }

    //指値注文を取得する
    @Synchronized
    private fun asyncGetOrderList(item:String): List<OrderData>? {

        if (!isMarketItem(item))
            return null

        val rs = mysql.query("select * from order_table where item_id='${item}';")?:return null

        val list = mutableListOf<OrderData>()

        while (rs.next()){

            val data = OrderData(
                UUID.fromString(rs.getString("uuid")),
                rs.getInt("id"),
                item,
                rs.getDouble("price"),
                rs.getInt("lot"),
                rs.getInt("buy")==1,
                rs.getInt("sell")==1,
                rs.getDate("entry_date")
            )

            list.add(data)
        }

        rs.close()
        mysql.close()

        return list
    }

    fun getOrderList(item: String,callback:(List<OrderData>?)->Unit){
        transactionQueue.add { callback.invoke(asyncGetOrderList(item)) }
    }

    //成り行き注文
    fun sendMarketBuy(uuid:UUID,item:String,lot: Int){

        transactionQueue.add {

            val p = Bukkit.getPlayer(uuid)

            if (!isMarketItem(item)){
                return@add
            }

            //安い順に売り注文を並べる
            var firstOrder : OrderData?

            //成立個数
            var require = lot

            while (require>0){

                firstOrder = asyncGetOrderList(item)?.filter { it.sell }?.minByOrNull { it.price }

                //指値が亡くなった
                if (firstOrder == null){
                    msg(p,"§c§l現在このアイテムの注文はありません")
                    return@add
                }

                //指値が要求個数より多い場合
                if (firstOrder.lot>require){

                    if (!bankAPI.withdraw(uuid,lot*firstOrder.price,"Man10MarketBuy","マーケット成行買い")){
                        msg(p,"§c§l銀行の残高が足りません！")
                        return@add
                    }

                    asyncTradeOrder(firstOrder.orderID,lot)
                    ItemBankAPI.addItemAmount(uuid,uuid,item,lot)

                    msg(p,"§e§l${lot}個購入")

                    return@add
                }

                //指値が要求個数以下の場合(注文が通ればこの指値は消える)

                //指値の注文数
                val orderLot = firstOrder.lot

                if (!bankAPI.withdraw(uuid,orderLot*firstOrder.price,"Man10MarketBuy","マーケット成行買い")){
                    msg(p,"§c§l銀行の残高が足りません！")
                    return@add
                }

                asyncTradeOrder(firstOrder.orderID,orderLot)
                ItemBankAPI.addItemAmount(uuid,uuid,item,orderLot)

                require-=orderLot

                msg(p,"§e§l${orderLot}個購入")

                continue
            }

        }
    }

    fun sendMarketSell(uuid: UUID,item: String,lot: Int){

        transactionQueue.add {

            val p = Bukkit.getPlayer(uuid)

            if (!isMarketItem(item)){
                return@add
            }

            //高い順に買い注文を並べる
            var firstOrder : OrderData?

            //成立個数
            var require = lot

            while (require>0){

                firstOrder = asyncGetOrderList(item)?.filter { it.buy }?.maxByOrNull { it.price }

                //指値が亡くなった
                if (firstOrder == null){
                    msg(p,"§c§l現在このアイテムの注文はありません")
                    return@add
                }

                //指値が要求個数より多い場合
                if (firstOrder.lot>require){

                    ItemBankAPI.takeItemAmount(uuid,uuid,item,lot){

                        if (it==null){
                            msg(p,"§c§lアイテムの数が足りません")
                            return@takeItemAmount
                        }
                        asyncTradeOrder(firstOrder.orderID,lot)

                        bankAPI.deposit(uuid,(lot*firstOrder.price),"Man10MarketSell","マーケット成行売り")

                        msg(p,"§e§l${lot}個売却")
                    }

                    return@add
                }

                //指値が要求個数以下の場合(注文が通ればこの指値は消える)

                //指値の注文数
                val orderLot = firstOrder.lot

                suspend {

                    require = suspendCoroutine {continuation->

                        ItemBankAPI.takeItemAmount(uuid,uuid,item,orderLot){

                            if (it==null){
                                msg(p,"§c§lアイテムの数が足りません")
                                return@takeItemAmount
                            }

                            asyncTradeOrder(firstOrder.orderID,orderLot)
                            bankAPI.deposit(uuid,(orderLot*firstOrder.price),"Man10MarketSell","マーケット成行売り")

                            msg(p,"§e§l${orderLot}個購入")

                            continuation.resume(require-orderLot)
                        }
                    }
                }

                continue
            }


        }

    }

    //指値
    fun sendOrderBuy(uuid: UUID,item: String,lot:Int,price:Double){

        transactionQueue.add {

            val p = Bukkit.getOfflinePlayer(uuid)

            if (!isMarketItem(item)){
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

            val nowPrice = asyncGetPrice(item)

            if (nowPrice == null){
                msg(p.player,"§c§l登録されていないアイテムです")

                return@add
            }

            //売値より高い指値入れられない
            if (price>nowPrice.ask){
                msg(p.player,"§c§l売値より安い値段に設定してください")

                return@add
            }

            val fixedPrice = floor(price)

            val requireMoney = lot*fixedPrice

            if (!bankAPI.withdraw(uuid,requireMoney,"Man10MarketOrderBuy","マーケット指値買い")){
                msg(p.player,"§c§l銀行の残高が足りません！(必要金額:${format(requireMoney)})")
                return@add
            }

            mysql.execute("INSERT INTO order_table (player, uuid, item_id, price, buy, sell, lot, entry_date) " +
                    "VALUES ('${p.name}', '${uuid}', '${item}', ${fixedPrice}, 1, 0, ${lot}, DEFAULT)")

            msg(p.player,"§b§l指値買§e§lを発注しました")
        }

    }

    fun sendOrderSell(uuid: UUID,item: String,lot:Int,price: Double){

        transactionQueue.add {

            val p = Bukkit.getOfflinePlayer(uuid)

            if (!isMarketItem(item)){
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

            val nowPrice = asyncGetPrice(item)

            if (nowPrice == null){
                msg(p.player,"§c§l登録されていないアイテムです")

                return@add
            }

            //買値より安い指値を入れれない
            if (price<nowPrice.bid){
                msg(p.player,"§c§l買値より高い値段に設定してください")

                return@add
            }

            val fixedPrice = floor(price)

            ItemBankAPI.takeItemAmount(uuid,uuid,item,lot){
                if (it==null){
                    msg(p.player,"§c§lアイテム取り出し失敗！")
                    return@takeItemAmount
                }
                mysql.execute("INSERT INTO order_table (player, uuid, item_id, price, buy, sell, lot, entry_date) " +
                        "VALUES ('${p.name}', '${uuid}', '${item}', ${fixedPrice}, 0, 1, ${lot}, DEFAULT)")

                msg(p.player,"§c§l指値売§e§lを発注しました")
            }
        }

    }


    //指値の削除
    fun cancelOrder(uuid: UUID,id:Int){

    }

    //成りが入った時に指定個数指値を減らす(null失敗、-1個数問題)
    private fun asyncTradeOrder(id: Int, amount:Int):Int?{

        val rs = mysql.query("select * from order_table where id = $id;")

        if (rs==null || !rs.next()){
            return null
        }

        val data = OrderData(
            UUID.fromString(rs.getString("uuid")),
            rs.getInt("id"),
            rs.getString("item_id"),
            rs.getDouble("price"),
            rs.getInt("lot"),
            rs.getInt("buy")==1,
            rs.getInt("sell")==1,
            rs.getDate("entry_date")
        )

        val newAmount = data.lot - amount

        if (newAmount<0){
            return -1
        }

        if (newAmount==0){
            mysql.execute("DELETE from order_table where id = ${id};")
        }else{
            mysql.execute("UPDATE order_table SET lot = $newAmount WHERE id = ${id};")

        }

        //指値買い
        if (data.buy){
            ItemBankAPI.addItemAmount(data.uuid,data.uuid,data.item,amount)
        }

        //指値売り
        if (data.sell){
            bankAPI.deposit(data.uuid,(amount*data.price),"Man10MarketOrderSell","マーケット指値売り")
        }

        return newAmount
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

        while (true){
            try {

                val action = transactionQueue.take()
                action.invoke()

            }catch (e:InterruptedException){
                Bukkit.getLogger().info("取引スレッドを停止しました")
                break
            } catch (e:Exception){
                Bukkit.getLogger().info(e.message)
                e.stackTrace.forEach { Bukkit.getLogger().info("${it.className};${it.methodName};${it.lineNumber}") }
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
        var date:Date

    )

    data class PriceData(
        var item:String,
        var ask:Double,
        var bid:Double,
        var price :Double = ask+bid/2
    )

}