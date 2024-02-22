using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace MarketServer.Model;

public class StorageLog
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public int id { get; set; }
    public int item_id { get; set; }
    public string item_key { get; set; }
    public string order_player { get; set; }
    public string order_uuid { get; set; }
    public string target_player { get; set; }
    public string target_uuid { get; set; }
    public string action { get; set; }
    public int? edit_amount { get; set; }
    public int? storage_amount { get; set; }
    public string world { get; set; }
    public double? x { get; set; }
    public double? y { get; set; }
    public double? z { get; set; }
    [Column(TypeName = "datetime")]
    [DatabaseGenerated(DatabaseGeneratedOption.Computed)]
    public DateTime? time { get; set; } = DateTime.Now;
}

public class StorageLogContext : BaseContext
{
    public DbSet<StorageLog> storage_log { get; set; }
}