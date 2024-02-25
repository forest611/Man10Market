using System.Collections;
using MarketServer.Data;
using MarketServer.Model;
using MarketServer.Utility;
using static MarketServer.Data.Item;

namespace MarketServer.Market;

/// <summary>
/// 指値注文クラス
/// 基本的にどの関数もスレッドセーフではない
/// </summary>
public class Order
{

    //排他使用する
    private static readonly OrderTableContext Context = new();
    private static Order EmptyOrder => new (0,Player.EmptyPlayer,EmptyItem,false,0,0);
    public int Id { get; }
    public Player OrderPlayer { get; }
    public Item Item { get; }
    public double Price { get; }
    public bool Buy { get; }
    public bool Sell { get; }
    public int Lot { get; private set; }


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

        Id = id;
        OrderPlayer = player;
        Price = price;
        Item = item;
        Buy = isBuy;
        Sell = !isBuy;
        Lot = lot;
    }

    /// <summary>
    /// 指定注文を成行で約定する
    /// </summary>
    /// <param name="player">約定ユーザー</param>
    /// <param name="requestLot">希望数</param>
    /// <returns>約定が成立した数</returns>
    private TradeResult ExecuteMarketOrder(Player player,int requestLot)
    {
        if (IsEmpty())
        {
            return TradeResult.OrderNotFound(Item,0,0);
        }

        var tradeLot = Lot > requestLot ? requestLot : Lot;
        var itemBank = ItemBank.ItemBank.GetItemBank(player, Item);

        switch (Buy)
        {
            //買い注文
            case true:
            {
                var withdrawAmount = Price * tradeLot;
                var canWithdraw = Bank.Bank.Withdraw(player, withdrawAmount, "Man10MarketMarketBuy", "マーケット成行買い").Result;
                if (!canWithdraw)
                {
                    return TradeResult.FailedWithdraw(Item,0,0);
                }
                var canAdd = itemBank.Add(tradeLot).Result;
                if (!canAdd)
                {
                    //出金後にアイテムの追加に失敗をした場合
                    Logger.TradeLog(player,Item,tradeLot,Price,TradeType.FailedAddItem);
                    break;
                }
                Logger.TradeLog(player,Item,tradeLot,Price,TradeType.MarketBuy);
                break;
            }

            //売り注文
            case false:
            {
                var canTake = itemBank.Take(tradeLot).Result;
                if (!canTake)
                {
                    return TradeResult.FailedTakeItem(Item,0,0);
                }
                var depositAmount = Price * tradeLot;
                var canDeposit = Bank.Bank.Deposit(player, depositAmount, "Man10MarketMarketSell", "マーケット成行売り").Result;
                if (!canDeposit)
                {
                    //アイテム取出し後に入金が失敗した場合
                    Logger.TradeLog(player,Item,tradeLot,Price,TradeType.FailedDeposit);
                    break;
                }
                Logger.TradeLog(player,Item,tradeLot,Price,TradeType.MarketSell);
                break;
            }
        }
        
        //取引後のロット数を保存
        Lot -= tradeLot;
        SaveChanges();
        
        return TradeResult.Success(Item,Price,tradeLot);
    }

    /// <summary>
    /// 約定されるロット数を取得
    /// </summary>
    /// <param name="lot"></param>
    /// <returns></returns>
    public int GetExecuteLot(int lot)
    {
        return Lot > lot ? lot : Lot;
    }
    
    /// <summary>
    /// DBから注文を消すときはこれを呼ぶ
    /// </summary>
    /// <returns></returns>
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

    public bool IsEmpty() => Id == 0;

    private void SaveChanges()
    {
        var record = Context.order_table.First(r => r.id == Id);

        if (Lot <= 0)
        {
            Delete();
            return;
        }
        
        record.price = Price;
        record.lot = Lot;
        Context.SaveChanges();
    }

    /// <summary>
    /// 指値買い注文
    /// </summary>
    /// <returns></returns>
    public static TradeResultCode OrderBuy(Player player, Item item, double price, int lot)
    {
        if (!CanOrder(item,price,true))
        {
            return TradeResultCode.IllegalPriceSetting;
        }
        var requireAmount = price * lot;
        var canWithdraw = Bank.Bank.Withdraw(player,requireAmount , "Man10MarketOrderBuy", "マーケット指値買い").Result;
        if (!canWithdraw)
        {
            return TradeResultCode.FailedWithdraw;
        }

        var record = new OrderTable
        {
            player = player.Name,
            uuid = player.Uuid,
            buy = true,
            sell = false,
            price = price,
            lot = lot,
            entry_date = DateTime.Now
        };

        Context.order_table.Add(record);
        Context.SaveChanges();
        Logger.TradeLog(player,item,lot,price,TradeType.OrderBuy);

        return TradeResultCode.Success;
    }

    /// <summary>
    /// 指値売り注文
    /// </summary>
    /// <param name="player"></param>
    /// <param name="item"></param>
    /// <param name="price"></param>
    /// <param name="lot"></param>
    /// <returns></returns>
    public static TradeResultCode OrderSell(Player player, Item item, double price, int lot)
    {
        if (!CanOrder(item,price,false))
        {
            return TradeResultCode.IllegalPriceSetting;
        }
        var itemBank = ItemBank.ItemBank.GetItemBank(player, item);
        var canTake = itemBank.Take(lot).Result;
        if (!canTake)
        {   
            return TradeResultCode.FailedTakeItem;
        }

        var record = new OrderTable
        {
            player = player.Name,
            uuid = player.Uuid,
            buy = false,
            sell = true,
            price = price,
            lot = lot,
            entry_date = DateTime.Now
        };

        Context.order_table.Add(record);
        Context.SaveChanges();
        Logger.TradeLog(player,item,lot,price,TradeType.OrderSell);
        
        return TradeResultCode.Success;        
    }

    private static bool CanOrder(Item item, double price, bool isBuy)
    {
        switch (isBuy)
        {
            case true:
            {
                var minOrder = GetMinSellOrder(item);
                if (!minOrder.IsEmpty() && price >= minOrder.Price)
                {
                    return false;
                }
                break;
            }
            case false:
            {
                var maxOrder = GetMaxBuyOrder(item);
                if (!maxOrder.IsEmpty() && price <= maxOrder.Price)
                {
                    return false;
                }
                break;
            }
        }

        return true;
    }
    
    public static Order GetFromId(int id)
    {
        var record = Context.order_table.FirstOrDefault(r => r.id == id);
        
        if (record==null)
        {
            return EmptyOrder;
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
    private static Order GetMinSellOrder(Item item)
    {
        var record = Context.order_table
            .Where(r => r.item_id == item.Name && r.sell)
            .OrderBy(r => r.price)
            .ThenBy(r => r.id)
            .FirstOrDefault();

        if (record==null)
        {
            return EmptyOrder;
        }

        var player = new Player(record.player, record.uuid);

        return new Order(record.id,player, item, false, record.price, record.lot);
    }

    /// <summary>
    /// 買い注文の中で一番高い注文を取得する
    /// </summary>
    /// <param name="item"></param>
    /// <returns></returns>
    private static Order GetMaxBuyOrder(Item item)
    {
        var record = Context.order_table
            .Where(r => r.item_id == item.Name && r.buy)
            .OrderByDescending(r => r.price)
            .ThenBy(r => r.id)
            .FirstOrDefault();

        if (record==null)
        {
            return EmptyOrder;
        }

        var player = new Player(record.player, record.uuid);

        return new Order(record.id,player, item, true, record.price, record.lot);
    }

    public static List<TradeResult> MarketBuy(Player player,Item item,int lot)
    {
        var resultList = new List<TradeResult>();
        var remainingLot = lot;

        //remainingLotが0になるまで成行注文を繰り返す
        while (remainingLot > 0)
        {
            var order = GetMinSellOrder(item);
            var result = order.ExecuteMarketOrder(player, remainingLot);
            resultList.Add(result);
            
            //所持金不足
            if (result.ResultCode == TradeResultCode.FailedWithdraw)
            {
                break;
            }

            //注文がなくなった
            if (result.ResultCode == TradeResultCode.OrderNotFound)
            {
                break;
            }
            
            remainingLot -= result.Lot;
        }
        return resultList;
    }

    
    public static List<TradeResult> MarketSell(Player player,Item item, int lot)
    {
        var resultList = new List<TradeResult>();
        var remainingLot = lot;

        while (remainingLot > 0)
        {
            var order = GetMaxBuyOrder(item);
            var result = order.ExecuteMarketOrder(player, remainingLot);
            resultList.Add(result);

            //アイテム不足
            if (result.ResultCode == TradeResultCode.FailedTakeItem)
            {
                break;
            }
            
            //注文がなくなった
            if (result.ResultCode == TradeResultCode.OrderNotFound)
            {
                break;
            }

            remainingLot -= result.Lot;
        }

        return resultList;
    }
}
