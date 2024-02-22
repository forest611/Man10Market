using MarketServer.Model;
using static MarketServer.Market.Item;

namespace MarketServer.Market;

/// <summary>
/// 指値注文クラス
/// 基本的にどの関数もスレッドセーフではない
/// </summary>
public class Order
{

    //排他使用する
    private static readonly OrderTableContext Context = new();
    
    public int Id { get; set; }
    public Player OrderPlayer { get; }
    public Item Item { get; }
    public double Price { get; }
    public bool Buy { get; }
    public bool Sell { get; }
    public int Lot { get; }


    private Order(int id,Player player, Item item, bool isBuy, double price, int lot)
    {
        if (price<0.0)
        {
            throw new ArgumentException("指値注文の金額がマイナス",nameof(price));
        }

        if (lot<0)
        {
            throw new ArgumentException("注文ロット数がマイナス",nameof(lot));
        }
        
        OrderPlayer = player;
        Price = price;
        Item = item;
        Buy = isBuy;
        Sell = !isBuy;
        Lot = lot;
    }

    public bool Delete()
    {
        var record = Context.order_table.FirstOrDefault(r => r.id == Id);
        if (record == null)
        {
            return false;
        }

        Context.order_table.Remove(record);
        return true;
    }

    /// <summary>
    /// 新規指値注文を投げる
    /// </summary>
    /// <param name="player">注文したプレイヤー</param>
    /// <param name="item">注文した内容</param>
    /// <param name="isBuy">買いならtrue 売りならfalse</param>
    /// <param name="price">金額</param>
    /// <param name="lot">個数</param>
    /// <exception cref="price">金額がマイナスの場合は例外</exception>
    /// <exception cref="lot">ロットがマイナスの場合は例外</exception>
    /// <returns>Orderインスタンス 注文失敗した場合はnull</returns>
    public static Order? AddNewOrder(Player player, Item item, bool isBuy, double price, int lot)
    {

        //不可能な注文をできないようにする
        switch (isBuy)
        {
            case true:
            {
                var minOrder = GetMinSellOrder(item);
                if (minOrder != null && price >= minOrder.Price)
                {
                    return null;
                }
                break;
            }
            case false:
            {
                var maxOrder = GetMaxBuyOrder(item);
                if (maxOrder != null && price <= maxOrder.Price)
                {
                    return null;
                }
                break;
            }
        }

        var record = new OrderTable
        {
            player = player.Name,
            uuid = player.Uuid,
            buy = isBuy,
            sell = !isBuy,
            price = price,
            lot = lot,
            entry_date = DateTime.Now
        };

        Context.order_table.Add(record);
        Context.SaveChanges();
        
        //TODO:idが変わってるか要チェック
        return new Order(record.id,player, item, isBuy, price, lot);
    }
    
    public static Order? GetFromId(int id)
    {
        var record = Context.order_table.FirstOrDefault(r => r.id == id);
        
        if (record==null)
        {
            return null;
        }

        var player = new Player(record.player, record.uuid);
        var item = GetItem(record.item_id);

        return new Order(record.id,player,item, false, record.price, record.lot);;
    }

    /// <summary>
    /// 売り指値注文のなかで一番安い注文を取得する
    /// </summary>
    /// <param name="item">指定アイテム</param>
    /// <returns></returns>
    public static Order? GetMinSellOrder(Item item)
    {
        var record = Context.order_table.Where(r => r.item_id == item.Name && r.sell).OrderByDescending(r => r.price)
            .FirstOrDefault();

        if (record==null)
        {
            return null;
        }

        var player = new Player(record.player, record.uuid);

        return new Order(record.id,player, item, false, record.price, record.lot);
    }

    /// <summary>
    /// 買い注文の中で一番高い注文を取得する
    /// </summary>
    /// <param name="item"></param>
    /// <returns></returns>
    public static Order? GetMaxBuyOrder(Item item)
    {
        var record = Context.order_table.Where(r => r.item_id == item.Name && r.buy).OrderBy(r => r.price)
            .FirstOrDefault();

        if (record==null)
        {
            return null;
        }

        var player = new Player(record.player, record.uuid);

        return new Order(record.id,player, item, true, record.price, record.lot);
    }
}