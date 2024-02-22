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