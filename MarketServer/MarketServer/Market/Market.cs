using System.Collections.Concurrent;

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

    public static bool MarketBuy(Player player,Item item)
    {
        
    }

    public static bool MarketSell()
    {
        
    }

    public static bool OrderBuy(Player player,Item item,double price,int lot)
    {
        var tcs = new TaskCompletionSource<bool>();
        TransactionQueue.Add(() =>
        {
            var ret = 
            Order.AddNewOrder(player, item, true, price, lot);
        });
    }

    public static bool OrderSell()
    {
        
    }

    public static async Task<bool> DeleteOrder(int id)
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
            tcs.SetResult(order.Delete());
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