using System.Collections.Concurrent;
using MarketServer.Data;
using MarketServer.Model;
using MarketServer.Utility;

namespace MarketServer.ItemBank;

/// <summary>
/// ユーザーとアイテムのItemBankの管理
/// </summary>
public class ItemBank
{
    private Player Player { get; }
    private Item Item { get; }
    
    private static readonly ConcurrentDictionary<(Player, Item), ItemBank> ItemBankDic = new();
    private static readonly BlockingCollection<Action<ItemStorageContext>> TransactionQueue = new();
    
    /// <summary>
    /// コンストラクタ
    /// </summary>
    /// <param name="player"></param>
    /// <param name="item"></param>
    private ItemBank(Player player, Item item)
    {
        Player = player;
        Item = item;
    }
    /// <summary>
    /// 指定ユーザーのItemBankをアクセスするにはここから行う
    /// </summary>
    /// <param name="player"></param>
    /// <param name="item"></param>
    /// <returns>ItemBankのインスタンス</returns>
    public static ItemBank GetItemBank(Player player, Item item)
    {
        if (ItemBankDic.ContainsKey((player, item))) return ItemBankDic[(player, item)];
        var itemBank = new ItemBank(player, item);
        ItemBankDic[(player, item)] = itemBank;
        return itemBank;
    }
    
    /// <summary>
    /// 新規ItemBankレコード作成
    /// </summary>
    /// <param name="context"></param>
    private void Create(ItemStorageContext context)
    {
        var newRecord = new ItemStorage
        {
            amount = 0,
            item_id = Item.Id,
            item_key = Item.Name,
            player = Player.Name,
            uuid = Player.Uuid,
            time = DateTime.Now
        };

        context.item_storage.Add(newRecord);
        context.SaveChanges();
        
        Logger.StorageLog(Item,null,Player,StorageActionType.CreateStorage,0,0,new Location());
    }

    public int GetAmount()
    {
        var tcs = new TaskCompletionSource<int>();
        
        TransactionQueue.Add(context =>
        {
            var record = GetRecord(context);

            //レコードが存在しない
            if (record == null)
            {
                Create(context);
                tcs.SetResult(0);
                return;
            }

            //存在する
            tcs.SetResult(record.amount);
        });
        
        return 0;
    }

    public async Task<bool> Add(int amount)
    {
        //0以下の場合はスルー
        if (amount <= 0)
        {
            return false;
        }
        
        var tcs =  new TaskCompletionSource<bool>();
        
        TransactionQueue.Add(context =>
        {
            var record = GetRecord(context);

            //レコードが存在しない場合
            if (record == null)
            {
                Create(context);
            }

            record!.amount += amount;
            context.SaveChanges();
            Logger.StorageLog(Item,null,Player,StorageActionType.AddItem,0,0,new Location());
            tcs.SetResult(true);
        });
        
        return await tcs.Task;
    }

    /// <summary>
    /// 取出し
    /// </summary>
    /// <param name="amount"></param>
    /// <returns></returns>
    public async Task<bool> Take(int amount)
    {
        //0以下の場合はスルー
        if (amount <= 0)
        {
            return false;
        }
        
        var tcs =  new TaskCompletionSource<bool>();
        
        TransactionQueue.Add(context =>
        {
            var record = GetRecord(context);

            //レコードが存在しない場合
            if (record == null)
            {
                Create(context);
            }

            //個数が足りない
            if (record!.amount < amount)
            {
                tcs.SetResult(false);
                return;
            }
            
            record!.amount -= amount;
            context.SaveChanges();
            Logger.StorageLog(Item,null,Player,StorageActionType.AddItem,0,0,new Location());
            tcs.SetResult(true);
        });
        
        return await tcs.Task;
    }

    /// <summary>
    /// DBからアイテムバンクのレコードを取得する
    /// </summary>
    /// <param name="context"></param>
    /// <returns>レコード</returns>
    private ItemStorage? GetRecord(ItemStorageContext context) => context.item_storage.FirstOrDefault(c => c.uuid == Player.Uuid && c.item_id == Item.Id);
    
    static ItemBank()
    {
        Console.WriteLine("ItemBankクラスのロード");
        Task.Run(ItemBankQueue);
    }
    
    private static void ItemBankQueue()
    {
        Console.WriteLine("アイテムバンクキュー起動");

        var context = new ItemStorageContext();

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
    
}