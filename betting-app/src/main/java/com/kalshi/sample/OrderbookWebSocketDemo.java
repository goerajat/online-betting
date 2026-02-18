package com.kalshi.sample;

import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.websocket.OrderbookDelta;
import com.kalshi.client.websocket.OrderbookSnapshot;
import com.kalshi.client.websocket.OrderbookUpdateConsumer;
import com.kalshi.client.websocket.OrderbookWebSocketClient;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Sample application demonstrating real-time orderbook updates via WebSocket.
 * Connects to Kalshi Demo API and subscribes to orderbook updates for specified markets.
 */
public class OrderbookWebSocketDemo {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final String[] DEFAULT_TICKERS = {"KXWTAMATCH-26JAN11SABKOS-SAB"};
    private static final String DEFAULT_CONFIG_FILE = "strategy.properties";

    // API credentials - loaded from config or environment
    private String apiKeyId;
    private String privateKeyFile;

    // Track orderbook state for each market
    private final Map<String, OrderbookState> orderbookStates = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        String[] tickers = args.length > 0 ? args : DEFAULT_TICKERS;

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║      Kalshi Orderbook WebSocket Demo                           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Subscribing to orderbook updates for: " + String.join(", ", tickers));
        System.out.println("Press Ctrl+C to exit");
        System.out.println();
        System.out.println("─".repeat(70));

