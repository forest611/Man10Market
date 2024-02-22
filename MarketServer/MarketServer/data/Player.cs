namespace MarketServer.data;

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
}