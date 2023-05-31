package red.man10.man10market

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import red.man10.man10bank.MySQLManager
import red.man10.man10itembank.ItemData
import red.man10.man10market.Man10Market.Companion.instance
import red.man10.man10market.Util.format
import red.man10.man10market.Util.prefix
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min


///
///mce mibのデータを取得
///
object MarketData {

    private val dailyCandleCache = ConcurrentHashMap<Pair<String, String>, MarketSeries>()
    private val hourlyCandleCache = ConcurrentHashMap<String,Pair<MarketSeries,Date>>()
//    private val marketValueCache = ConcurrentHashMap<String, Double>()
    private val highLowPriceCache = ConcurrentHashMap<String, HighLow>()//高値安値

    private val timer = Timer()

    //onEnableでよぶ
    fun init(){
        Thread { loadHighLowPrice() }.start()

        timer.schedule(
            object : TimerTask(){
                override fun run() {
                    Market.getItemIndex().forEach {
                        saveHour(it)
                        writeCSV(it,"hour")
                    }
                    Bukkit.getLogger().info("1時間足の値を保存しました")
                }
            },0,1000*60
        )

        asyncWriteDictionaryCSV()
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

    fun tickEvent(item: String,last:Market.PriceData,volume: Int) {

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

        //一時間足に追記
        saveHour(item,volume)

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
//    fun getMarketValue(item: String): Double {
//
//        if (marketValueCache[item] != null) {
//            return marketValueCache[item]!!
//        }
//
//        val mysql = MySQLManager(instance, "Man10MarketData")
//        val rs = mysql.query("select sum(amount) from item_storage where item_key='$item';") ?: return 0.0
//
//        if (!rs.next()) {
//            rs.close()
//            mysql.close()
//            return 0.0
//        }
//
//        var total = rs.getInt(1)
//
//        rs.close()
//        mysql.close()
//
//        val rs2 = mysql.query("select sum(lot) from order_table where item_id='$item' and sell=1;") ?: return 0.0
//
//        if (!rs2.next()) {
//            rs2.close()
//            mysql.close()
//            return 0.0
//        }
//
//        total += rs2.getInt(1)
//
//        rs2.close()
//        mysql.close()
//
//        val bid = Market.getPrice(item).bid
//
//        marketValueCache[item] = bid * total
//
//        return bid * total
//
//    }

//    fun getYesterdayOHLC(item: String): MarketSeries {
//
//        val sdf = SimpleDateFormat("yyyy-MM-dd 00:00:00")
//        val calender = Calendar.getInstance()
//        calender.time = Date()
//        calender.add(Calendar.DAY_OF_YEAR, -1)
//        calender.set(Calendar.HOUR, 0)
//        calender.set(Calendar.MINUTE, 0)
//
//        val yesterday = calender.time
//
//        val key = Pair(item, sdf.format(yesterday))
//
//        //キャッシュがあるならそっちをとる
//        if (dailyCandleCache[key] != null) {
//            return dailyCandleCache[key]!!
//        }
//
//        val mysql = MySQLManager(instance, "Man10MarketData")
//        val rs = mysql.query(
//            "select bid,volume from tick_table " +
//                    "where item_id='${item}' and date>'${sdf.format(yesterday)}' and date< '${sdf.format(Date())}';"
//        ) ?: return MarketSeries()
//
//        val list = mutableListOf<Double>()
//        var volume = 0
//
//        while (rs.next()) {
//            list.add(rs.getDouble("bid"))
//            volume += rs.getInt("volume")
//        }
//
//        rs.close()
//        mysql.close()
//
//        if (list.isEmpty())
//            return MarketSeries()
//
//        val ret = MarketSeries(list.first(), list.maxOf { it }, list.minOf { it }, list.last(), volume, list.size)
//        dailyCandleCache[key] = ret
//
//        return ret
//    }

    //騰落率
//    fun getPercentageChange(item: String): Double {
//
//        val yesterday = getYesterdayOHLC(item).close
//        val today = Market.getPrice(item).bid
//
//        if (yesterday == 0.0) {
//            return 0.0
//        }
//
//        return ((today / yesterday) - 1)
//    }

    //現在値をCSVに書き込む
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

    //id,アイテム名の辞書を書き込む
    private fun asyncWriteDictionaryCSV(){
        Market.addJob {
            val csv = File(instance.dataFolder.path+"/id_list.csv")
            val index = Market.getItemIndex()

            csv.bufferedWriter().use { writer ->
                writer.write("id,アイテム名\n")
                index.forEach { writer.write("${Market.getItemNumber(it)},${it}\n") }

            }
        }
    }

    //一時間足のデータをDBに登録
    fun saveHour(item: String, volume: Int = 0){

        val nowPrice = Market.getPrice(item).bid
        val data = hourlyCandleCache[item]?: Pair(MarketSeries(nowPrice,nowPrice,nowPrice,nowPrice), Date())
        val series = data.first

        val last = Calendar.getInstance()
        last.time = data.second

        val year = last.get(Calendar.YEAR)
        val month = last.get(Calendar.MONTH)+1
        val day = last.get(Calendar.DAY_OF_MONTH)
        val hour = last.get(Calendar.HOUR)

        val high = max(data.first.high,nowPrice)
        val low = min(data.first.low,nowPrice)

        series.high = high
        series.low = low

        Market.addJob {sql ->

            val rs = sql.query("SELECT * from hour_table where " +
                    "item_id='${item}' and year=$year and month=$month and day=$day and hour=$hour")

            // レコードが生成されていなかったら、新規で挿入
            if (rs == null || !rs.next()){
                sql.execute("INSERT INTO hour_table " +
                        "(item_id, open, high, low, close, year, month, day, hour, date, volume) " +
                        "VALUES ('${item}', ${nowPrice}, ${nowPrice}, ${nowPrice}, ${nowPrice}, " +
                        "${year}, ${month}, ${day}, ${hour}, now(), ${volume})")

                hourlyCandleCache.remove(item)
                sql.close()
                return@addJob
            }

            rs.close()
            sql.close()

            //価格情報の更新
            sql.execute("UPDATE hour_table SET " +
                        "high=$high,low=$low,close=$nowPrice,volume=volume+${volume} where " +
                    "item_id='${item}' and year=$year and month=$month and day=$day and hour=$hour")

            hourlyCandleCache[item] = Pair(series,data.second)
        }
    }

    //一時間足のデータをDBに登録
    fun saveDay(item: String, volume: Int = 0){

        val nowPrice = Market.getPrice(item).bid
        val data = hourlyCandleCache[item]?: Pair(MarketSeries(nowPrice,nowPrice,nowPrice,nowPrice), Date())
        val series = data.first

        val last = Calendar.getInstance()
        last.time = data.second

        val year = last.get(Calendar.YEAR)
        val month = last.get(Calendar.MONTH)+1
        val day = last.get(Calendar.DAY_OF_MONTH)
        val hour = last.get(Calendar.HOUR)

        val high = max(data.first.high,nowPrice)
        val low = min(data.first.low,nowPrice)

        series.high = high
        series.low = low

        Market.addJob {sql ->

            val rs = sql.query("SELECT * from hour_table where " +
                    "item_id='${item}' and year=$year and month=$month and day=$day and hour=$hour")

            // レコードが生成されていなかったら、新規で挿入
            if (rs == null || !rs.next()){
                sql.execute("INSERT INTO hour_table " +
                        "(item_id, open, high, low, close, year, month, day, hour, date, volume) " +
                        "VALUES ('${item}', ${nowPrice}, ${nowPrice}, ${nowPrice}, ${nowPrice}, " +
                        "${year}, ${month}, ${day}, ${hour}, now(), ${volume})")

                hourlyCandleCache.remove(item)
                sql.close()
                return@addJob
            }

            rs.close()
            sql.close()

            //価格情報の更新
            sql.execute("UPDATE hour_table SET " +
                    "high=$high,low=$low,close=$nowPrice,volume=volume+${volume} where " +
                    "item_id='${item}' and year=$year and month=$month and day=$day and hour=$hour")

            hourlyCandleCache[item] = Pair(series,data.second)
        }
    }

    private fun writeCSV(item:String, tf:String){

        Market.addJob {sql ->
            val id = Market.getItemNumber(item)
            val rs = sql.query("SELECT date,open,high,low,close,volume from ${tf}_table where item_id='$item'")?:return@addJob

            try {

                val folder = File(instance.dataFolder.path+"/"+tf)
                if (!folder.exists()){
                    folder.mkdir()
                }

                val csv = File(instance.dataFolder.path+"/$tf/$id.csv")
                val writer = FileWriter(csv)
                writer.write("date,open,high,low,close,volume\n")

                while (rs.next()){
                    val date = rs.getTimestamp("date")
                    val open = rs.getDouble("open")
                    val high = rs.getDouble("high")
                    val low = rs.getDouble("low")
                    val close = rs.getDouble("close")
                    val volume = rs.getInt("volume")
                    writer.write("${SimpleDateFormat("yyyy-MM-dd HH:00:00").format(date)}," +
                            "${open},${high},${low},${close},${volume}\n")
                }

                rs.close()
                sql.close()
                writer.close()
            }catch (e:Exception){
                Bukkit.getLogger().warning(e.message)
            }
        }
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