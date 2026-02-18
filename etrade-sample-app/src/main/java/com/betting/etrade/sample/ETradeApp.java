package com.betting.etrade.sample;

import com.betting.etrade.manager.MarketDataManager;
import com.betting.etrade.model.QuoteData;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Sample application demonstrating E*TRADE MarketDataManager usage.
 */
public class ETradeApp {

    private static final String CONFIG_FILE = "etrade-config.properties";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Scanner scanner = new Scanner(System.in);
    private MarketDataManager marketDataManager;
    private String activeSubscriptionId;

    public static void main(String[] args) {
        new ETradeApp().run();
    }

    public void run() {
        printBanner();

        try {
            // Create MarketDataManager from config file
            println("Loading configuration from " + CONFIG_FILE + "...");
            marketDataManager = MarketDataManager.fromPropertiesFile(CONFIG_FILE);
            println("Configuration loaded successfully.");

            // Authenticate
            authenticate();

            // Run main menu
            runMainMenu();

        } catch (Exception e) {
            println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void authenticate() throws IOException {
        println("\n--- OAuth Authentication ---");

        marketDataManager.authenticate(authUrl -> {
            println("\nPlease authorize the application:");
            println(authUrl);

            // Try to open browser automatically
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(new URI(authUrl));
                    println("\n(Browser opened automatically)");
                } catch (Exception e) {
                    println("\n(Please open the URL manually in your browser)");
                }
            }

            // Get verifier code from user
            print("\nEnter the verification code: ");
            return scanner.nextLine().trim();
        });

        println("Authentication successful!");
    }

    private void runMainMenu() {
        while (true) {
            println("\n========== E*TRADE Market Data Demo ==========");
            println("1. Get single quote");
            println("2. Get multiple quotes");
            println("3. Subscribe to quotes (with callback)");
            println("4. Stop subscription");
            println("5. Exit");
            print("\nChoice: ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        getSingleQuote();
                        break;
                    case "2":
                        getMultipleQuotes();
                        break;
                    case "3":
                        startSubscription();
                        break;
                    case "4":
                        stopSubscription();
                        break;
                    case "5":
                        println("Goodbye!");
                        return;
                    default:
                        println("Invalid choice.");
                }
            } catch (Exception e) {
                println("Error: " + e.getMessage());
            }
        }
    }

    private void getSingleQuote() throws IOException {
        print("Enter symbol: ");
        String symbol = scanner.nextLine().trim().toUpperCase();

        println("Fetching quote for " + symbol + "...");

        List<QuoteData> quotes = marketDataManager.getQuotes(symbol);
        if (quotes.isEmpty()) {
            println("No quote data returned for " + symbol);
        } else {
            printQuote(quotes.get(0));
        }
    }

    private void getMultipleQuotes() throws IOException {
        print("Enter symbols (comma-separated): ");
        String input = scanner.nextLine().trim().toUpperCase();
        String[] symbols = input.split(",");

        println("Fetching quotes...");

        List<QuoteData> quotes = marketDataManager.getQuotes(symbols);

        if (quotes.isEmpty()) {
            println("No quotes returned.");
        } else {
            println("\n" + "-".repeat(80));
            for (QuoteData quote : quotes) {
                printQuote(quote);
            }
            println("-".repeat(80));
        }
    }

    private void startSubscription() {
        if (activeSubscriptionId != null) {
            println("Subscription already active. Stop it first.");
            return;
        }

        print("Enter symbols to subscribe (comma-separated): ");
        String input = scanner.nextLine().trim().toUpperCase();
        List<String> symbols = Arrays.asList(input.split(","));

        println("\nStarting subscription for: " + symbols);
        println("Poll interval: " + (marketDataManager.getPollIntervalMs() / 1000) + " seconds");
        println("Press Enter to return to menu (subscription continues in background)\n");

        activeSubscriptionId = marketDataManager.subscribe(
                symbols,
                quotes -> {
                    String time = LocalDateTime.now().format(TIME_FORMAT);
                    println("\n[" + time + "] Quotes received:");
                    for (QuoteData quote : quotes) {
                        printQuoteLine(quote);
                    }
                },
                error -> println("[ERROR] " + error.getMessage())
        );

        println("Subscription started with ID: " + activeSubscriptionId);

        // Wait for user to press enter
        scanner.nextLine();
    }

    private void stopSubscription() {
        if (activeSubscriptionId == null) {
            println("No active subscription.");
            return;
        }

        marketDataManager.unsubscribe(activeSubscriptionId);
        println("Subscription " + activeSubscriptionId + " stopped.");
        activeSubscriptionId = null;
    }

    private void printQuote(QuoteData quote) {
        println("\n  Symbol: " + quote.getSymbol());
        println("  Status: " + quote.getQuoteStatus());
        println("  Time:   " + quote.getDateTime());

        if (quote.getIntraday() != null) {
            var intraday = quote.getIntraday();
            println("  Last:   $" + formatPrice(intraday.getLastTrade()));
            println("  Change: $" + formatPrice(intraday.getChangeClose()) +
                    " (" + formatPercent(intraday.getChangeClosePercentage()) + ")");
            println("  Bid:    $" + formatPrice(intraday.getBid()));
            println("  Ask:    $" + formatPrice(intraday.getAsk()));
            println("  High:   $" + formatPrice(intraday.getHigh()));
            println("  Low:    $" + formatPrice(intraday.getLow()));
            println("  Volume: " + formatVolume(intraday.getTotalVolume()));
        }
    }

    private void printQuoteLine(QuoteData quote) {
        if (quote.getIntraday() != null) {
            var intraday = quote.getIntraday();
            String change = formatPrice(intraday.getChangeClose());
            String pct = formatPercent(intraday.getChangeClosePercentage());
            String direction = intraday.getChangeClose() != null && intraday.getChangeClose() >= 0 ? "+" : "";

            System.out.printf("  %-6s  $%-10s  %s%s (%s)  Vol: %s%n",
                    quote.getSymbol(),
                    formatPrice(intraday.getLastTrade()),
                    direction, change, pct,
                    formatVolume(intraday.getTotalVolume()));
        } else {
            System.out.printf("  %-6s  $%-10s%n", quote.getSymbol(), formatPrice(quote.getLastPrice()));
        }
    }

    private String formatPrice(Double price) {
        return price != null ? String.format("%.2f", price) : "N/A";
    }

    private String formatPercent(Double pct) {
        return pct != null ? String.format("%.2f%%", pct) : "N/A";
    }

    private String formatVolume(Long volume) {
        if (volume == null) return "N/A";
        if (volume >= 1_000_000) return String.format("%.2fM", volume / 1_000_000.0);
        if (volume >= 1_000) return String.format("%.2fK", volume / 1_000.0);
        return volume.toString();
    }

    private void cleanup() {
        if (marketDataManager != null) {
            marketDataManager.shutdown();
        }
        scanner.close();
    }

    private void printBanner() {
        println("╔════════════════════════════════════════════════════════════╗");
        println("║        E*TRADE MarketDataManager Demo                      ║");
        println("║                                                            ║");
        println("║   Demonstrates INTRADAY quote subscriptions & callbacks    ║");
        println("╚════════════════════════════════════════════════════════════╝");
    }

    private void println(String msg) {
        System.out.println(msg);
    }

    private void print(String msg) {
        System.out.print(msg);
    }
}
