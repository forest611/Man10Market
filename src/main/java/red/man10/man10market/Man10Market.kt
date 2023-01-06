package red.man10.man10market

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10market.map.MappRenderer
import red.man10.man10market.stock.Stock

class Man10Market : JavaPlugin() {

    companion object {
        lateinit var instance: Man10Market
        lateinit var bankAPI: BankAPI

        var isMarketOpen = false
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
    }

    override fun onDisable() {
        // Plugin shutdown logic
        Market.interruptTransactionQueue()
        saveMarketConfig()
    }

    fun loadMarketConfig() {

        reloadConfig()

        isMarketOpen = config.getBoolean("MarketOpen", false)
    }

    fun saveMarketConfig() {

        config.set("MarketOpen", isMarketOpen)

        saveConfig()
    }
}