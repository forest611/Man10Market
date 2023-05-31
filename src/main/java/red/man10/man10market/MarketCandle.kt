package red.man10.man10market

import org.bukkit.Bukkit
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object MarketCandle {


    private val hourlyCandleCache = ConcurrentHashMap<String,Pair<MarketData.MarketSeries,Date>>()
    private val dailyCandleCache = ConcurrentHashMap<String,Pair<MarketData.MarketSeries,Date>>()
    private val timer = Timer()

    fun schedule(){
        timer.schedule(
            object : TimerTask(){
                override fun run() {
                    Market.getItemIndex().forEach {
                        saveHour(it)
                        saveDay(it)
                        writeCSV(it,"hour")
                        writeCSV(it,"day")
                    }
                }
            },0,1000*60*5
        )

    }

    //一時間足のデータをDBに登録
    fun saveHour(item: String, volume: Int = 0){

        val nowPrice = Market.getPrice(item).bid
        val data = hourlyCandleCache[item]?: Pair(
            MarketData.MarketSeries(
                nowPrice,
                nowPrice,
                nowPrice,
                nowPrice
            ), Date()
        )
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

    //日足のデータをDBに登録
    fun saveDay(item: String, volume: Int = 0){

        val nowPrice = Market.getPrice(item).bid
        val data = dailyCandleCache[item]?: Pair(
            MarketData.MarketSeries(
                nowPrice,
                nowPrice,
                nowPrice,
                nowPrice
            ), Date()
        )
        val series = data.first

        val last = Calendar.getInstance()
        last.time = data.second

        val year = last.get(Calendar.YEAR)
        val month = last.get(Calendar.MONTH)+1
        val day = last.get(Calendar.DAY_OF_MONTH)

        val high = max(data.first.high,nowPrice)
        val low = min(data.first.low,nowPrice)

        series.high = high
        series.low = low

        Market.addJob {sql ->

            val rs = sql.query("SELECT * from day_table where " +
                    "item_id='${item}' and year=$year and month=$month and day=$day")

            // レコードが生成されていなかったら、新規で挿入
            if (rs == null || !rs.next()){
                sql.execute("INSERT INTO day_table " +
                        "(item_id, open, high, low, close, year, month,day, date, volume) " +
                        "VALUES ('${item}', ${nowPrice}, ${nowPrice}, ${nowPrice}, ${nowPrice}, " +
                        "${year}, ${month}, ${day}, now(), ${volume})")

                dailyCandleCache.remove(item)
                sql.close()
                return@addJob
            }

            rs.close()
            sql.close()

            //価格情報の更新
            sql.execute("UPDATE day_table SET " +
                    "high=$high,low=$low,close=$nowPrice,volume=volume+${volume} where " +
                    "item_id='${item}' and year=$year and month=$month and day=$day")

            dailyCandleCache[item] = Pair(series,data.second)
        }
    }

    private fun writeCSV(item:String, tf:String){

        Market.addJob {sql ->
            val id = Market.getItemNumber(item)
            val rs = sql.query("SELECT date,open,high,low,close,volume from ${tf}_table where item_id='$item' order by date desc LIMIT 200")?:return@addJob

            try {

                val folder = File(Man10Market.instance.dataFolder.path+"/"+tf)
                if (!folder.exists()){
                    folder.mkdir()
                }

                val csv = File(Man10Market.instance.dataFolder.path+"/$tf/$id.csv")
                val writer = FileWriter(csv)
                writer.write("date,open,high,low,close,volume\n")

                val tempList = mutableListOf<String>()

                while (rs.next()){
                    val date = rs.getTimestamp("date")
                    val open = rs.getDouble("open")
                    val high = rs.getDouble("high")
                    val low = rs.getDouble("low")
                    val close = rs.getDouble("close")
                    val volume = rs.getInt("volume")
                    tempList.add("${SimpleDateFormat("yyyy-MM-dd HH:00:00").format(date)}," +
                            "${open},${high},${low},${close},${volume}\n")
                }

                tempList.reverse()
                tempList.forEach { writer.write(it) }

                rs.close()
                sql.close()
                writer.close()
            }catch (e:Exception){
                Bukkit.getLogger().warning(e.message)
            }
        }
    }
}