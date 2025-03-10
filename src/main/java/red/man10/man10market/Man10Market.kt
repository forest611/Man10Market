package red.man10.man10market

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10market.assistant.Assistant
import red.man10.man10market.map.MappRenderer
import red.man10.man10market.stock.Stock

class Man10Market : JavaPlugin() {

    companion object {
        lateinit var instance: Man10Market
        lateinit var bankAPI: BankAPI

        var isMarketOpen = false
        var csvPath = ""

        private var apiKey = ""

        fun setupAssistant() {
            Assistant.setup(instance, apiKey)
        }
    }


    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        instance = this
        bankAPI = BankAPI(this)

        getCommand("mce")!!.setExecutor(Command)
        getCommand("mstock")!!.setExecutor(Stock)

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
}