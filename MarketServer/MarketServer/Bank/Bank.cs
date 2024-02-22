using MarketServer.Data;
using MarketServer.Market;

namespace MarketServer.Bank;

public static class Bank
{
    public static async Task<bool> Deposit(Player player, double amount, string note, string displayNote)
    {
        return await Task.FromResult(false);
    }

    public static async Task<bool> Withdraw(Player player, double amount, string note, string displayNote)
    {
        return await Task.FromResult(false);
    }
}