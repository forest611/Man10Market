package red.man10.man10market

import org.bukkit.entity.Player

object Util {

    const val prefix = "§4§l[§2§lMarket§4§l]§f§r"

    fun msg(p: Player?, msg: String) {
        p?.sendMessage(prefix + msg)
    }

    fun format(amount: Double, digit: Int = 0): String {
        return String.format("%,.${digit}f", amount)
    }
}