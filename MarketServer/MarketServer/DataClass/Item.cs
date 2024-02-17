namespace MarketServer.DataClass;

/// <summary>
/// 取引アイテムの情報を管理するクラス
/// </summary>
public class Item
{
    private int Id { get;  }
    private string Name { get; }

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