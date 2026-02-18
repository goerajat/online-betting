package com.kalshi.sample;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Orderbook;
import com.kalshi.client.model.Series;
import com.kalshi.client.service.EventService;
import com.kalshi.client.service.MarketService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sample application that queries Kalshi Demo API for series, events, and markets.
 * Demonstrates usage of the kalshi-java-client library.
 */
public class KalshiDemoApp {

    private static final String[] TARGET_TICKERS = {"KXINXPOS", "KXINX"};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final KalshiApi api;

    public KalshiDemoApp() {
        // Create API client for demo environment (no authentication needed for public data)
        this.api = KalshiApi.builder()
                .useDemo()
                .build();
    }

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           Kalshi Demo API - Sample Application                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        KalshiDemoApp app = new KalshiDemoApp();

        try {
            app.run();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void run() {
        for (String ticker : TARGET_TICKERS) {
            System.out.println("━".repeat(70));
            System.out.println("  Querying data for ticker: " + ticker);
            System.out.println("━".repeat(70));
            System.out.println();

            querySeriesData(ticker);
            queryEventData(ticker);
            queryMarketData(ticker);

            System.out.println();
        }

        System.out.println("═".repeat(70));
        System.out.println("  Query completed successfully!");
        System.out.println("═".repeat(70));
    }

    private void querySeriesData(String ticker) {
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  SERIES DATA                                                    │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        try {
            Series series = api.series().getSeries(ticker);
            printSeries(series);
        } catch (Exception e) {
            System.out.println("  Could not retrieve series: " + e.getMessage());
            System.out.println("  (Ticker might be an event or market ticker, not a series ticker)");
        }
        System.out.println();
    }

    private void queryEventData(String ticker) {
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  EVENT DATA                                                     │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        try {
            List<Event> events = api.events().getEvents(EventService.EventQuery.builder().seriesTicker(ticker).withNestedMarkets(false).build());
            events.forEach(this::printEvent);
        } catch (Exception e) {
            System.out.println("  Could not retrieve event: " + e.getMessage());
            System.out.println("  (Ticker might be a series or market ticker, not an event ticker)");
        }
        System.out.println();
    }

    private void queryMarketData(String ticker) {
        System.out.println("┌─────────────────────────────────────────────────────────────────┐");
        System.out.println("│  MARKET DATA                                                    │");
        System.out.println("└─────────────────────────────────────────────────────────────────┘");

        // Try to get specific market first
        try {
            Market market = api.markets().getMarket(ticker);
            printMarket(market);
            printOrderbook(ticker);
            return;
        } catch (Exception e) {
            // Not a direct market ticker, try searching
        }

        // Search for markets by event ticker
        try {
            List<Market> markets = api.markets().getMarkets(
                    MarketService.MarketQuery.builder()
                            .eventTicker(ticker)
                            .limit(10)
                            .build()
            );

            if (!markets.isEmpty()) {
                System.out.println("  Found " + markets.size() + " market(s) for event: " + ticker);
                System.out.println();
                for (Market market : markets) {
                    printMarket(market);
                    printOrderbook(market.getTicker());
                    System.out.println();
                }
            } else {
                // Try searching by series ticker
                markets = api.markets().getMarkets(
                        MarketService.MarketQuery.builder()
                                .seriesTicker(ticker)
                                .limit(10)
                                .build()
                );

                if (!markets.isEmpty()) {
                    System.out.println("  Found " + markets.size() + " market(s) for series: " + ticker);
                    System.out.println();
                    for (Market market : markets) {
                        printMarket(market);
                        printOrderbook(market.getTicker());
                        System.out.println();
                    }
                } else {
                    System.out.println("  No markets found for ticker: " + ticker);
                }
            }
        } catch (Exception e) {
            System.out.println("  Error retrieving markets: " + e.getMessage());
        }
    }

    private void printSeries(Series series) {
        System.out.println();
        System.out.println("  ╭──────────────────────────────────────────────────────────────╮");
        System.out.printf("  │ Series: %-53s │%n", truncate(series.getTicker(), 53));
        System.out.println("  ╰──────────────────────────────────────────────────────────────╯");
        System.out.println();
        System.out.printf("  Title:       %s%n", series.getTitle());
        System.out.printf("  Category:    %s%n", series.getCategory());
        System.out.printf("  Frequency:   %s%n", series.getFrequency());
        System.out.printf("  Fee Type:    %s%n", series.getFeeType());

        if (series.getVolume() != null) {
            System.out.printf("  Volume:      %,d contracts%n", series.getVolume());
        }

        if (series.getTags() != null && !series.getTags().isEmpty()) {
            System.out.printf("  Tags:        %s%n", String.join(", ", series.getTags()));
        }

        if (series.getSettlementSources() != null && !series.getSettlementSources().isEmpty()) {
            System.out.println("  Settlement Sources:");
            series.getSettlementSources().forEach(source ->
                    System.out.printf("    • %s: %s%n", source.getName(), source.getUrl())
            );
        }
    }

    private void printEvent(Event event) {
        System.out.println();
        System.out.println("  ╭──────────────────────────────────────────────────────────────╮");
        System.out.printf("  │ Event: %-54s │%n", truncate(event.getEventTicker(), 54));
        System.out.println("  ╰──────────────────────────────────────────────────────────────╯");
        System.out.println();
        System.out.printf("  Title:            %s%n", event.getTitle());

        if (event.getSubTitle() != null) {
            System.out.printf("  Subtitle:         %s%n", event.getSubTitle());
        }

        System.out.printf("  Series:           %s%n", event.getSeriesTicker());
        System.out.printf("  Category:         %s%n", event.getCategory());
        System.out.printf("  Mutually Excl.:   %s%n", event.getMutuallyExclusive());

        if (event.getStrikeDate() != null) {
            System.out.printf("  Strike Date:      %s%n", formatInstant(event.getStrikeDate()));
        }

        if (event.getStrikePeriod() != null) {
            System.out.printf("  Strike Period:    %s%n", event.getStrikePeriod());
        }

        if (event.getMarkets() != null && !event.getMarkets().isEmpty()) {
            System.out.println();
            System.out.println("  Associated Markets: " + event.getMarkets().size());
            for (Market market : event.getMarkets()) {
                System.out.printf("    • %s - %s%n", market.getTicker(), truncate(market.getTitle(), 45));
            }
        }
    }

    private void printMarket(Market market) {
        System.out.println("  ╭──────────────────────────────────────────────────────────────╮");
        System.out.printf("  │ Market: %-53s │%n", truncate(market.getTicker(), 53));
        System.out.println("  ╰──────────────────────────────────────────────────────────────╯");
        System.out.println();
        System.out.printf("  Title:          %s%n", market.getTitle());

        if (market.getSubtitle() != null && !market.getSubtitle().isEmpty()) {
            System.out.printf("  Subtitle:       %s%n", market.getSubtitle());
        }

        System.out.printf("  Event:          %s%n", market.getEventTicker());
        System.out.printf("  Type:           %s%n", market.getMarketType());
        System.out.printf("  Status:         %s%n", formatStatus(market.getStatus()));
        System.out.println();

        // Pricing information
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ PRICING                                                     │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.printf("  Yes Bid:        %s%n", formatPrice(market.getYesBidDollars()));
        System.out.printf("  Yes Ask:        %s%n", formatPrice(market.getYesAskDollars()));
        System.out.printf("  No Bid:         %s%n", formatPrice(market.getNoBidDollars()));
        System.out.printf("  No Ask:         %s%n", formatPrice(market.getNoAskDollars()));
        System.out.printf("  Last Price:     %s%n", formatPrice(market.getLastPriceDollars()));
        System.out.println();

        // Volume and Interest
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ VOLUME & INTEREST                                           │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.printf("  Volume:         %s contracts%n", formatNumber(market.getVolume()));
        System.out.printf("  24h Volume:     %s contracts%n", formatNumber(market.getVolume24h()));
        System.out.printf("  Open Interest:  %s contracts%n", formatNumber(market.getOpenInterest()));
        System.out.println();

        // Timeline
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ TIMELINE                                                    │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");

        if (market.getOpenTime() != null) {
            System.out.printf("  Open Time:      %s%n", formatInstant(market.getOpenTime()));
        }
        if (market.getCloseTime() != null) {
            System.out.printf("  Close Time:     %s%n", formatInstant(market.getCloseTime()));
        }
        if (market.getExpirationTime() != null) {
            System.out.printf("  Expiration:     %s%n", formatInstant(market.getExpirationTime()));
        }

        // Result (if settled)
        if (market.getResult() != null && !market.getResult().isEmpty()) {
            System.out.println();
            System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
            System.out.println("  │ SETTLEMENT                                                  │");
            System.out.println("  └─────────────────────────────────────────────────────────────┘");
            System.out.printf("  Result:         %s%n", market.getResult().toUpperCase());
        }
    }

    private void printOrderbook(String ticker) {
        try {
            Orderbook orderbook = api.markets().getOrderbook(ticker, 5);

            System.out.println();
            System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
            System.out.println("  │ ORDERBOOK (Top 5 levels)                                    │");
            System.out.println("  └─────────────────────────────────────────────────────────────┘");

            System.out.println();
            System.out.println("  YES Bids:                          NO Bids:");
            System.out.println("  ─────────────────────              ─────────────────────");

            int maxLevels = Math.max(
                    orderbook.getYes() != null ? orderbook.getYes().size() : 0,
                    orderbook.getNo() != null ? orderbook.getNo().size() : 0
            );

            for (int i = 0; i < maxLevels; i++) {
                String yesLevel = "";
                String noLevel = "";

                if (orderbook.getYes() != null && i < orderbook.getYes().size()) {
                    List<Number> level = orderbook.getYes().get(i);
                    yesLevel = String.format("%d¢ × %d", level.get(0).intValue(), level.get(1).intValue());
                }

                if (orderbook.getNo() != null && i < orderbook.getNo().size()) {
                    List<Number> level = orderbook.getNo().get(i);
                    noLevel = String.format("%d¢ × %d", level.get(0).intValue(), level.get(1).intValue());
                }

                System.out.printf("  %-25s          %-25s%n", yesLevel, noLevel);
            }

            if (maxLevels == 0) {
                System.out.println("  (No orders in orderbook)");
            }

            System.out.println();
            System.out.printf("  Total Yes Depth: %,d contracts%n", orderbook.getTotalYesDepth());
            System.out.printf("  Total No Depth:  %,d contracts%n", orderbook.getTotalNoDepth());

        } catch (Exception e) {
            System.out.println("  Could not retrieve orderbook: " + e.getMessage());
        }
    }

    // Helper methods

    private String formatPrice(String price) {
        if (price == null || price.isEmpty()) {
            return "N/A";
        }
        try {
            double value = Double.parseDouble(price);
            return String.format("$%.2f (%.0f¢)", value, value * 100);
        } catch (NumberFormatException e) {
            return price;
        }
    }

    private String formatNumber(Long number) {
        if (number == null) {
            return "N/A";
        }
        return String.format("%,d", number);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return DATE_FORMAT.format(instant);
    }

    private String formatStatus(String status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status.toLowerCase()) {
            case "active", "open" -> "🟢 " + status.toUpperCase();
            case "closed", "finalized", "settled" -> "🔴 " + status.toUpperCase();
            case "initialized", "unopened" -> "🟡 " + status.toUpperCase();
            default -> status.toUpperCase();
        };
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
