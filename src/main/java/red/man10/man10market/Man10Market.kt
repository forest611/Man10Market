package red.man10.man10market

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.BankAPI
import red.man10.man10market.map.MappRenderer

class Man10Market : JavaPlugin() {

    companion object{
        lateinit var instance : Man10Market
        lateinit var bankAPI: BankAPI
    }


    override fun onEnable() {
        // Plugin startup logic

        instance = this
        bankAPI = BankAPI(this)

        MappRenderer.setup(this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}