        OrderbookWebSocketDemo demo = new OrderbookWebSocketDemo();
        demo.run(tickers);
    }

    public void run(String[] tickers) {
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        // Load API credentials
        try {
            loadApiCredentials();
        } catch (Exception e) {
            System.err.println("Failed to load API credentials: " + e.getMessage());
            return;
        }

        // Create authenticator from API key and private key file
        Path privateKeyPath = Path.of(privateKeyFile);
        System.out.println("Loading private key from: " + privateKeyPath.toAbsolutePath());

        KalshiAuthenticator authenticator = KalshiAuthenticator.fromFile(apiKeyId, privateKeyPath);

        // Create WebSocket client for demo environment with authentication
        OrderbookWebSocketClient wsClient = OrderbookWebSocketClient.builder()
                //.useDemo()
                .authenticator(authenticator)
                .build();

        // Add shutdown hook for clean exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            wsClient.close();
            shutdownLatch.countDown();
        }));

        // Subscribe to orderbook updates
        wsClient.subscribe(List.of(tickers), new OrderbookUpdateConsumer() {
            @Override
            public void onConnected() {
                printTimestamp();
                System.out.println(" [CONNECTED] WebSocket connection established");
                System.out.println();
            }

            @Override
            public void onSubscribed(String marketTicker) {
                printTimestamp();
                System.out.println(" [SUBSCRIBED] " + marketTicker);
            }

            @Override
            public void onSnapshot(OrderbookSnapshot snapshot) {
                handleSnapshot(snapshot);
            }

            @Override
            public void onDelta(OrderbookDelta delta) {
                handleDelta(delta);
            }

            @Override
            public void onDisconnected(int code, String reason) {
                printTimestamp();
                System.out.println(" [DISCONNECTED] Code: " + code + ", Reason: " + reason);
                shutdownLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                printTimestamp();
                System.out.println(" [ERROR] " + error.getMessage());
                error.printStackTrace();
            }
        });

        // Wait for shutdown
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        wsClient.close();
        System.out.println("Demo ended.");
    }

    private void handleSnapshot(OrderbookSnapshot snapshot) {
        String ticker = snapshot.getMarketTicker();

        // Store the snapshot state
        OrderbookState state = new OrderbookState(snapshot);
        orderbookStates.put(ticker, state);

        printTimestamp();
        System.out.println(" [SNAPSHOT] " + ticker);
        System.out.println();
        printOrderbookState(state);
        System.out.println();
    }

    private void handleDelta(OrderbookDelta delta) {
        String ticker = delta.getMarketTicker();

        // Update local state
        OrderbookState state = orderbookStates.get(ticker);
        if (state != null) {
            state.applyDelta(delta);
        }

        // Print delta info
        printTimestamp();
        String direction = delta.getDelta() > 0 ? "+" : "";
        String sideEmoji = delta.isYesSide() ? "🟢" : "🔴";

        System.out.printf(" [DELTA] %s %s %s @ %d¢: %s%d contracts%n",
                ticker,
                sideEmoji,
                delta.getSide().toUpperCase(),
                delta.getPrice(),
                direction,
                delta.getDelta());

        if (delta.isOwnOrder()) {
            System.out.println("         (Triggered by your order: " + delta.getClientOrderId() + ")");
        }

        // Show updated best prices
        if (state != null) {
            System.out.printf("         Best Yes: %s¢  |  Best No: %s¢%n",
                    state.getBestYesBid() != null ? state.getBestYesBid() : "N/A",
                    state.getBestNoBid() != null ? state.getBestNoBid() : "N/A");
        }
        System.out.println();
    }

    private void printOrderbookState(OrderbookState state) {
        System.out.println("  ┌─────────────────────────────────────────────────────────────┐");
        System.out.println("  │ ORDERBOOK                                                   │");
        System.out.println("  └─────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  YES Bids                           NO Bids");
        System.out.println("  ─────────────────────              ─────────────────────");

        int maxLevels = Math.max(
                state.getYesLevels().size(),
                state.getNoLevels().size()
        );
        maxLevels = Math.min(maxLevels, 5); // Show top 5 levels

        for (int i = 0; i < maxLevels; i++) {
            String yesLevel = "";
            String noLevel = "";

            if (i < state.getYesLevels().size()) {
                int[] level = state.getYesLevels().get(i);
                yesLevel = String.format("%3d¢ × %-6d", level[0], level[1]);
            }

            if (i < state.getNoLevels().size()) {
                int[] level = state.getNoLevels().get(i);
                noLevel = String.format("%3d¢ × %-6d", level[0], level[1]);
            }

            System.out.printf("  %-25s          %-25s%n", yesLevel, noLevel);
        }

        if (maxLevels == 0) {
            System.out.println("  (Empty orderbook)");
        }

        System.out.println();
        System.out.printf("  Total Yes Depth: %,d  |  Total No Depth: %,d%n",
                state.getTotalYesDepth(), state.getTotalNoDepth());
    }

    private void printTimestamp() {
        System.out.print("[" + TIME_FORMAT.format(LocalDateTime.now()) + "]");
    }

    /**
     * Load API credentials from config file or environment variables.
     */
    private void loadApiCredentials() throws Exception {
        // Try loading from config file first
        Path configPath = Path.of(DEFAULT_CONFIG_FILE);
        if (Files.exists(configPath)) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                props.load(fis);
            }
            String keyId = props.getProperty("api.keyId");
            String keyFile = props.getProperty("api.privateKeyFile");
            if (keyId != null && !keyId.trim().isEmpty()) {
                apiKeyId = keyId.trim();
                System.out.println("Loaded API Key ID from " + DEFAULT_CONFIG_FILE);
            }
            if (keyFile != null && !keyFile.trim().isEmpty()) {
                privateKeyFile = keyFile.trim();
                System.out.println("Loaded private key file from " + DEFAULT_CONFIG_FILE);
            }
        }

        // Fall back to environment variables
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            apiKeyId = System.getenv("KALSHI_API_KEY_ID");
            if (apiKeyId != null && !apiKeyId.isEmpty()) {
                System.out.println("Loaded API Key ID from environment variable KALSHI_API_KEY_ID");
            }
        }
        if (privateKeyFile == null || privateKeyFile.isEmpty()) {
            privateKeyFile = System.getenv("KALSHI_PRIVATE_KEY_FILE");
            if (privateKeyFile != null && !privateKeyFile.isEmpty()) {
                System.out.println("Loaded private key file from environment variable KALSHI_PRIVATE_KEY_FILE");
            }
        }

        // Validate
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            throw new IllegalStateException(
                "API Key ID not configured. Set via:\n" +
                "  1. Config file (" + DEFAULT_CONFIG_FILE + "): api.keyId=your-key-id\n" +
                "  2. Environment variable: KALSHI_API_KEY_ID");
        }
        if (privateKeyFile == null || privateKeyFile.isEmpty()) {
            throw new IllegalStateException(
                "Private key file not configured. Set via:\n" +
                "  1. Config file (" + DEFAULT_CONFIG_FILE + "): api.privateKeyFile=path/to/key.pem\n" +
                "  2. Environment variable: KALSHI_PRIVATE_KEY_FILE");
        }
    }

    /**
     * Tracks the current state of an orderbook, applying deltas as they arrive.
     */
    private static class OrderbookState {
        private final String marketTicker;
        private final Map<Integer, Integer> yesBids = new ConcurrentHashMap<>();
        private final Map<Integer, Integer> noBids = new ConcurrentHashMap<>();

        public OrderbookState(OrderbookSnapshot snapshot) {
            this.marketTicker = snapshot.getMarketTicker();

            // Initialize from snapshot
            if (snapshot.getYes() != null) {
                for (List<Number> level : snapshot.getYes()) {
                    int price = level.get(0).intValue();
                    int quantity = level.get(1).intValue();
                    yesBids.put(price, quantity);
                }
            }

            if (snapshot.getNo() != null) {
                for (List<Number> level : snapshot.getNo()) {
                    int price = level.get(0).intValue();
                    int quantity = level.get(1).intValue();
                    noBids.put(price, quantity);
                }
            }
        }

        public void applyDelta(OrderbookDelta delta) {
            Map<Integer, Integer> bids = delta.isYesSide() ? yesBids : noBids;
            int price = delta.getPrice();
            int currentQty = bids.getOrDefault(price, 0);
            int newQty = currentQty + delta.getDelta();

            if (newQty <= 0) {
                bids.remove(price);
            } else {
                bids.put(price, newQty);
            }
        }

        public Integer getBestYesBid() {
            return yesBids.keySet().stream().max(Integer::compareTo).orElse(null);
        }

        public Integer getBestNoBid() {
            return noBids.keySet().stream().max(Integer::compareTo).orElse(null);
        }

        public List<int[]> getYesLevels() {
            return yesBids.entrySet().stream()
                    .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                    .map(e -> new int[]{e.getKey(), e.getValue()})
                    .toList();
        }

        public List<int[]> getNoLevels() {
            return noBids.entrySet().stream()
                    .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                    .map(e -> new int[]{e.getKey(), e.getValue()})
                    .toList();
        }

        public long getTotalYesDepth() {
            return yesBids.values().stream().mapToLong(Integer::longValue).sum();
        }

        public long getTotalNoDepth() {
            return noBids.values().stream().mapToLong(Integer::longValue).sum();
        }
    }
}
