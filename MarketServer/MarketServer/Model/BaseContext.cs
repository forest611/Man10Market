using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class BaseContext : DbContext
{
    private static string? Host { get; set; }
    private static string? Port { get; set; }
    private static string? Pass { get; set; }
    private static string? User { get; set; }
    private static string? DatabaseName { get; set; }


    public static void LoadConfig(IConfiguration config)
    {
        Host = config["Database:Host"];
        Port = config["Database:Port"];
        Pass = config["Database:Pass"];
        User = config["Database:User"];
        DatabaseName = config["Database:DatabaseName"];
        Console.WriteLine("データベースのConfigを読み込みました");
        TestConnect();
    }

    public static void TestConnect()
    {
        Console.WriteLine("データベースの接続テストを行います。");
        bool canConnect = false;
        try
        {
            canConnect = new BaseContext().Database.CanConnect();
        }
        catch (Exception e)
        {
            canConnect = false;
            Console.WriteLine(e);
        }
        finally
        {
            Console.WriteLine($"データベースの接続:{canConnect}");
        }
    }
    
    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        var connectionString = $"server='{Host}';port='{Port}';user='{User}';password='{Pass}';Database='{DatabaseName}'";
        var serverVersion = new MySqlServerVersion(new Version(8, 0, 30));
        optionsBuilder.UseMySql(connectionString, serverVersion);
        // .UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking);
    }
}