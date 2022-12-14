package red.man10.man10market.menu

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10itembank.menu.MenuFramework
import red.man10.man10market.Man10Market
import red.man10.man10market.Market
import red.man10.man10market.Util

class BuyMenu(p: Player, item: String) : MenuFramework(p, 9, "購入する") {

    private val tradeLot = arrayOf(1, 2, 4, 8, 16, 32, 64)

    init {

        val data = ItemBankAPI.getItemData(item)

        val infoButton = Button(Material.LIME_STAINED_GLASS_PANE)
        setButton(infoButton, 0)
        setButton(infoButton, 8)

        Market.getOrderList(item) { order ->

            val sells = order?.filter { o -> o.sell }

            if (sells.isNullOrEmpty()) {
                Util.msg(p, "§c§l現在のこのアイテムの注文がありません")
                Bukkit.getScheduler().runTask(Man10Market.instance, Runnable { p.closeInventory() })
                return@getOrderList
            }

            tradeLot.forEach {

                val button = Button(data!!.item!!.type)
                if (data.item!!.hasItemMeta() && data.item!!.itemMeta.hasCustomModelData()) {
                    button.cmd(data.item!!.itemMeta.customModelData)
                }

                button.title("§e§l${it}個購入する (推定必要金額${Util.format(getRequirePrice(it, sells))}円)")

                val icon = button.icon()
                icon.amount = it

                button.setClickAction { _ ->
                    Market.sendMarketBuy(p.uniqueId, item, it, true)
                }

                Bukkit.getScheduler().runTask(Man10Market.instance, Runnable { menu.addItem(icon) })
            }
        }
    }

    private fun getRequirePrice(lot: Int, list: List<Market.OrderData>): Double {
        //残り個数
        var remainAmount = lot
        var requirePrice = 0.0

        for (order in list) {

            if (remainAmount <= 0)
                return requirePrice

            val trade = if (order.lot > remainAmount) remainAmount else order.lot

            requirePrice += trade * order.price
            remainAmount -= trade

        }

        return requirePrice
    }

}