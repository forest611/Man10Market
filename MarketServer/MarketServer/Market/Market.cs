using System.Collections.Concurrent;
using MarketServer.data;

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

    public static async Task<bool> MarketBuy(Player player,Item item,int lot)
    {
        var tcs = new TaskCompletionSource<bool>();
        TransactionQueue.Add(() =>
        {
            var remainingLot = lot;
            var itemBank = ItemBank.GetItemBank(player, item);

            while (remainingLot>0)
            {
                var order = Order.GetMinSellOrder(item);

                if (order==null)
                {
                    tcs.SetResult(true);
                    return;
                }

                var executeLot = order.GetExecuteLot(remainingLot);
                var withdrawAmount = order.Price * executeLot;
                var canWithdraw = Bank.Bank.Withdraw(player, withdrawAmount, "Man10MarketMarketBuy", "マーケット成行買い").Result;
                if (!canWithdraw)
                {
                    tcs.SetResult(false);
                    return;
                }
                _ = order.ExecuteOrder(executeLot);
                remainingLot -= executeLot;
                var canAdd = itemBank.Add(executeLot).Result;
                if (!canAdd){}
                {
                    //TODO:ログ
                    return;
                }
            }
            tcs.SetResult(true);
        });
        return await tcs.Task;
    }

    public static async Task<bool> MarketSell(Player player,Item item,int lot)
    {
        var tcs = new TaskCompletionSource<bool>();
        TransactionQueue.Add((() =>
        {
            var remainingLot = lot;
            var itemBank = ItemBank.GetItemBank(player, item);

            while (remainingLot>0)
            {
                var order = Order.GetMaxBuyOrder(item);

                if (order==null)
                {
                    tcs.SetResult(true);
                    return;
                }

                var executeLot = order.GetExecuteLot(remainingLot);
                var canTake = itemBank.Take(executeLot).Result;
                if (!canTake)
                {
                    tcs.SetResult(false);
                    return;
                }

                _ = order.ExecuteOrder(executeLot);
                remainingLot -= executeLot;
                var depositAmount = order.Price * executeLot;
                var canDeposit = Bank.Bank.Deposit(player, depositAmount, "Man10MarketMarketSell", "マーケット成行売り").Result;
                if (!canDeposit)
                {
                    //TODO:ログ
                }
            }
            tcs.SetResult(true);
        }));

        return await tcs.Task;
    }

    public static async Task<bool> OrderBuy(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<bool>();
        TransactionQueue.Add(() =>
        {
            var requireAmount = price * lot;
            var canWithdraw = Bank.Bank.Withdraw(player,requireAmount , "Man10MarketOrderBuy", "マーケット指値買い").Result;
            if (!canWithdraw)
            {
                tcs.SetResult(false);
                return;
            }
            Order.AddNewOrder(player, item, true, price, lot);
        });
        return await tcs.Task;
    }

    public static async Task<bool> OrderSell(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<bool>();
        TransactionQueue.Add(() =>
        {
            var itemBank = ItemBank.GetItemBank(player, item);
            var canTake = itemBank.Take(lot).Result;
            if (!canTake)
            {   
                tcs.SetResult(false);
                return;
            }
            Order.AddNewOrder(player, item, false, price, lot);
        });
        return await tcs.Task;
    }

    public static async Task<bool> CancelOrder(int id)
    {
        var tcs = new TaskCompletionSource<bool>();
        TransactionQueue.Add(() =>
        {
            var order = Order.GetFromId(id);
            if (order == null)
            {
                tcs.SetResult(false);
                return;
            }
            var canDelete = order.Delete();
            //注文の削除
            if (!canDelete)
            {
                tcs.SetResult(false);
            }

            //買い注文の場合は返金
            if (order.Buy)
            {
                var amount = order.Price * order.Lot;
                var canDeposit = Bank.Bank.Deposit(order.OrderPlayer, amount, "CancelMarketOrderBuy", "マーケット指値買いキャンセル").Result;
                if (!canDeposit)
                {
                    //TODO:失敗ログを残す
                }
                tcs.SetResult(true);
            }

            //売り注文の場合はアイテムを返す
            if (order.Sell)
            {
                var itemBank = ItemBank.GetItemBank(order.OrderPlayer, order.Item);
                var added = itemBank.Add(order.Lot).Result;
                if (!added)
                {
                    //TODO:失敗ログを残す
                }
                tcs.SetResult(true);
            }

            //TODO:何かしら
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