package red.man10.man10market.stock

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10market.Man10Market.Companion.bankAPI
import red.man10.man10market.Util.format
import red.man10.man10market.Util.msg
import java.util.*

object Stock {

    fun registerStock(p:Player,owner:UUID,name:String){

    }

    fun issueStock(p:Player,name:String,amount:Int){

        ItemBankAPI.addItemAmount(p.uniqueId,p.uniqueId,name,amount){

        }

    }

    fun payDividend(p:Player,name:String,amount:Double){

        val shareholder = getShareholder(name)

        val totalStock = shareholder.sumOf { it.second }

        if (!bankAPI.withdraw(p.uniqueId,totalStock*amount,"PayDividend","配当金の支払い:${name}")){
            msg(p,"配当金を支払うために必要な金額は${format(totalStock*amount)}円です！")
            return
        }

        shareholder.forEach {
            bankAPI.deposit(it.first,it.second*amount,"PayDividend","配当金の支払い:${name}")
        }

        msg(p,"配当金支払い完了")
    }

    fun getIssuedStock(name:String):Int{

        return 0
    }


    //所有者と保有株数を取得
    fun getShareholder(name:String):List<Pair<UUID,Int>>{

        return emptyList()
    }

    fun createStockItem(title:String,lore:List<String>):ItemStack{

        val stockItem = ItemStack(Material.PAPER)
        val meta = stockItem.itemMeta
        meta.setCustomModelData(2)

        val fixLore = lore.toMutableList()

        fixLore.add("§c[この株券は所有者が§7${title}§cの株主であることを証する]")
        fixLore.add("§c[mibに保管することによって効力を発する]")

        meta.displayName(Component.text(title))
        meta.lore = lore

        stockItem.itemMeta = meta

        return stockItem

    }

}