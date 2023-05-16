package red.man10.man10market

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import red.man10.man10bank.MySQLManager
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10itembank.ItemData
import red.man10.man10market.Man10Market.Companion.instance
import red.man10.man10market.Util.format
import red.man10.man10market.Util.prefix
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap
import kotlin.math.max
import kotlin.math.min


///
///mce mibのデータを取得
///
object MarketData {

    private val sdf = SimpleDateFormat("yyyy-MM-dd 00:00:00")

    private val dailyOHLCCache = ConcurrentHashMap<Pair<String, String>, MarketSeries>()
    private val hourlyOHLCCache = ConcurrentHashMap<String,Pair<MarketSeries,Date>>()
    private val marketValueCache = ConcurrentHashMap<String, Double>()
    private val highLowPriceCache = ConcurrentHashMap<String, HighLow>()//高値安値

    private val timer = Timer()

    init {
        Thread {
            loadHighLowPrice()
        }.start()

        timer.schedule(
            object : TimerTask(){
                override fun run() {
                    Market.getItemIndex().forEach { checkHourOHLC(it) }
                }
            },0,60*1000)
    }


    private fun loadHighLowPrice() {

        val mysql = MySQLManager(instance, "Man10Market")
        val rs = mysql.query("select item_id,bid from tick_table  where volume>0;") ?: return

        while (rs.next()) {
            val item = rs.getString("item_id")
            val price = rs.getDouble("bid")
            val cache = highLowPriceCache[item] ?: HighLow(0.0, 0.0)//高値、安値の順番

            if (cache.high < price) {
                highLowPriceCache[item] = HighLow(price, cache.low)
            }
            if (cache.low > price) {
                highLowPriceCache[item] = HighLow(cache.high, price)
            }
        }

        rs.close()
        mysql.close()

    }

    fun tickEvent(item: String,last:Market.PriceData) {

        val price = Market.getPrice(item)

        val highlow = highLowPriceCache[item]// ?: HighLow(0.0, Double.MAX_VALUE)

        if (price.ask != 0.0){

            if (price.price>last.price){
                Bukkit.broadcast(Component.text( "${prefix}§a${item}: ${format(last.price)}から${format(price.price)}へ値上がりしました"))
            }

            if (price.price<last.price){
                Bukkit.broadcast(Component.text( "${prefix}§c${item}: ${format(last.price)}から${format(price.price)}へ値下がりしました"))
            }

        }

        //価格情報をCSVに吐き出す
        asyncWritePriceDataToCSV()
        //OHLCの確認
        checkHourOHLC(item)

        if (highlow != null){
            //高値更新
            if (highlow.high < price.bid) {

                highLowPriceCache[item] = HighLow(price.bid, highlow.low)

                Bukkit.broadcast(Component.text("${prefix}§a§lマーケット速報！！${item}:${format(price.bid)}円 過去最高値更新！！！"))
            }

            //安値更新
            if (highlow.low > price.bid) {

                highLowPriceCache[item] = HighLow(highlow.high, price.bid)

                Bukkit.broadcast(Component.text("${prefix}§c§lマーケット速報！！${item}:${format(price.bid)}円 過去最安値更新！！！"))
            }

        }
    }

    //ユーザーのアイテム資産の総額を見る
    fun getItemEstate(uuid: UUID): Double {

        val mysql = MySQLManager(instance, "Man10MarketData")

        val rs = mysql.query("select item_id,amount from item_storage where uuid='${uuid}';") ?: return 0.0

        val map = HashMap<Int, Int>()

        while (rs.next()) {
            map[rs.getInt("item_id")] = rs.getInt("amount")
        }

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select item_id,lot from order_table where uuid='${uuid}' and sell=1;") ?: return 0.0

        while (rs2.next()) {
            val id = ItemData.getID(rs2.getString("item_id"))
            map[id] = (map[id] ?: 0) + rs2.getInt("lot")
        }

        rs2.close()
        mysql.close()

        val dataMap = ItemData.getItemIndexMap()

        var estate = 0.0

        map.forEach {
            estate += (dataMap[it.key]!!.bid * it.value)
        }

        return estate
    }

    //時価総額を取得
    fun getMarketValue(item: String): Double {

        if (marketValueCache[item] != null) {
            return marketValueCache[item]!!
        }

        val mysql = MySQLManager(instance, "Man10MarketData")
        val rs = mysql.query("select sum(amount) from item_storage where item_key='$item';") ?: return 0.0

        if (!rs.next()) {
            rs.close()
            mysql.close()
            return 0.0
        }

        var total = rs.getInt(1)

        rs.close()
        mysql.close()

        val rs2 = mysql.query("select sum(lot) from order_table where item_id='$item' and sell=1;") ?: return 0.0

        if (!rs2.next()) {
            rs2.close()
            mysql.close()
            return 0.0
        }

        total += rs2.getInt(1)

        rs2.close()
        mysql.close()

        val bid = Market.getPrice(item).bid

        marketValueCache[item] = bid * total

        return bid * total

    }


