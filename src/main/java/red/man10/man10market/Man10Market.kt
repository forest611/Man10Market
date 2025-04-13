package red.man10.man10market

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10itembank.util.MySQLManager
import red.man10.man10market.assistant.Assistant
import red.man10.man10market.assistant.ConversationManager
import red.man10.man10market.map.MappRenderer
import red.man10.man10market.stock.Stock
import java.io.BufferedReader
import java.io.InputStreamReader

class Man10Market : JavaPlugin() {

    companion object {
        lateinit var instance: Man10Market
        lateinit var bankAPI: BankAPI

        var isMarketOpen = false
        var csvPath = ""

        private var apiKey = ""

        fun setupAssistant() {
            // アシスタントの初期化
            Assistant.setup(instance, apiKey)
            // 会話マネージャーの初期化
            ConversationManager.setup(instance)
        }
    }


    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        instance = this
        bankAPI = BankAPI(this)

        getCommand("mce")!!.setExecutor(Command)
        getCommand("mstock")!!.setExecutor(Stock)

        // テーブル初期化
        initializeTables()
        
        MappRenderer.setup(this)
        loadMarketConfig()
        MarketData.init()

        setupAssistant()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        Market.interruptTransactionQueue()
        saveMarketConfig()
    }

    fun loadMarketConfig() {

        reloadConfig()

        isMarketOpen = config.getBoolean("MarketOpen", false)
        csvPath = config.getString("CSVPath","")?:""
        apiKey = config.getString("OpenAIKey","")?:""
    }

    fun saveMarketConfig() {

        config.set("MarketOpen", isMarketOpen)

        saveConfig()
    }
    
    /**
     * SQLテーブルを初期化する
     */
    private fun initializeTables() {
        logger.info("テーブルの初期化を開始します...")
        
        try {
            // SQLファイルを読み込む
            val inputStream = getResource("sql/table.sql")
            if (inputStream == null) {
                logger.warning("table.sqlファイルが見つかりません")
                return
            }
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            val sqlBuilder = StringBuilder()
            var line: String?

            val mysql = MySQLManager(this, "Man10MarketInit")
            
            // SQLファイルの内容を読み込む
            while (reader.readLine().also { line = it } != null) {
                // コメント行スキップ
                if (line!!.trim().startsWith("--")) {
                    continue
                }
                sqlBuilder.append(line).append("\n")
                
                // SQLステートメントの終わりを検出したら実行
                if (line!!.trim().endsWith(";")) {
                    val sql = sqlBuilder.toString().trim()
                    if (sql.isNotEmpty()) {
                        mysql.execute(sql)
                    }
                    sqlBuilder.clear()
                }
            }
            
            reader.close()
            logger.info("テーブルの初期化が完了しました")
            
        } catch (e: Exception) {
            logger.severe("テーブルの初期化中にエラーが発生しました: ${e.message}")
            e.printStackTrace()
        }
    }
    
}