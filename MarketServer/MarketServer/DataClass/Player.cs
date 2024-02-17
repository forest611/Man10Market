namespace MarketServer.DataClass;

/// <summary>
/// プレイヤー 資産情報などを探すキーとして扱う
/// </summary>
public class Player
{
    public string Name { get; }
    public string Uuid { get; }
    
    public Player(string name, string uuid)
    {
        Name = name;
        Uuid = uuid;
    }

    public Player(string uuid)
    {
        Uuid = uuid;
        Name = GetNameFromUuid();
        //nameをDBから取得する
    }

    private string GetNameFromUuid()
    {
        //TODO:
        return "";
    }
}