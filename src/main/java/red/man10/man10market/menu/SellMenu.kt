package red.man10.man10market.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10itembank.ItemData
import red.man10.man10itembank.menu.MenuFramework
import red.man10.man10market.Market

class SellMenu(p: Player) : MenuFramework(p, 54, "§c§lアイテムを売却する") {

    init {
        val putButton = Button(Material.RED_STAINED_GLASS_PANE)
        putButton.title("§c§l全て売却")
        putButton.lore(mutableListOf("§c売却に失敗したアイテムは/mibに保存されます"))

        putButton.setClickAction { e ->
            sellItems(p, e.inventory)
        }

        arrayOf(45, 46, 47, 48, 49, 50, 51, 52, 53).forEach { setButton(putButton, it) }

        setCloseListener { e ->
            sellItems(p, e.inventory)
        }
    }

    private fun sellItems(p: Player, menu: Inventory) {

        val sellData = HashMap<String, Int>()

        for (i in 0 until 45) {

            val item = menu.getItem(i) ?: continue
            val data = ItemData.getItemData(item) ?: continue
            val amount = sellData[data.itemKey] ?: 0
            sellData[data.itemKey] = amount + item.amount
            item.amount = 0
        }

        sellData.forEach {
            ItemBankAPI.addItemAmount(p.uniqueId, p.uniqueId, it.key, it.value) { _ ->
                Market.sendMarketSell(p.uniqueId, it.key, it.value)
            }
        }

    }

}