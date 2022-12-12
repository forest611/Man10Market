package red.man10.man10market.menu

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import red.man10.man10itembank.ItemBankAPI
import red.man10.man10itembank.menu.MenuFramework
import red.man10.man10market.Market

class MainMenu(p:Player,page:Int) :MenuFramework(p,54,"Man10中央取引所"){

    init {

        setClickListener{it.isCancelled = true}
        val itemIndex = Market.getItemIndex()


        var inc = 0

        while (menu.getItem(44) == null){

            val index = inc+page*45
            inc++
            if (itemIndex.size<=index){
                break
            }

            val item = itemIndex[index]

            val itemData = ItemBankAPI.getItemData(item)?:continue
            val button = Button(itemData.item!!.type)

            button.title("§b${item}")
            if (itemData.item!!.hasItemMeta() && itemData.item!!.itemMeta.hasCustomModelData()){
                button.cmd(itemData.item!!.itemMeta.customModelData)
            }

            button.lore(mutableListOf("§a§l左クリック:購入","§c§l右クリック:売却","§b§lシフト左クリック:高度な取引"))

            button.setClickAction{e->

                val clicked = e.whoClicked as Player

                if (e.isShiftClick){
                    clicked.closeInventory()
                    clicked.performCommand("mce price $item")
                    return@setClickAction

                }

                if (e.isLeftClick){
                    BuyMenu(p,item).open()
                    return@setClickAction
                }

                if (e.isRightClick){
                    SellMenu(p).open()
                    return@setClickAction
                }

            }
            menu.addItem(button.icon())
        }

        //Back
        val back = Button(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
        back.title("")
        arrayOf(45,46,47,48,49,50,51,52,53).forEach { setButton(back,it) }

        //previous
        if (page!=0){
            val previous = Button(Material.RED_STAINED_GLASS_PANE)
            previous.title("前のページへ")
            previous.setClickAction{ MainMenu(p,page-1).open() }
            arrayOf(45,46,47).forEach { setButton(previous,it) }

        }

        //next
        if (menu.getItem(44) != null){
            val next = Button(Material.RED_STAINED_GLASS_PANE)
            next.title("次のページへ")
            next.setClickAction{ MainMenu(p,page+1).open() }
            arrayOf(51,52,53).forEach { setButton(next,it) }
        }
    }

}