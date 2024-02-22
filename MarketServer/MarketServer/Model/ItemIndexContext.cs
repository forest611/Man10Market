using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class ItemIndex
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int id { get; set; }
    public string item_key { get; set; }
    public string item_name { get; set; }
    public double price { get; set; }
    public double bid { get; set; }
    public double ask { get; set; }
    public double tick { get; set; }
    public DateTime time { get; set; }
    public bool disabled { get; set; }
    public string base64 { get; set; }
    
}

public class ItemIndexContext : BaseContext
{
    public DbSet<ItemIndex> item_index { get; set; }
}