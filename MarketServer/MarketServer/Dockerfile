FROM mcr.microsoft.com/dotnet/aspnet:6.0 AS base
WORKDIR /app
EXPOSE 80
EXPOSE 443

FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build
WORKDIR /src
COPY ["MarketServer/MarketServer.csproj", "MarketServer/"]
RUN dotnet restore "MarketServer/MarketServer.csproj"
COPY . .
WORKDIR "/src/MarketServer"
RUN dotnet build "MarketServer.csproj" -c Release -o /app/build

FROM build AS publish
RUN dotnet publish "MarketServer.csproj" -c Release -o /app/publish /p:UseAppHost=false

FROM base AS final
WORKDIR /app
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "MarketServer.dll"]
