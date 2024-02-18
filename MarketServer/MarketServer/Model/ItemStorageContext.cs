using System.ComponentModel.DataAnnotations;
using MarketServer.DataClass;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;


public class ItemStorage
{
    [Key] 
    public int id { get; set; }

    public string player { get; set; }
    public string uuid { get; set; }
    public int item_id { get; set; }
    public string item_key { get; set; }
    public int amount { get; set; }
    public DateTime time { get; set; }
}

public class ItemStorageContext : BaseContext
{
    public DbSet<ItemStorage> item_storage { get; set; }
}