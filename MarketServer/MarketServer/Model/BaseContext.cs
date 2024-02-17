using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class BaseContext : DbContext
{
    
    protected static string? Host { get; set; }
    protected static string? Port { get; set; }
    protected static string? Pass { get; set; }
    protected static string? User { get; set; }
    protected static string? DatabaseName { get; set; }


    public static void LoadConfig(IConfiguration config)
    {
        Host = config["Database:Host"];
        Port = config["Database:Port"];
        Pass = config["Database:Pass"];
        User = config["Database:User"];
        DatabaseName = config["Database:DatabaseName"];
    }
    
    protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
    {
        var connectionString = $"server='{Host}';port='{Port}';user='{User}';password='{Pass}';Database='{DatabaseName}'";
        var serverVersion = new MySqlServerVersion(new Version(8, 0, 30));
        optionsBuilder.UseMySql(connectionString, serverVersion);
        // .UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking);
    }
}