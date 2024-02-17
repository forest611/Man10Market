using System.Collections.Concurrent;

namespace MarketServer.DataClass;

/// <summary>
/// ユーザーとアイテムのItemBankの管理
/// </summary>
public class ItemBank
{




    static ItemBank()
    {
        Console.WriteLine("ItemBankクラスのロード");
        Task.Run(ItemBankQueue);
    }

    private static readonly BlockingCollection<Action> TransactionQueue = new();

    private static void ItemBankQueue()
    {
        Console.WriteLine("アイテムバンクキュー起動");

        while (TransactionQueue.TryTake(out var job, Timeout.Infinite))
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