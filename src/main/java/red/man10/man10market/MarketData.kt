package red.man10.man10market

import red.man10.man10bank.MySQLManager
import red.man10.man10market.Man10Market.Companion.instance
import java.text.SimpleDateFormat
import java.util.*

object MarketData {

    private val sdf = SimpleDateFormat("yyyy-MM-dd 00:00:00")

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

        return MarketSeries(list.first(),list.max(),list.min(),list.last(),volume,list.size)
    }

    //騰落率
    fun getPercentageChange(item: String):Double{

        val yesterday = getYesterdayOHLC(item)
        val today = getYesterdayOHLC(item)

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