using System.Collections.Concurrent;
using MarketServer.data;
using MarketServer.util;

namespace MarketServer.Market;

/// <summary>
/// mceの取引を処理するクラス
/// </summary>
public static class Market
{

    static Market()
    {
        Console.WriteLine("Marketクラスのロード");
        Task.Run(MarketTransactionQueue);
    }

    public static async Task<TradeResult> MarketBuy(Player player,Item item,int lot)
    {
        var tcs = new TaskCompletionSource<TradeResult>();
        TransactionQueue.Add(() =>
        {
            var remainingLot = lot;
            var itemBank = ItemBank.ItemBank.GetItemBank(player, item);

            while (remainingLot>0)
            {
                var order = Order.GetMinSellOrder(item);

                if (order==null)
                {
                    tcs.SetResult(TradeResult.OrderNotFound);
                    return;
                }

                var executeLot = order.GetExecuteLot(remainingLot);
                var withdrawAmount = order.Price * executeLot;
                var canWithdraw = Bank.Bank.Withdraw(player, withdrawAmount, "Man10MarketMarketBuy", "マーケット成行買い").Result;
                if (!canWithdraw)
                {
                    tcs.SetResult(TradeResult.FailedWithdraw);
                    return;
                }
                _ = order.ExecuteOrder(executeLot);
                remainingLot -= executeLot;
                var canAdd = itemBank.Add(executeLot).Result;
                if (!canAdd)
                {
                    //アイテムの追加に失敗したらログを残す
                    Logger.TradeLog(player,item,executeLot,order.Price,TradeType.FailedAddItem);
                    return;
                }
                Logger.TradeLog(player,item,executeLot,order.Price,TradeType.MarketBuy);
            }
            tcs.SetResult(TradeResult.Success);
        });
        return await tcs.Task;
    }

    public static async Task<TradeResult> MarketSell(Player player,Item item,int lot)
    {
        var tcs = new TaskCompletionSource<TradeResult>();
        TransactionQueue.Add((() =>
        {
            var remainingLot = lot;
            var itemBank = ItemBank.ItemBank.GetItemBank(player, item);

            while (remainingLot>0)
            {
                var order = Order.GetMaxBuyOrder(item);

                if (order==null)
                {
                    tcs.SetResult(TradeResult.OrderNotFound);
                    return;
                }

                var executeLot = order.GetExecuteLot(remainingLot);
                var canTake = itemBank.Take(executeLot).Result;
                if (!canTake)
                {
                    tcs.SetResult(TradeResult.FailedTakeItem);
                    return;
                }

                _ = order.ExecuteOrder(executeLot);
                remainingLot -= executeLot;
                var depositAmount = order.Price * executeLot;
                var canDeposit = Bank.Bank.Deposit(player, depositAmount, "Man10MarketMarketSell", "マーケット成行売り").Result;
                if (!canDeposit)
                {
                    //入金に失敗したらログを残す
                    Logger.TradeLog(player,item,executeLot,order.Price,TradeType.FailedDeposit);
                    return;
                }
                Logger.TradeLog(player,item,executeLot,order.Price,TradeType.MarketSell);
            }
            tcs.SetResult(TradeResult.Success);
        }));

        return await tcs.Task;
    }

    public static async Task<TradeResult> OrderBuy(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<TradeResult>();
        TransactionQueue.Add(() =>
        {
            if (!Order.CanOrder(item,price,true))
            {
                tcs.SetResult(TradeResult.IllegalPriceSetting);
                return;
            }
            
            var requireAmount = price * lot;
            var canWithdraw = Bank.Bank.Withdraw(player,requireAmount , "Man10MarketOrderBuy", "マーケット指値買い").Result;
            if (!canWithdraw)
            {
                tcs.SetResult(TradeResult.FailedWithdraw);
                return;
            }
            Order.AddNewOrder(player, item, true, price, lot);
            Logger.TradeLog(player,item,lot,price,TradeType.OrderBuy);
            tcs.SetResult(TradeResult.Success);
        });
        return await tcs.Task;
    }

    public static async Task<TradeResult> OrderSell(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<TradeResult>();
        TransactionQueue.Add(() =>
        {
            if (!Order.CanOrder(item,price,false))
            {
                tcs.SetResult(TradeResult.IllegalPriceSetting);
                return;
            }

            var itemBank = ItemBank.ItemBank.GetItemBank(player, item);
            var canTake = itemBank.Take(lot).Result;
            if (!canTake)
            {   
                tcs.SetResult(TradeResult.FailedTakeItem);
                return;
            }
            Order.AddNewOrder(player, item, false, price, lot);
            Logger.TradeLog(player,item,lot,price,TradeType.OrderSell);
            tcs.SetResult(TradeResult.Success);
        });
        return await tcs.Task;
    }

    public static async Task<TradeResult> CancelOrder(int id)
    {
        var tcs = new TaskCompletionSource<TradeResult>();
        TransactionQueue.Add(() =>
        {
            var order = Order.GetFromId(id);
            if (order == null)
            {
                tcs.SetResult(TradeResult.OrderNotFound);
                return;
            }
            var canDelete = order.Delete();
            //注文の削除
            if (!canDelete)
            {
                tcs.SetResult(TradeResult.OrderNotFound);
                return;
            }

            //買い注文の場合は返金
            if (order.Buy)
            {
                var amount = order.Price * order.Lot;
                var canDeposit = Bank.Bank.Deposit(order.OrderPlayer, amount, "CancelMarketOrderBuy", "マーケット指値買いキャンセル").Result;
                if (!canDeposit)
                {
                    Logger.TradeLog(order.OrderPlayer,order.Item,order.Lot,order.Price,TradeType.FailedDeposit);
                }
            }

            //売り注文の場合は返品
            if (order.Sell)
            {
                var itemBank = ItemBank.ItemBank.GetItemBank(order.OrderPlayer, order.Item);
                var added = itemBank.Add(order.Lot).Result;
                if (!added)
                {
                    Logger.TradeLog(order.OrderPlayer,order.Item,order.Lot,order.Price,TradeType.FailedAddItem);
                }
            }
            
            Logger.TradeLog(order.OrderPlayer,order.Item,order.Lot,order.Price,TradeType.CancelOrder);
            tcs.SetResult(TradeResult.Success);
        });
        return await tcs.Task;
    }
    
    //取引処理を動かすトランザクション
    private static readonly BlockingCollection<Action> TransactionQueue = new();
    
    private static void MarketTransactionQueue()
    {
        Console.WriteLine("マーケットキューの起動");

        while (TransactionQueue.TryTake(out var job ,Timeout.Infinite))
        {
            try
            {
                job?.Invoke();
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        }
    }
}

public enum TradeResult
{
    Success,
    OrderNotFound,
    FailedWithdraw,
    FailedTakeItem,
    IllegalPriceSetting
}