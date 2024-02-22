using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class DayTable
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int id { get; set; }
    public string item_id { get; set; }
    public double? open { get; set; }
    public double? high { get; set; }
    public double? low { get; set; }
    public double? close { get; set; }
    public int? year { get; set; }
    public int? month { get; set; }
    public int? day { get; set; }
    public DateTime? date { get; set; }
    public int? volume { get; set; }    
}

public class DayTableContext : BaseContext
{
    public DbSet<DayTable> day_table { get; set; }
}