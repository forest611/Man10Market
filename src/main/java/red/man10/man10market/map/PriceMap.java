package red.man10.man10market.map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import red.man10.man10itembank.ItemBankAPI;
import red.man10.man10market.Man10Market;
import red.man10.man10market.Market;
import red.man10.man10market.Util;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceMap {


    private static final Map<String, Market.PriceData> priceCache = new HashMap<>();

    public static void getPriceMap(Player p, String item) {

        ItemStack map = MappRenderer.getMapItem(Man10Market.instance, "price:" + item);

        if (map == null) {
            p.sendMessage("Error Null");
            return;
        }

        p.getInventory().addItem(map);
    }

    public static void registerPriceMap() {

        Bukkit.getLogger().info("Priceマップ登録");

        try {

            List<String> items = Market.INSTANCE.getItemIndex();

            for (String item : items) {
                MappRenderer.draw("price:" + item, 600, (String key, int mapId, Graphics2D g) -> drawPrice(g, item));

                MappRenderer.displayTouchEvent("price:" + item, (key, mapId, player, x, y) -> {
                    player.performCommand("mce price " + item);
                    return true;
                });
            }

        } catch (Exception e) {
            Bukkit.getLogger().info(e.getMessage());
            System.out.println(e.getMessage());
        }

        Bukkit.getLogger().info("Priceマップ登録 完了");

    }


    //      現在値を表示
    static boolean drawPrice(Graphics2D g, String item) {

        Market.PriceData cache = priceCache.get(item);
        Market.PriceData price = Market.INSTANCE.getPrice(item);

        //価格変化がなかった場合は、更新をしない
        if (cache != null && cache.getPrice() == price.getPrice()){
            return false;
        }

        g.setColor(Color.GRAY);
        g.fillRect(0, 0, 128, 128);

        int id = ItemBankAPI.INSTANCE.getItemData(item).getId();

        MappDraw.drawImage(g,String.valueOf(id),64,20,64,64);

        g.setColor(Color.WHITE);

        int titleSize = 20;
        if (item.length() > 6) {
            titleSize = 12;
        }

        g.setFont(new Font("SansSerif", Font.BOLD, titleSize));

        MappDraw.drawShadowString(g, item, Color.WHITE, Color.BLACK, 5, 20);

        g.setFont(new Font("SansSerif", Font.BOLD, 20));

        Color col = Color.YELLOW;


        String strPrice = Util.INSTANCE.format(price.getPrice(), 0);


        g.setColor(col);
//        g.drawString(strPrice,10,50);

        if (price.getBid() == 0 || price.getAsk() == Double.MAX_VALUE) {
            strPrice = "仲直未定";
        }

        MappDraw.drawShadowString(g, strPrice, col, Color.BLACK, 10, 50);

        g.setColor(Color.GREEN);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));


        if (price.getAsk() == Double.MAX_VALUE) {
            MappDraw.drawShadowString(g, "売り注文なし", Color.GREEN, Color.black, 4, 80);
        } else {
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString("買:" + Util.INSTANCE.format(price.getAsk(), 0), 4, 80);
        }

        g.setColor(Color.RED);

        if (price.getBid() == 0) {
            //g.drawString("売り注文なし",4,80);
            MappDraw.drawShadowString(g, "買い注文なし", Color.RED, Color.black, 4, 100);
        } else {

            g.drawString("売:" + Util.INSTANCE.format(price.getBid(), 0), 4, 98);
        }

        return true;

    }


}
