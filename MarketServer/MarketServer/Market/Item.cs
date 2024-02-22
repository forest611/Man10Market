namespace MarketServer.Market;

/// <summary>
/// 取引アイテムの情報を管理するクラス
/// </summary>
public class Item
{
    public int Id { get;  }
    public string Name { get; }

    private Item(int id, string name)
    {
        Id = id;
        Name = name;
    }

    static Item()
    {
        LoadItem();
    }

    private static void LoadItem()
    {
        
    }
}