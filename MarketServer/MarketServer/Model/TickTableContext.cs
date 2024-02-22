using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class TickTable
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int id { get; set; }
    public string item_id { get; set; }
    [Column(TypeName = "datetime")]
    [DatabaseGenerated(DatabaseGeneratedOption.Computed)]
    public DateTime date { get; set; } = DateTime.Now;
    public double? bid { get; set; }
    public double? ask { get; set; }
    public int? volume { get; set; }
}

public class TickTableContext : BaseContext
{
    public DbSet<TickTable> tick_table { get; set; }
}