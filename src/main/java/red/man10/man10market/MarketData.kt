package red.man10.man10market

import red.man10.man10bank.MySQLManager
import red.man10.man10itembank.ItemData
import red.man10.man10market.Man10Market.Companion.instance
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap


///
///mce mibのデータを取得
///
object MarketData {

    private val sdf = SimpleDateFormat("yyyy-MM-dd 00:00:00")


    //ユーザーのアイテム資産の総額を見る
    fun getItemEstate(uuid: UUID): Double {

        val mysql = MySQLManager(instance,"Man10MarketData")

        val rs = mysql.query("select item_id,amount from item_storage where uuid='${uuid}';")?:return 0.0

        val map = HashMap<Int,Int>()

        while (rs.next()){
            map[rs.getInt("item_id")] = rs.getInt("amount")
        }

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select item_id,lot from order_table where uuid='${uuid}' and sell=1;")?:return 0.0

        while (rs2.next()){
            val id = ItemData.getID(rs2.getString("item_id"))
            map[id] = (map[id]?:0) + rs2.getInt("lot")
        }

        rs2.close()
        mysql.close()

        val dataMap = ItemData.getItemIndexMap()

        var estate = 0.0

        map.forEach{
            estate += (dataMap[it.key]!!.bid * it.value)
        }

        return estate
    }

    //時価総額を取得
    fun getMarketValue(item:String):Double{

        val mysql = MySQLManager(instance,"Man10MarketData")
        val rs = mysql.query("select sum(amount) from item_storage where item_key='$item';")?:return  0.0

        if (!rs.next()){
            rs.close()
            mysql.close()
            return 0.0
        }

        var total = rs.getInt(1)

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select sum(lot) from order_table where item_id='$item' and sell=1;")?:return 0.0

        if (!rs2.next()){
            rs2.close()
            mysql.close()
            return 0.0
        }

        total += rs2.getInt(1)

        rs2.close()
        mysql.close()

        val bid = ItemData.getItemIndexMap().values.firstOrNull { it.itemKey == item }?.bid ?:0.0

        return bid*total

    }

    //今日の(未確定)OHLCを見る
    fun getTodayOHLC(item:String):MarketSeries{

        val mysql = MySQLManager(instance,"Man10MarketData")
        val rs = mysql.query("select bid,volume from tick_table " +
                "where item_id='${item}' and date>'${sdf.format(Date())}';")?:return MarketSeries()

        val list = mutableListOf<Double>()
        var volume = 0

        while (rs.next()){
            list.add(rs.getDouble("bid"))
            volume += rs.getInt("volume")
        }

        rs.close()
        mysql.close()

        if (list.isEmpty())
            return MarketSeries()

        return MarketSeries(list.first(),list.maxOf { it },list.minOf { it },list.last(),volume,list.size)
    }

    fun getYesterdayOHLC(item:String):MarketSeries{

        val calender = Calendar.getInstance()
        calender.time = Date()
        calender.add(Calendar.DAY_OF_YEAR,-1)
        calender.set(Calendar.HOUR,0)
        calender.set(Calendar.MINUTE,0)

        val yesterday = calender.time

        val mysql = MySQLManager(instance,"Man10MarketData")
        val rs = mysql.query("select bid,volume from tick_table " +
                "where item_id='${item}' and date>'${sdf.format(yesterday)}' and date< '${sdf.format(Date())}';")?:return MarketSeries()

        val list = mutableListOf<Double>()
        var volume = 0

        while (rs.next()){
            list.add(rs.getDouble("bid"))
            volume += rs.getInt("volume")
        }

        rs.close()
        mysql.close()

        if (list.isEmpty())
            return MarketSeries()

        return MarketSeries(list.first(),list.maxOf { it },list.minOf { it },list.last(),volume,list.size)
    }

    //騰落率
    fun getPercentageChange(item: String):Double{

        val yesterday = getYesterdayOHLC(item)
        val today = getTodayOHLC(item)

        if (yesterday.close == 0.0){
            return 0.0
        }

        return ((today.close/yesterday.close) - 1)
    }

    data class MarketSeries(
        val open :Double = 0.0,
        val high:Double = 0.0,
        val low:Double = 0.0,
        val close:Double = 0.0,
        var volume: Int = 0,
        var tickVolume: Int = 0
    )

}