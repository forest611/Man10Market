using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class ExecutionLog
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int id { get; set; }
    public string player { get; set; }
    public string uuid { get; set; }
    public string item_id { get; set; }
    public int? amount { get; set; }
    public double? price { get; set; }
    public string exe_type { get; set; }
    [Column(TypeName = "datetime")]
    [DatabaseGenerated(DatabaseGeneratedOption.Computed)]
    public DateTime? datetime { get; set; } = DateTime.Now;
}

public class ExecutionLogContext : BaseContext
{
    public DbSet<ExecutionLog> execution_log { get; set; }
}