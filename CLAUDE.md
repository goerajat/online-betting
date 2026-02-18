# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

This is a Java 17 multi-module Maven project.

```bash
# Build entire project
mvn clean install

# Build a single module
mvn clean install -pl kalshi-java-client

# Run all tests
mvn test

# Run tests for a single module
mvn test -pl kalshi-java-client

# Run a single test class
mvn test -pl kalshi-java-client -Dtest=OrderbookTest

# Run a single test method
mvn test -pl kalshi-java-client -Dtest=OrderbookTest#testMethodName

# Run the E*TRADE console sample app
mvn exec:java -pl etrade-sample-app

# Run the E*TRADE JavaFX app
mvn javafx:run -pl etrade-javafx-app
```

No linter or formatter is configured. Tests use JUnit 5 with OkHttp MockWebServer for HTTP mocking.

## Architecture

Multi-module Maven project for trading on Kalshi (binary options) with optional E*TRADE stock market data integration.

### Module Dependency Graph

```
marketdata-api          (provider-agnostic interfaces: Quote, MarketDataManager)
    ↑
etrade-api              (E*TRADE client: OAuth 1.0, INTRADAY quotes, subscriptions)
    ↑
kalshi-java-client      (core: Kalshi REST + WebSocket client, strategy framework, risk mgmt)
    ↑
betting-app             (application: demo apps, JavaFX UI, strategy implementations)

etrade-sample-app       (standalone console demo for E*TRADE)
etrade-javafx-app       (standalone JavaFX demo for E*TRADE)
```

### kalshi-java-client — Core Library

Entry point: `KalshiApi` (builder pattern). Provides access to all services via `api.series()`, `api.events()`, `api.markets()`, `api.orders()`.

**Key layers:**
- **Services** (`SeriesService`, `EventService`, `MarketService`, `OrderService`) — REST API calls via `KalshiClient` (OkHttp + Jackson)
- **Managers** (`OrderManager`, `PositionManager`, `MarketManager`, `EventManager`) — stateful tracking with polling/WebSocket
- **WebSocket clients** (`OrderbookWebSocketClient`, `PositionWebSocketClient`, `MarketLifecycleWebSocketClient`) — live streaming
- **Strategy framework** (`TradingStrategy` → `EventStrategy`) — abstract base with lifecycle hooks (`onStart`, `onTimer`, `onMarketChange`, `onOrderChange`). Strategies are loaded by class name from `strategy.properties` and managed by `StrategyManager`/`StrategyLauncher`
- **Risk management** (`RiskChecker`, `RiskConfig`) — per-order/position limits with per-strategy overrides
- **Event filtering** (`EventFilter`, `EventFilterCriteria`) — filter events by series, date range, category, title pattern
- **Authentication** — RSA-PSS SHA256 signing via `KalshiAuthenticator`
- **Exception hierarchy** — `KalshiApiException` → `AuthenticationException`, `RateLimitException`, `OrderException`

### betting-app — Application Layer

- `KalshiDemoApp` — console demo
- `OrderbookFxApp` — JavaFX live orderbook viewer
- `MultiEventStrategyDemo` — runs multiple strategies
- Strategy implementations: `IndexEventStrategy`, `ExampleStrategy`
- UI components: `StrategyManagerTab`, `OrderBlotterTable`, `PositionBlotterTable`, `RiskConfigPanel`
- Configuration: `strategy.properties` (series tickers, strategy class, filters, risk limits, API credentials) and `etrade-config.properties`

### API Environments

- **Kalshi Production**: `https://api.elections.kalshi.com/trade-api/v2`
- **Kalshi Demo**: `https://demo-api.kalshi.co/trade-api/v2` (set `api.useDemo=true` or call `.useDemo()` on builder)
- **E*TRADE Sandbox**: `https://apisb.etrade.com`
- **E*TRADE Production**: `https://api.etrade.com`

### Key Dependencies

OkHttp 4.12.0 (HTTP), Jackson 2.17.0 (JSON), SLF4J 2.0.12 + Logback (logging), JavaFX 21 (GUI, optional), JUnit 5.10.2 (testing).

## Important Notes

- API credentials (PEM keys, key IDs) are configured in `strategy.properties` and `etrade-config.properties`. These can also be set via environment variables `KALSHI_API_KEY_ID` and `KALSHI_PRIVATE_KEY_FILE`.
- Prices in Kalshi are in cents (e.g., 50 = $0.50). Risk limits (`maxOrderNotional`, etc.) are also in cents.
- New strategies extend `EventStrategy`, implement lifecycle hooks, and are registered by fully-qualified class name in `strategy.properties`.
