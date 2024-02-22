using System.Collections.Concurrent;
using MarketServer.data;
using MarketServer.Model;

namespace MarketServer.util;

public static class Logger
{
    private static readonly BlockingCollection<Action> LogQueue = new();
    private static readonly ExecutionLogContext ExeLogContext = new();
    private static readonly StorageLogContext StorageLogContext = new();

    static Logger()
    {
        Task.Run(InsertMethod);
    }
    
    public static void TradeLog(Player player, Item item, int lot, double price, TradeType type)
    {
        var record = new ExecutionLog
        {
            player = player.Name,
            uuid = player.Uuid,
            item_id = item.Name,
            amount = lot,
            price = price,
            exe_type = TradeTypeToString(type)
        };

        LogQueue.Add(() =>
        {
            ExeLogContext.Add(record);
            ExeLogContext.SaveChanges();
        });
    }

    public static void StorageLog(Player? order, Player target, StorageActionType type, int editAmount, int storageAmount,Location location)
    {
        var record = new StorageLog
        {
            order_player = order?.Name ?? "null",
            order_uuid = order?.Uuid ?? "null",
            target_player = target.Name,
            target_uuid = target.Uuid,
            action = type.ToString(),
            edit_amount = editAmount,
            storage_amount = storageAmount,
            world = location.World,
            x = location.X,
            y = location.Y,
            z = location.Z
            
        };
        
        LogQueue.Add(() =>
        {
            StorageLogContext.Add(record);
            StorageLogContext.SaveChanges();
        });
    }

    private static void InsertMethod()
    {
        Console.WriteLine("ログキュー起動");

        while (LogQueue.TryTake(out var log , Timeout.Infinite))
        {
            try
            {
                log?.Invoke();
            }
            catch (Exception e)
            {
                Console.WriteLine(e);
            }
        }
    }

    private static string TradeTypeToString(TradeType type)
    {
        return type switch
        {
            TradeType.MarketBuy => "成行買い",
            TradeType.MarketSell => "成行売り",
            TradeType.OrderBuy => "指値買い",
            TradeType.OrderSell => "指値売り",
            TradeType.ModifyOrder => "指値調整",
            TradeType.CancelOrder => "指値取り消し",
            TradeType.FailedDeposit => "入金失敗",
            TradeType.FailedAddItem => "アイテム追加失敗",
            _ => "不明"
        };
    }
}

public enum TradeType
{
    MarketBuy,
    MarketSell,
    OrderBuy,
    OrderSell,
    ModifyOrder,
    CancelOrder,
    FailedDeposit,
    FailedAddItem,
}

public enum StorageActionType
{
    CreateStorage,
    AddItem,
    TakeItem
}