package red.man10.man10market

import org.bukkit.entity.Player

object Util {

    const val prefix = ""

    fun msg(p:Player?,msg:String){
        p?.sendMessage(prefix+msg)
    }

}