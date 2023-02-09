package red.man10.man10market.map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import red.man10.man10market.Man10Market;

import java.awt.*;

public class ChartMap {

    public static void getChartMap(Player p, String item) {

        ItemStack map = MappRenderer.getMapItem(Man10Market.instance, "chart:" + item);

        if (map == null) {
            p.sendMessage("Error Null");
            return;
        }

        p.getInventory().addItem(map);
    }

    public static void registerChartMap() {



    }


    //      チャート表示
    static void drawChart(Graphics2D g, String item) {



    }


}