    fun getYesterdayOHLC(item: String): MarketSeries {

        val calender = Calendar.getInstance()
        calender.time = Date()
        calender.add(Calendar.DAY_OF_YEAR, -1)
        calender.set(Calendar.HOUR, 0)
        calender.set(Calendar.MINUTE, 0)

        val yesterday = calender.time

        val key = Pair(item, sdf.format(yesterday))

        //キャッシュがあるならそっちをとる
        if (dailyOHLCCache[key] != null) {
            return dailyOHLCCache[key]!!
        }


        val mysql = MySQLManager(instance, "Man10MarketData")
        val rs = mysql.query(
            "select bid,volume from tick_table " +
                    "where item_id='${item}' and date>'${sdf.format(yesterday)}' and date< '${sdf.format(Date())}';"
        ) ?: return MarketSeries()

        val list = mutableListOf<Double>()
        var volume = 0

        while (rs.next()) {
            list.add(rs.getDouble("bid"))
            volume += rs.getInt("volume")
        }

        rs.close()
        mysql.close()

        if (list.isEmpty())
            return MarketSeries()

        val ret = MarketSeries(list.first(), list.maxOf { it }, list.minOf { it }, list.last(), volume, list.size)
        dailyOHLCCache[key] = ret

        return ret
    }

    //騰落率
    fun getPercentageChange(item: String): Double {

        val yesterday = getYesterdayOHLC(item).close
        val today = Market.getPrice(item).bid

        if (yesterday == 0.0) {
            return 0.0
        }

        return ((today / yesterday) - 1)
    }

    fun asyncWritePriceDataToCSV(){
        Market.addJob {

            val csv = File(instance.dataFolder.path+"/price.csv")
            val index = Market.getItemIndex()


            csv.bufferedWriter().use { writer->

                writer.write("アイテム名,仲直,売値,買値\n")
                index.forEach { item ->
                    val price = Market.getPrice(item)
                    writer.write("$item," +
                            "${String.format("%.0f",price.price)}," +
                            "${String.format("%.0f",price.ask)}," +
                            "${String.format("%.0f",price.bid)}\n")
                }
            }
        }
    }

    fun saveHour(item: String){

        val nowPrice = Market.getPrice(item).bid
        val data = hourlyOHLCCache[item]?: Pair(MarketSeries(nowPrice), Date())
        val series = data.first

        val last = Calendar.getInstance()
        last.time = data.second

        Market.addJob {sql ->



            sql.execute("INSERT INTO man10_market.hour_table " +
                    "(item_id, open, high, low, close, year, month, day, hour, date, volume) " +
                    "VALUES ('${item}', ${nowPrice}, ${nowPrice}, ${nowPrice}, ${nowPrice}, " +
                    "${last.get(Calendar.YEAR)}, ${last.get(Calendar.MONTH)}, ${last.get(Calendar.DAY_OF_MONTH)}, ${last.get(Calendar.HOUR)}, now(), 0)")




        }
    }

    fun checkHourOHLC(item:String){

        val nowPrice = Market.getPrice(item).bid
        val data = hourlyOHLCCache[item]?: Pair(MarketSeries(nowPrice), Date())
        val series = data.first

        series.high = max(series.high,nowPrice)
        series.low = min(series.low,nowPrice)
        series.close = nowPrice

        val last = Calendar.getInstance()
        last.time = data.second
        val now = Calendar.getInstance()
        now.time = Date()

        //時間の変更があったら確定してCSVに書き込み
        if (last.get(Calendar.HOUR) != now.get(Calendar.HOUR)){

            asyncWriteOHLC(item,last.time,series,"hour")
            //終値を始値にして新しいキャッシュを作る
            hourlyOHLCCache.remove(item)
            return
        }

        //高値安値を修正して保存
        hourlyOHLCCache[item] = Pair(series,last.time)
    }

    private fun asyncWriteOHLC(item:String,date:Date,data:MarketSeries,tf:String){

        Thread{

            val id = ItemBankAPI.getItemData(item)?.id?:-1
            val csvFile = File(instance.dataFolder.path+"/${tf}/${id}.csv")
            val writer = BufferedWriter(FileWriter(csvFile,true))

            if (!csvFile.exists()){
                writer.write("date,open,high,low,close,volume\n")
            }
            writer.write("${SimpleDateFormat("yyyy-MM-dd HH:00:00").format(date)},${data.open},${data.high},${data.low},${data.close},${data.volume}")
            writer.close()
        }.start()

    }

    data class MarketSeries(
        var open: Double = 0.0,
        var high: Double = 0.0,
        var low: Double = 0.0,
        var close: Double = 0.0,
        var volume: Int = 0,    //取引アイテム数
        var tickVolume: Int = 0 //Tick数
    )

    data class HighLow(
        var high: Double,
        var low: Double
    )

}