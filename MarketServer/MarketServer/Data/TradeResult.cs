namespace MarketServer.Data;

public class TradeResult
{
    public Item Item { get; }
    public double Price { get; }
    public int Lot { get; }
    public TradeResultCode ResultCode { get; }

    private TradeResult(Item item, double price, int lot, TradeResultCode resultCode)
    {
        Item = item;
        Price = price;
        Lot = lot;
        ResultCode = resultCode;
    }

    public static TradeResult Success(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.Success);
    
    public static TradeResult OrderNotFound(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.OrderNotFound);
    
    public static TradeResult FailedDeposit(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.FailedDeposit);
    
    public static TradeResult FailedWithdraw(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.FailedWithdraw);
    
    public static TradeResult FailedAddItem(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.FailedAddItem);
    
    public static TradeResult FailedTakeItem(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.FailedTakeItem);
    
    public static TradeResult IllegalPriceSetting(Item item, double price, int lot) =>
        new(item, price, lot, TradeResultCode.IllegalPriceSetting);
    
    
}

public enum TradeResultCode
{
    Success,
    OrderNotFound,
    FailedDeposit,
    FailedWithdraw,
    FailedTakeItem,
    FailedAddItem,
    IllegalPriceSetting
}