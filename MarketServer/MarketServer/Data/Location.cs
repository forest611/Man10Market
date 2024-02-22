namespace MarketServer.data;

public class Location
{
    public double X { get; }
    public double Y { get; }
    public double Z { get; }
    public string World { get; }

    public Location(double x, double y, double z, string world)
    {
        X = x;
        Y = y;
        Z = z;
        World = world;
    }

    public Location()
    {
        X = 0;
        Y = 0;
        Z = 0;
        World = "none";
    }
}