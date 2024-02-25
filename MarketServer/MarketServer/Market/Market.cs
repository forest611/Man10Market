using System.Collections.Concurrent;
using MarketServer.Data;
using MarketServer.Utility;

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

    public static async Task<List<TradeResult>> MarketBuy(Player player,Item item,int lot)
    {
        var tcs = new TaskCompletionSource<List<TradeResult>>();
        TransactionQueue.Add(() =>
        {
            var result = Order.MarketBuy(player, item, lot);
            tcs.SetResult(result);
        });
        return await tcs.Task;
    }

    public static async Task<List<TradeResult>> MarketSell(Player player,Item item,int lot)
    {
        var tcs = new TaskCompletionSource<List<TradeResult>>();
        TransactionQueue.Add(() =>
        {
            var result = Order.MarketSell(player, item, lot);
            tcs.SetResult(result);
        });
        return await tcs.Task;
    }

    public static async Task<TradeResultCode> OrderBuy(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<TradeResultCode>();
        TransactionQueue.Add(() =>
        {
            tcs.SetResult(Order.OrderBuy(player,item,price,lot));
        });
        return await tcs.Task;
    }

    public static async Task<TradeResultCode> OrderSell(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<TradeResultCode>();
        TransactionQueue.Add(() =>
        {
            tcs.SetResult(Order.OrderSell(player,item,price,lot));
        });
        return await tcs.Task;
    }

    public static async Task<TradeResultCode> CancelOrder(int id)
    {
        var tcs = new TaskCompletionSource<TradeResultCode>();
        TransactionQueue.Add(() =>
        {
            var order = Order.GetFromId(id);
            if (order.IsEmpty())
            {
                tcs.SetResult(TradeResultCode.OrderNotFound);
                return;
            }
            var canDelete = order.Delete();
            //注文の削除
            if (!canDelete)
            {
                tcs.SetResult(TradeResultCode.OrderNotFound);
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
            tcs.SetResult(TradeResultCode.Success);
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

