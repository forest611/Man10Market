using MarketServer.Model;

namespace MarketServer.Market;

/// <summary>
/// 取引アイテムの情報を管理するクラス
/// </summary>
public class Item
{
    public int Id { get;  }
    public string Name { get; }

    private static readonly HashSet<Item> ItemSet = new();

    private Item(int id, string name)
    {
        Id = id;
        Name = name;
    }

    static Item()
    {
        LoadItem();
    }

    public static Item GetItem(string name)
    {
        return ItemSet.FirstOrDefault(r => r.Name == name) ?? throw new KeyNotFoundException($"{name}というアイテムがない");
    }

    private static void LoadItem()
    {
        ItemSet.Clear();

        var context = new ItemIndexContext();
        
        

    }
}