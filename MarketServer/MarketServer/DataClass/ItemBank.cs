using System.Collections.Concurrent;
using MarketServer.Model;

namespace MarketServer.DataClass;

/// <summary>
/// ユーザーとアイテムのItemBankの管理
/// </summary>
public class ItemBank
{
    private Player Player { get; }
    private Item Item { get; }

    public ItemBank(Player player, Item item)
    {
        Player = player;
        Item = item;
    }

    public int GetAmount()
    {
        return 0;
    }

    public async Task<bool> Add(int amount)
    {
        var tcs =  new TaskCompletionSource<bool>();
        
        
        TransactionQueue.Add(context =>
        {
            
            tcs.SetResult(false);
        });
        
        return await tcs.Task;
    }

    public bool Take(int amount)
    {
        return false;
    }
    
    static ItemBank()
    {
        Console.WriteLine("ItemBankクラスのロード");
        Task.Run(ItemBankQueue);
    }

    private static readonly BlockingCollection<Action<ItemIndexContext>> TransactionQueue = new();

    private static void ItemBankQueue()
    {
        Console.WriteLine("アイテムバンクキュー起動");

        var context = new ItemIndexContext();

        while (TransactionQueue.TryTake(out var job, Timeout.Infinite))
        {
            try
            {
                job?.Invoke(context);
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        }
    }
    
    
    // トランザクションの結果を取得するためのサンプル
    // public static async Task<double> SyncGetBalance(string uuid)
    // {
    //     var tcs = new TaskCompletionSource<double>();
    //     
    //     GetBalance(uuid, r =>
    //     {
    //         tcs.SetResult(r);
    //     });
    //
    //     return await tcs.Task;
    // }
    
}