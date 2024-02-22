using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class OrderTable
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public string item_id { get; set; }
    public double price { get; set; }
    public bool buy { get; set; }
    public bool sell { get; set; }
    public int lot { get; set; }
    public DateTime entry_date { get; set; }
    
}

public class OrderTableContext : BaseContext
{
    public DbSet<OrderTable> order_table { get; set; }
}