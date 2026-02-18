package com.kalshi.sample;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.KalshiClient;
import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.config.StrategyConfig;
import com.kalshi.client.config.StrategyLauncher;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.model.Series;
import com.kalshi.client.risk.RiskChecker;
import com.kalshi.client.risk.RiskConfig;
import com.kalshi.client.service.EventService;
import com.kalshi.client.strategy.StrategyManager;
import com.kalshi.client.strategy.TradingStrategy;
import com.kalshi.client.websocket.OrderbookDelta;
import com.kalshi.client.websocket.OrderbookSnapshot;
import com.kalshi.client.websocket.OrderbookUpdateConsumer;
import com.kalshi.client.websocket.OrderbookWebSocketClient;
import com.kalshi.sample.marketdata.MarketDataService;
import com.betting.marketdata.model.Quote;
import com.kalshi.sample.ui.RiskConfigPanel;
import com.kalshi.sample.ui.StrategyManagerTab;
import com.kalshi.sample.ui.StrategyTab;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * JavaFX application for browsing Kalshi series/events/markets and viewing orderbooks.
 * Features tabbed navigation with Orders, Positions, and Market Explorer tabs.
 */
public class OrderbookFxApp extends Application {

    // API credentials - loaded from config file or environment variables
    private String apiKeyId;
    private String privateKeyFile;

    private OrderbookWebSocketClient wsClient;
    private KalshiApi kalshiApi;
    private OrderManager orderManager;
    private PositionManager positionManager;
    private MarketManager marketManager;
    private RiskChecker riskChecker;
    private TradingStrategy strategy;
    private String strategyClassName;

    // Multi-strategy support via config file
    private String configFilePath;
    private StrategyLauncher strategyLauncher;
    private StrategyManager strategyManager;

    // Market data service for external data (E*TRADE, etc.)
    private String marketDataConfigPath;
    private MarketDataService marketDataService;
    private Stage primaryStage;
    private String currentTicker;
    private boolean isSubscribed = false;
    private static final int MAX_ORDERBOOK_LEVELS = 5;

    // Tabs
    private TabPane tabPane;
    private Tab ordersTab;
    private Tab positionsTab;
    private Tab marketsTab;
    private Tab riskTab;
    private List<StrategyTab> strategyTabs = new ArrayList<>();
    private StrategyManagerTab strategyManagerTab;
    private RiskConfigPanel riskConfigPanel;
    private Tab marketDataTab;
    private Label marketDataStatusLabel;

    // Market Data quotes display
    private TextField tickerInputField;
    private TableView<QuoteRow> quotesTable;
    private ObservableList<QuoteRow> quoteRows = FXCollections.observableArrayList();
    private String marketDataSubscriptionId;
    private final Map<String, QuoteRow> quoteRowMap = new ConcurrentHashMap<>();

    // Orders table
    private TableView<OrderRow> ordersTable;
    private ObservableList<OrderRow> orderRows = FXCollections.observableArrayList();

    // Positions table
    private TableView<PositionRow> positionsTable;
    private ObservableList<PositionRow> positionRows = FXCollections.observableArrayList();

    // Market info cache
    private final Map<String, Market> marketInfoCache = new ConcurrentHashMap<>();

    // Background thread pool for API calls
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "kalshi-background");
        t.setDaemon(true);
        return t;
    });

    // Loading overlay components
    private StackPane rootStack;
    private VBox loadingOverlay;
    private Label loadingStatusLabel;
    private ProgressIndicator loadingProgress;

    // Orderbook state - shared across tabs
    private final Map<Integer, Integer> yesBids = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> noBids = new ConcurrentHashMap<>();

    // Market Explorer - Tree Navigation
    private TextField seriesTickerField;
    private Button searchButton;
    private TreeView<TreeItemData> treeView;
    private Label treeStatusLabel;

    // Market Info Header (shared)
    private Label marketTitleLabel;
    private Label marketSubtitleLabel;
    private Label marketStatusLabel;
    private Label connectionLabel;
    private Label marketDataConnectionLabel;

    // Orderbook tables - Orders tab
    private TableView<PriceLevel> ordersYesBidTable;
    private TableView<PriceLevel> ordersYesAskTable;
    private TableView<PriceLevel> ordersNoBidTable;
    private TableView<PriceLevel> ordersNoAskTable;
    private ObservableList<PriceLevel> ordersYesBidLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> ordersYesAskLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> ordersNoBidLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> ordersNoAskLevels = FXCollections.observableArrayList();

    // Orderbook tables - Positions tab
    private TableView<PriceLevel> positionsYesBidTable;
    private TableView<PriceLevel> positionsYesAskTable;
    private TableView<PriceLevel> positionsNoBidTable;
    private TableView<PriceLevel> positionsNoAskTable;
    private ObservableList<PriceLevel> positionsYesBidLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> positionsYesAskLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> positionsNoBidLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> positionsNoAskLevels = FXCollections.observableArrayList();

    // Orderbook tables - Markets tab
    private TableView<PriceLevel> marketsYesBidTable;
    private TableView<PriceLevel> marketsYesAskTable;
    private TableView<PriceLevel> marketsNoBidTable;
    private TableView<PriceLevel> marketsNoAskTable;
    private ObservableList<PriceLevel> marketsYesBidLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> marketsYesAskLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> marketsNoBidLevels = FXCollections.observableArrayList();
    private ObservableList<PriceLevel> marketsNoAskLevels = FXCollections.observableArrayList();

    private TextArea logArea;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        // Parse command line parameters
        Parameters params = getParameters();
        Map<String, String> named = params.getNamed();

        // Look for --config=path/to/strategy.properties parameter (multi-strategy mode)
        configFilePath = named.get("config");
        if (configFilePath != null && !configFilePath.isEmpty()) {
            System.out.println("Configuration file specified: " + configFilePath);
        }

        // Look for --strategy=com.example.MyStrategy parameter (single strategy mode)
        strategyClassName = named.get("strategy");
        if (strategyClassName != null && !strategyClassName.isEmpty()) {
            System.out.println("Strategy class specified: " + strategyClassName);
        }

        // Note: --config takes precedence over --strategy
        if (configFilePath != null && strategyClassName != null) {
            System.out.println("Both --config and --strategy specified; using --config (multi-strategy mode)");
        }

        // Look for --marketdata=path/to/marketdata.properties parameter
        marketDataConfigPath = named.get("marketdata");
        if (marketDataConfigPath != null && !marketDataConfigPath.isEmpty()) {
            System.out.println("Market data configuration file specified: " + marketDataConfigPath);
        }

        // Load API credentials from config file or environment variables
        loadApiCredentials();
    }

    /**
     * Load API credentials from configuration file or environment variables.
     * Priority: 1. Config file  2. Environment variables
     */
    private void loadApiCredentials() {
        // Try to load from config file first
        if (configFilePath != null && !configFilePath.isEmpty()) {
            try {
                StrategyConfig config = StrategyLauncher.loadConfig(configFilePath);
                if (config.getApiKeyId() != null && !config.getApiKeyId().isEmpty()) {
                    apiKeyId = config.getApiKeyId();
                    System.out.println("Loaded API Key ID from config file");
                }
                if (config.getPrivateKeyFile() != null && !config.getPrivateKeyFile().isEmpty()) {
                    privateKeyFile = config.getPrivateKeyFile();
                    System.out.println("Loaded private key file path from config file");
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to load config file for API credentials: " + e.getMessage());
            }
        }

        // Fall back to environment variables if not set from config
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            apiKeyId = System.getenv("KALSHI_API_KEY_ID");
            if (apiKeyId != null && !apiKeyId.isEmpty()) {
                System.out.println("Loaded API Key ID from environment variable KALSHI_API_KEY_ID");
            }
        }
        if (privateKeyFile == null || privateKeyFile.isEmpty()) {
            privateKeyFile = System.getenv("KALSHI_PRIVATE_KEY_FILE");
            if (privateKeyFile != null && !privateKeyFile.isEmpty()) {
                System.out.println("Loaded private key file path from environment variable KALSHI_PRIVATE_KEY_FILE");
            }
        }

        // Validate credentials
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            throw new IllegalStateException(
                "Kalshi API Key ID not configured. Set via:\n" +
                "  1. Config file: api.keyId=your-key-id\n" +
                "  2. Environment variable: KALSHI_API_KEY_ID");
        }
        if (privateKeyFile == null || privateKeyFile.isEmpty()) {
            throw new IllegalStateException(
                "Kalshi private key file not configured. Set via:\n" +
                "  1. Config file: api.privateKeyFile=path/to/key.pem\n" +
                "  2. Environment variable: KALSHI_PRIVATE_KEY_FILE");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Kalshi Trading Application");

        // Create main layout
        BorderPane mainContent = new BorderPane();
        mainContent.setStyle("-fx-background-color: #1a1a2e;");

        // Top: Title bar with connection status
        mainContent.setTop(createTitleBar());

        // Center: Tab pane
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #1a1a2e;");

        ordersTab = new Tab("Orders(0)");
        ordersTab.setContent(createOrdersTabContent());

        positionsTab = new Tab("Positions(0)");
        positionsTab.setContent(createPositionsTabContent());

        marketsTab = new Tab("Markets");
        marketsTab.setContent(createMarketsTabContent());

        // Risk tab - content will be set after RiskChecker is initialized
        riskTab = new Tab("Risk");
        riskTab.setContent(createRiskTabPlaceholder());

        // Market Data tab - for external market data providers
        marketDataTab = new Tab("Market Data");
        marketDataTab.setContent(createMarketDataTabContent());

        tabPane.getTabs().addAll(ordersTab, positionsTab, marketsTab, riskTab, marketDataTab);
        mainContent.setCenter(tabPane);

        // Bottom: Log area
        mainContent.setBottom(createLogArea());

        // Create loading overlay
        loadingOverlay = createLoadingOverlay();
        loadingOverlay.setVisible(false);

        // Use StackPane as root to allow loading overlay
        rootStack = new StackPane(mainContent, loadingOverlay);

        Scene scene = new Scene(rootStack, 1400, 900);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> shutdown());
        primaryStage.show();

        // Initialize clients in background thread
        initializeClientsAsync();
    }

    /**
     * Creates a semi-transparent loading overlay with progress indicator.
     */
    private VBox createLoadingOverlay() {
        VBox overlay = new VBox(15);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");

        loadingProgress = new ProgressIndicator();
        loadingProgress.setStyle("-fx-progress-color: #4CAF50;");
        loadingProgress.setPrefSize(60, 60);

        loadingStatusLabel = new Label("Initializing...");
        loadingStatusLabel.setTextFill(Color.WHITE);
        loadingStatusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        overlay.getChildren().addAll(loadingProgress, loadingStatusLabel);
        return overlay;
    }

    /**
     * Shows the loading overlay with the given status message.
     */
    private void showLoading(String message) {
        Platform.runLater(() -> {
            loadingStatusLabel.setText(message);
            loadingOverlay.setVisible(true);
        });
    }

    /**
     * Hides the loading overlay.
     */
    private void hideLoading() {
        Platform.runLater(() -> loadingOverlay.setVisible(false));
    }

    /**
     * Updates the loading status message.
     */
    private void updateLoadingStatus(String message) {
        Platform.runLater(() -> loadingStatusLabel.setText(message));
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox(20);
        titleBar.setPadding(new Insets(10, 15, 10, 15));
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setStyle("-fx-background-color: #0f3460;");

        Label title = new Label("Kalshi Trading Application");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Market info in title bar
        marketTitleLabel = new Label("No market selected");
        marketTitleLabel.setTextFill(Color.LIGHTGRAY);
        marketTitleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 12));

        marketSubtitleLabel = new Label("");
        marketSubtitleLabel.setTextFill(Color.GRAY);
        marketSubtitleLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 11));

        marketStatusLabel = new Label("");
        marketStatusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));

        VBox marketInfo = new VBox(2);
        marketInfo.setAlignment(Pos.CENTER_RIGHT);
        marketInfo.getChildren().addAll(marketTitleLabel, marketSubtitleLabel);

        connectionLabel = new Label("● Disconnected");
        connectionLabel.setTextFill(Color.RED);
        connectionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        // Market data status (external data like E*TRADE)
        marketDataConnectionLabel = new Label("");
        marketDataConnectionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        marketDataConnectionLabel.setTextFill(Color.GRAY);

        titleBar.getChildren().addAll(title, spacer, marketInfo, marketStatusLabel, marketDataConnectionLabel, connectionLabel);
        return titleBar;
    }

    // ==================== Orders Tab ====================

    private VBox createOrdersTabContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #1a1a2e;");

        // Orders table at top
        VBox ordersBox = createOrdersTableSection();

        // Orderbook below (2x2 grid)
        VBox orderbookBox = createOrderbookSection(
            ordersYesBidTable = new TableView<>(), ordersYesBidLevels,
            ordersYesAskTable = new TableView<>(), ordersYesAskLevels,
            ordersNoBidTable = new TableView<>(), ordersNoBidLevels,
            ordersNoAskTable = new TableView<>(), ordersNoAskLevels
        );

        content.getChildren().addAll(ordersBox, orderbookBox);
        VBox.setVgrow(orderbookBox, Priority.ALWAYS);

        return content;
    }

    @SuppressWarnings("unchecked")
    private VBox createOrdersTableSection() {
        VBox ordersBox = new VBox(5);
        ordersBox.setPadding(new Insets(8));
        ordersBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label ordersLabel = new Label("OPEN ORDERS");
        ordersLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        ordersLabel.setTextFill(Color.LIGHTSKYBLUE);

        ordersTable = new TableView<>();
        ordersTable.setItems(orderRows);
        ordersTable.setPlaceholder(new Label("No open orders"));
        ordersTable.setStyle("-fx-background-color: #0f3460;");
        ordersTable.setPrefHeight(200);
        ordersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        ordersTable.setFixedCellSize(-1);

        // Click handler to show orderbook for selected order
        ordersTable.setOnMouseClicked(e -> {
            OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 1 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                String ticker = selected.getTicker();
                Market cached = getMarketInfo(ticker);
                if (cached != null) {
                    onMarketSelected(cached);
                    log("Showing orderbook for order: " + ticker);
                } else {
                    // Fetch market info in background
                    log("Loading market info for: " + ticker);
                    fetchMarketInfoAsync(ticker).thenAccept(market -> {
                        if (market != null) {
                            Platform.runLater(() -> {
                                onMarketSelected(market);
                                log("Showing orderbook for order: " + ticker);
                            });
                        }
                    });
                }
            }
        });

        // Context menu for canceling orders
        ContextMenu orderContextMenu = new ContextMenu();
        MenuItem cancelOrderItem = new MenuItem("Cancel Order");
        cancelOrderItem.setOnAction(e -> {
            OrderRow selected = ordersTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                cancelOrder(selected);
            }
        });
        orderContextMenu.getItems().add(cancelOrderItem);
        ordersTable.setContextMenu(orderContextMenu);

        // Row factory for buy/sell colors
        ordersTable.setRowFactory(tv -> new TableRow<OrderRow>() {
            @Override
            protected void updateItem(OrderRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else {
                    String action = item.actionProperty().get();
                    boolean isEven = getIndex() % 2 == 0;
                    if ("BUY".equalsIgnoreCase(action)) {
                        setStyle(isEven ? "-fx-background-color: #1b4332;" : "-fx-background-color: #2d6a4f;");
                    } else if ("SELL".equalsIgnoreCase(action)) {
                        setStyle(isEven ? "-fx-background-color: #4a1c1c;" : "-fx-background-color: #6b2c2c;");
                    } else {
                        setStyle(isEven ? "-fx-background-color: #0f3460;" : "-fx-background-color: #1a4a7a;");
                    }
                }
            }
        });

        // Columns
        TableColumn<OrderRow, String> marketCol = new TableColumn<>("Market");
        marketCol.setCellValueFactory(c -> c.getValue().marketDescriptionProperty());
        marketCol.setCellFactory(col -> createWrappingCell());

        TableColumn<OrderRow, String> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(c -> c.getValue().sideProperty());
        sideCol.setPrefWidth(40);
        sideCol.setCellFactory(col -> createWhiteTextCell());

        TableColumn<OrderRow, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(c -> c.getValue().actionProperty());
        actionCol.setPrefWidth(50);
        actionCol.setCellFactory(col -> new TableCell<OrderRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if ("BUY".equalsIgnoreCase(item)) {
                        setTextFill(Color.LIGHTGREEN);
                        setFont(Font.font("Arial", FontWeight.BOLD, 12));
                    } else if ("SELL".equalsIgnoreCase(item)) {
                        setTextFill(Color.LIGHTCORAL);
                        setFont(Font.font("Arial", FontWeight.BOLD, 12));
                    } else {
                        setTextFill(Color.WHITE);
                    }
                }
            }
        });

        TableColumn<OrderRow, Number> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(c -> c.getValue().priceProperty());
        priceCol.setPrefWidth(45);
        priceCol.setCellFactory(col -> createWhiteNumberCell());

        TableColumn<OrderRow, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> c.getValue().quantityProperty());
        qtyCol.setPrefWidth(40);
        qtyCol.setCellFactory(col -> createWhiteNumberCell());

        ordersTable.getColumns().addAll(marketCol, sideCol, actionCol, priceCol, qtyCol);

        ordersBox.getChildren().addAll(ordersLabel, ordersTable);
        VBox.setVgrow(ordersTable, Priority.ALWAYS);

        return ordersBox;
    }

    // ==================== Positions Tab ====================

    private VBox createPositionsTabContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #1a1a2e;");

        // Positions table at top
        VBox positionsBox = createPositionsTableSection();

        // Orderbook below (2x2 grid)
        VBox orderbookBox = createOrderbookSection(
            positionsYesBidTable = new TableView<>(), positionsYesBidLevels,
            positionsYesAskTable = new TableView<>(), positionsYesAskLevels,
            positionsNoBidTable = new TableView<>(), positionsNoBidLevels,
            positionsNoAskTable = new TableView<>(), positionsNoAskLevels
        );

        content.getChildren().addAll(positionsBox, orderbookBox);
        VBox.setVgrow(orderbookBox, Priority.ALWAYS);

        return content;
    }

    @SuppressWarnings("unchecked")
    private VBox createPositionsTableSection() {
        VBox positionsBox = new VBox(5);
        positionsBox.setPadding(new Insets(8));
        positionsBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label positionsLabel = new Label("POSITIONS");
        positionsLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        positionsLabel.setTextFill(Color.LIGHTGREEN);

        positionsTable = new TableView<>();
        positionsTable.setItems(positionRows);
        positionsTable.setPlaceholder(new Label("No positions"));
        positionsTable.setStyle("-fx-background-color: #0f3460;");
        positionsTable.setPrefHeight(200);
        positionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        positionsTable.setFixedCellSize(-1);

        // Click handler to show orderbook for selected position
        positionsTable.setOnMouseClicked(e -> {
            PositionRow selected = positionsTable.getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 1 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                String ticker = selected.getTicker();
                Market cached = getMarketInfo(ticker);
                if (cached != null) {
                    onMarketSelected(cached);
                    log("Showing orderbook for position: " + ticker);
                } else {
                    // Fetch market info in background
                    log("Loading market info for: " + ticker);
                    fetchMarketInfoAsync(ticker).thenAccept(market -> {
                        if (market != null) {
                            Platform.runLater(() -> {
                                onMarketSelected(market);
                                log("Showing orderbook for position: " + ticker);
                            });
                        }
                    });
                }
            }
        });

        // Row factory for alternating colors
        positionsTable.setRowFactory(tv -> new TableRow<PositionRow>() {
            @Override
            protected void updateItem(PositionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else {
                    boolean isEven = getIndex() % 2 == 0;
                    setStyle(isEven ? "-fx-background-color: #0f3460;" : "-fx-background-color: #1a4a7a;");
                }
            }
        });

        // Columns
        TableColumn<PositionRow, String> marketCol = new TableColumn<>("Market");
        marketCol.setCellValueFactory(c -> c.getValue().marketDescriptionProperty());
        marketCol.setCellFactory(col -> createWrappingCellPosition());

        TableColumn<PositionRow, Number> posCol = new TableColumn<>("Pos");
        posCol.setCellValueFactory(c -> c.getValue().positionProperty());
        posCol.setPrefWidth(45);
        posCol.setCellFactory(col -> createWhiteNumberCellPosition());

        TableColumn<PositionRow, String> costCol = new TableColumn<>("Cost");
        costCol.setCellValueFactory(c -> c.getValue().costProperty());
        costCol.setPrefWidth(60);
        costCol.setCellFactory(col -> createWhiteTextCellPosition());

        TableColumn<PositionRow, String> pnlCol = new TableColumn<>("P&L");
        pnlCol.setCellValueFactory(c -> c.getValue().pnlProperty());
        pnlCol.setPrefWidth(60);
        pnlCol.setCellFactory(col -> createWhiteTextCellPosition());

        positionsTable.getColumns().addAll(marketCol, posCol, costCol, pnlCol);

        positionsBox.getChildren().addAll(positionsLabel, positionsTable);
        VBox.setVgrow(positionsTable, Priority.ALWAYS);

        return positionsBox;
    }

    // ==================== Markets Tab ====================

    private SplitPane createMarketsTabContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: #1a1a2e;");

        // Left: Tree navigation
        VBox leftPanel = createTreePanel();

        // Right: Orderbook
        VBox rightPanel = new VBox(10);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setStyle("-fx-background-color: #1a1a2e;");

        VBox orderbookBox = createOrderbookSection(
            marketsYesBidTable = new TableView<>(), marketsYesBidLevels,
            marketsYesAskTable = new TableView<>(), marketsYesAskLevels,
            marketsNoBidTable = new TableView<>(), marketsNoBidLevels,
            marketsNoAskTable = new TableView<>(), marketsNoAskLevels
        );

        rightPanel.getChildren().add(orderbookBox);
        VBox.setVgrow(orderbookBox, Priority.ALWAYS);

        splitPane.getItems().addAll(leftPanel, rightPanel);
        splitPane.setDividerPositions(0.35);

        return splitPane;
    }

    private VBox createTreePanel() {
        VBox leftPanel = new VBox(10);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-background-color: #16213e;");

        // Search section
        Label searchLabel = new Label("Series Ticker:");
        searchLabel.setTextFill(Color.WHITE);
        searchLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);

        seriesTickerField = new TextField();
        seriesTickerField.setPromptText("e.g., KXINX");
        seriesTickerField.setPrefWidth(150);
        seriesTickerField.setOnAction(e -> searchSeries());

        searchButton = new Button("Search");
        searchButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        searchButton.setOnAction(e -> searchSeries());

        searchBox.getChildren().addAll(seriesTickerField, searchButton);

        treeStatusLabel = new Label("Enter a series ticker to browse");
        treeStatusLabel.setTextFill(Color.LIGHTGRAY);
        treeStatusLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        treeStatusLabel.setWrapText(true);

        Label treeLabel = new Label("Series / Events / Markets");
        treeLabel.setTextFill(Color.WHITE);
        treeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        treeView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        treeView.setCellFactory(tv -> new TreeItemCell());
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null && newVal.getValue().isMarket()) {
                onMarketSelected(newVal.getValue().getMarket());
            }
        });
        VBox.setVgrow(treeView, Priority.ALWAYS);

        leftPanel.getChildren().addAll(searchLabel, searchBox, treeStatusLabel, treeLabel, treeView);
        return leftPanel;
    }

    // ==================== Shared Orderbook Section ====================

    @SuppressWarnings("unchecked")
    private VBox createOrderbookSection(
            TableView<PriceLevel> yesBidTable, ObservableList<PriceLevel> yesBidLevels,
            TableView<PriceLevel> yesAskTable, ObservableList<PriceLevel> yesAskLevels,
            TableView<PriceLevel> noBidTable, ObservableList<PriceLevel> noBidLevels,
            TableView<PriceLevel> noAskTable, ObservableList<PriceLevel> noAskLevels) {

        VBox container = new VBox(10);
        container.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");
        container.setPadding(new Insets(10));

        Label title = new Label("ORDERBOOK");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setTextFill(Color.WHITE);

        // Top row: Yes Bids | Yes Asks
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER);

        VBox yesBidBox = createOrderbookTable("YES BIDS", yesBidTable, yesBidLevels, Color.LIGHTGREEN);
        VBox yesAskBox = createOrderbookTable("YES ASKS", yesAskTable, yesAskLevels, Color.LIGHTCORAL);

        topRow.getChildren().addAll(yesBidBox, yesAskBox);
        HBox.setHgrow(yesBidBox, Priority.ALWAYS);
        HBox.setHgrow(yesAskBox, Priority.ALWAYS);

        // Bottom row: No Bids | No Asks
        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER);

        VBox noBidBox = createOrderbookTable("NO BIDS", noBidTable, noBidLevels, Color.LIGHTGREEN);
        VBox noAskBox = createOrderbookTable("NO ASKS", noAskTable, noAskLevels, Color.LIGHTCORAL);

        bottomRow.getChildren().addAll(noBidBox, noAskBox);
        HBox.setHgrow(noBidBox, Priority.ALWAYS);
        HBox.setHgrow(noAskBox, Priority.ALWAYS);

        container.getChildren().addAll(title, topRow, bottomRow);
        VBox.setVgrow(topRow, Priority.ALWAYS);
        VBox.setVgrow(bottomRow, Priority.ALWAYS);

        return container;
    }

    @SuppressWarnings("unchecked")
    private VBox createOrderbookTable(String title, TableView<PriceLevel> table,
                                       ObservableList<PriceLevel> data, Color accentColor) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: #0f3460; -fx-background-radius: 8;");

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        titleLabel.setTextFill(accentColor);

        TableColumn<PriceLevel, String> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(cellData -> cellData.getValue().priceProperty());
        priceCol.setPrefWidth(60);
        priceCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<PriceLevel, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(cellData -> cellData.getValue().quantityProperty());
        qtyCol.setPrefWidth(60);
        qtyCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().addAll(priceCol, qtyCol);
        table.setItems(data);
        table.setPlaceholder(new Label("No orders"));
        table.setStyle("-fx-background-color: #0a2540;");
        table.setPrefHeight(150);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        box.getChildren().addAll(titleLabel, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        return box;
    }

    // ==================== Risk Tab ====================

    /**
     * Creates a placeholder for the Risk tab while waiting for RiskChecker initialization.
     */
    private VBox createRiskTabPlaceholder() {
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setStyle("-fx-background-color: #1a1a2e;");

        Label label = new Label("Risk Management");
        label.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        label.setTextFill(Color.WHITE);

        Label waiting = new Label("Initializing...");
        waiting.setTextFill(Color.LIGHTGRAY);

        placeholder.getChildren().addAll(label, waiting);
        return placeholder;
    }

    /**
     * Initializes the Risk tab with the RiskConfigPanel after RiskChecker is ready.
     * Must be called from JavaFX Application Thread.
     */
    private void initializeRiskTab() {
        if (riskChecker != null) {
            riskConfigPanel = new RiskConfigPanel(riskChecker);
            riskTab.setContent(riskConfigPanel);
            log("Risk tab initialized");
        }
    }

    // ==================== Market Data Tab ====================

    /**
     * Creates the Market Data tab content with status, subscription, and quotes display.
     */
    @SuppressWarnings("unchecked")
    private VBox createMarketDataTabContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #1a1a2e;");

        // Title
        Label title = new Label("External Market Data");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        // Top row: Status and Authentication side by side
        HBox topRow = new HBox(15);

        // Status section
        VBox statusBox = new VBox(8);
        statusBox.setPadding(new Insets(15));
        statusBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");
        statusBox.setPrefWidth(300);

        Label statusTitle = new Label("CONNECTION STATUS");
        statusTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        statusTitle.setTextFill(Color.LIGHTSKYBLUE);

        marketDataStatusLabel = new Label("Not configured");
        marketDataStatusLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 13));
        marketDataStatusLabel.setTextFill(Color.LIGHTGRAY);

        Button authenticateBtn = new Button("Authenticate");
        authenticateBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        authenticateBtn.setOnAction(e -> authenticateMarketData());

        statusBox.getChildren().addAll(statusTitle, marketDataStatusLabel, authenticateBtn);

        // Subscription section
        VBox subscriptionBox = new VBox(10);
        subscriptionBox.setPadding(new Insets(15));
        subscriptionBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");
        HBox.setHgrow(subscriptionBox, Priority.ALWAYS);

        Label subscriptionTitle = new Label("QUOTE SUBSCRIPTION");
        subscriptionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        subscriptionTitle.setTextFill(Color.LIGHTSKYBLUE);

        Label subscriptionHelp = new Label("Enter ticker symbols (comma-separated):");
        subscriptionHelp.setTextFill(Color.LIGHTGRAY);

        HBox subscriptionInputRow = new HBox(10);
        subscriptionInputRow.setAlignment(Pos.CENTER_LEFT);

        tickerInputField = new TextField();
        tickerInputField.setPromptText("e.g., SPY, QQQ, AAPL, MSFT");
        tickerInputField.setPrefWidth(300);
        tickerInputField.setOnAction(e -> subscribeToTickers());

        Button subscribeBtn = new Button("Subscribe");
        subscribeBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        subscribeBtn.setOnAction(e -> subscribeToTickers());

        Button unsubscribeBtn = new Button("Unsubscribe All");
        unsubscribeBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        unsubscribeBtn.setOnAction(e -> unsubscribeFromTickers());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        refreshBtn.setOnAction(e -> refreshQuotes());

        subscriptionInputRow.getChildren().addAll(tickerInputField, subscribeBtn, unsubscribeBtn, refreshBtn);
        subscriptionBox.getChildren().addAll(subscriptionTitle, subscriptionHelp, subscriptionInputRow);

        topRow.getChildren().addAll(statusBox, subscriptionBox);

        // Quotes table
        VBox quotesBox = new VBox(8);
        quotesBox.setPadding(new Insets(15));
        quotesBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");
        VBox.setVgrow(quotesBox, Priority.ALWAYS);

        Label quotesTitle = new Label("REAL-TIME QUOTES");
        quotesTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        quotesTitle.setTextFill(Color.LIGHTSKYBLUE);

        quotesTable = new TableView<>();
        quotesTable.setItems(quoteRows);

        // Styled placeholder
        Label placeholderLabel = new Label("No quotes - enter tickers above and click Subscribe");
        placeholderLabel.setTextFill(Color.LIGHTGRAY);
        quotesTable.setPlaceholder(placeholderLabel);

        quotesTable.setStyle(
            "-fx-background-color: #0a1628;" +
            "-fx-table-cell-border-color: #1a3a5c;" +
            "-fx-control-inner-background: #0a1628;" +
            "-fx-control-inner-background-alt: #0f2540;"
        );
        quotesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(quotesTable, Priority.ALWAYS);

        // Row factory for alternating colors with high contrast
        quotesTable.setRowFactory(tv -> new TableRow<QuoteRow>() {
            @Override
            protected void updateItem(QuoteRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else {
                    boolean isEven = getIndex() % 2 == 0;
                    if (isEven) {
                        setStyle("-fx-background-color: #0a1628;"); // Darker row
                    } else {
                        setStyle("-fx-background-color: #122a4a;"); // Lighter row
                    }
                }
            }
        });

        // Table columns
        TableColumn<QuoteRow, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(c -> c.getValue().symbolProperty());
        symbolCol.setPrefWidth(80);
        symbolCol.setCellFactory(col -> createQuoteTextCell());

        TableColumn<QuoteRow, String> lastPriceCol = new TableColumn<>("Last");
        lastPriceCol.setCellValueFactory(c -> c.getValue().lastPriceProperty());
        lastPriceCol.setPrefWidth(80);
        lastPriceCol.setCellFactory(col -> createQuoteTextCell());

        TableColumn<QuoteRow, String> changeCol = new TableColumn<>("Change");
        changeCol.setCellValueFactory(c -> c.getValue().changeProperty());
        changeCol.setPrefWidth(80);
        changeCol.setCellFactory(col -> createChangeCell());

        TableColumn<QuoteRow, String> changePctCol = new TableColumn<>("Change %");
        changePctCol.setCellValueFactory(c -> c.getValue().changePercentProperty());
        changePctCol.setPrefWidth(80);
        changePctCol.setCellFactory(col -> createChangeCell());

        TableColumn<QuoteRow, String> bidCol = new TableColumn<>("Bid");
        bidCol.setCellValueFactory(c -> c.getValue().bidProperty());
        bidCol.setPrefWidth(80);
        bidCol.setCellFactory(col -> createQuoteTextCell());

        TableColumn<QuoteRow, String> askCol = new TableColumn<>("Ask");
        askCol.setCellValueFactory(c -> c.getValue().askProperty());
        askCol.setPrefWidth(80);
        askCol.setCellFactory(col -> createQuoteTextCell());

        TableColumn<QuoteRow, String> volumeCol = new TableColumn<>("Volume");
        volumeCol.setCellValueFactory(c -> c.getValue().volumeProperty());
        volumeCol.setPrefWidth(100);
        volumeCol.setCellFactory(col -> createQuoteTextCell());

        TableColumn<QuoteRow, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(c -> c.getValue().timeProperty());
        timeCol.setPrefWidth(100);
        timeCol.setCellFactory(col -> createQuoteTextCell());

        quotesTable.getColumns().addAll(symbolCol, lastPriceCol, changeCol, changePctCol, bidCol, askCol, volumeCol, timeCol);

        quotesBox.getChildren().addAll(quotesTitle, quotesTable);

        content.getChildren().addAll(title, topRow, quotesBox);
        return content;
    }

    /**
     * Creates a table cell with white text for quote display.
     */
    private TableCell<QuoteRow, String> createQuoteTextCell() {
        return new TableCell<QuoteRow, String>() {
            {
                setFont(Font.font("Arial", FontWeight.NORMAL, 13));
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
                setStyle("-fx-padding: 8 5 8 5;");
            }
        };
    }

    /**
     * Creates a table cell for change values (green for positive, red for negative).
     */
    private TableCell<QuoteRow, String> createChangeCell() {
        return new TableCell<QuoteRow, String>() {
            {
                setFont(Font.font("Arial", FontWeight.BOLD, 13));
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-padding: 8 5 8 5;");
                    if (item.startsWith("-")) {
                        setTextFill(Color.web("#ff6b6b")); // Brighter red
                    } else if (item.startsWith("+") || (!item.equals("--") && !item.startsWith("0"))) {
                        setTextFill(Color.web("#69f0ae")); // Brighter green
                    } else {
                        setTextFill(Color.WHITE);
                    }
                }
            }
        };
    }

    /**
     * Subscribe to ticker symbols entered in the input field.
     */
    private void subscribeToTickers() {
        if (marketDataService == null || !marketDataService.isInitialized()) {
            showError("Not Configured", "Market data service is not configured.");
            return;
        }

        if (!marketDataService.isAuthenticated()) {
            showError("Not Authenticated", "Please authenticate first.");
            return;
        }

        String input = tickerInputField.getText().trim().toUpperCase();
        if (input.isEmpty()) {
            showError("Invalid Input", "Please enter at least one ticker symbol.");
            return;
        }

        // Parse tickers
        List<String> tickers = new ArrayList<>();
        for (String ticker : input.split("[,\\s]+")) {
            ticker = ticker.trim();
            if (!ticker.isEmpty()) {
                tickers.add(ticker);
            }
        }

        if (tickers.isEmpty()) {
            showError("Invalid Input", "Please enter valid ticker symbols.");
            return;
        }

        // Unsubscribe from existing subscription if any
        if (marketDataSubscriptionId != null) {
            marketDataService.unsubscribe(marketDataSubscriptionId);
            marketDataSubscriptionId = null;
        }

        log("Subscribing to tickers: " + tickers);

        try {
            marketDataSubscriptionId = marketDataService.subscribe(
                tickers,
                quotes -> Platform.runLater(() -> updateQuotesTable(quotes)),
                error -> Platform.runLater(() -> log("ERROR: Quote update failed: " + error.getMessage()))
            );
            log("Subscription created: " + marketDataSubscriptionId);
        } catch (Exception e) {
            log("ERROR: Failed to subscribe: " + e.getMessage());
            showError("Subscription Failed", e.getMessage());
        }
    }

    /**
     * Unsubscribe from all market data subscriptions.
     */
    private void unsubscribeFromTickers() {
        if (marketDataSubscriptionId != null && marketDataService != null) {
            marketDataService.unsubscribe(marketDataSubscriptionId);
            marketDataSubscriptionId = null;
            log("Unsubscribed from market data");
        }
        quoteRows.clear();
        quoteRowMap.clear();
    }

    /**
     * Refresh quotes on demand (one-time fetch).
     */
    private void refreshQuotes() {
        if (marketDataService == null || !marketDataService.isInitialized()) {
            showError("Not Configured", "Market data service is not configured.");
            return;
        }

        if (!marketDataService.isAuthenticated()) {
            showError("Not Authenticated", "Please authenticate first.");
            return;
        }

        String input = tickerInputField.getText().trim().toUpperCase();
        if (input.isEmpty()) {
            showError("Invalid Input", "Please enter at least one ticker symbol.");
            return;
        }

        // Parse tickers
        List<String> tickers = new ArrayList<>();
        for (String ticker : input.split("[,\\s]+")) {
            ticker = ticker.trim();
            if (!ticker.isEmpty()) {
                tickers.add(ticker);
            }
        }

        if (tickers.isEmpty()) {
            return;
        }

        log("Fetching quotes for: " + tickers);

        CompletableFuture.runAsync(() -> {
            try {
                List<Quote> quotes = marketDataService.getQuotes(tickers);
                Platform.runLater(() -> updateQuotesTable(quotes));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log("ERROR: Failed to fetch quotes: " + e.getMessage());
                });
            }
        }, backgroundExecutor);
    }

    /**
     * Update the quotes table with new quote data.
     */
    private void updateQuotesTable(List<Quote> quotes) {
        for (Quote quote : quotes) {
            String symbol = quote.getSymbol();
            QuoteRow existingRow = quoteRowMap.get(symbol);

            if (existingRow != null) {
                // Update existing row
                existingRow.update(quote);
            } else {
                // Add new row
                QuoteRow newRow = new QuoteRow(quote);
                quoteRowMap.put(symbol, newRow);
                quoteRows.add(newRow);
            }
        }
    }

    /**
     * Authenticate with the market data provider.
     */
    private void authenticateMarketData() {
        if (marketDataService == null || !marketDataService.isInitialized()) {
            showError("Not Configured", "Market data service is not configured.\nUse --marketdata=path/to/config.properties");
            return;
        }

        if (marketDataService.isAuthenticated()) {
            log("Market data already authenticated");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Platform.runLater(() -> {
                    marketDataStatusLabel.setText("Authenticating...");
                    marketDataStatusLabel.setTextFill(Color.YELLOW);
                });

                marketDataService.authenticate();

                Platform.runLater(() -> {
                    marketDataStatusLabel.setText("Authenticated");
                    marketDataStatusLabel.setTextFill(Color.LIGHTGREEN);
                    log("Market data service authenticated successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    marketDataStatusLabel.setText("Authentication failed: " + e.getMessage());
                    marketDataStatusLabel.setTextFill(Color.LIGHTCORAL);
                    log("ERROR: Market data authentication failed: " + e.getMessage());
                });
            }
        }, backgroundExecutor);
    }

    /**
     * Updates the market data status label based on service state.
     * Must be called from JavaFX Application Thread.
     */
    private void updateMarketDataStatus() {
        if (marketDataService == null) {
            marketDataStatusLabel.setText("Not configured");
            marketDataStatusLabel.setTextFill(Color.LIGHTGRAY);
            marketDataConnectionLabel.setText("");
        } else if (!marketDataService.isInitialized()) {
            marketDataStatusLabel.setText("Service not initialized");
            marketDataStatusLabel.setTextFill(Color.YELLOW);
            marketDataConnectionLabel.setText("");
        } else if (marketDataService.isAuthenticated()) {
            String provider = marketDataService.getActiveManager() != null ?
                marketDataService.getActiveManager().getProviderName() : "Unknown";
            marketDataStatusLabel.setText("Connected to " + provider);
            marketDataStatusLabel.setTextFill(Color.LIGHTGREEN);
            marketDataConnectionLabel.setText("● " + provider);
            marketDataConnectionLabel.setTextFill(Color.LIGHTGREEN);
        } else {
            String provider = marketDataService.getActiveManager() != null ?
                marketDataService.getActiveManager().getProviderName() : "Unknown";
            marketDataStatusLabel.setText(provider + " - Not authenticated");
            marketDataStatusLabel.setTextFill(Color.YELLOW);
            marketDataConnectionLabel.setText("● " + provider);
            marketDataConnectionLabel.setTextFill(Color.YELLOW);
        }
    }

    /**
     * Creates StrategyTab instances for all running strategies.
     * Must be called from JavaFX Application Thread.
     */
    private void initializeStrategyTabs() {
        // Clear any existing strategy tabs
        for (StrategyTab tab : strategyTabs) {
            tabPane.getTabs().remove(tab);
            tab.dispose();
        }
        strategyTabs.clear();

        // Remove existing strategy manager tab if present
        if (strategyManagerTab != null) {
            tabPane.getTabs().remove(strategyManagerTab);
            strategyManagerTab = null;
        }

        // Single strategy mode
        if (strategy != null) {
            StrategyTab tab = createStrategyTab(strategy);
            strategyTabs.add(tab);
            tabPane.getTabs().add(tab);
            log("Strategy tab created for: " + strategy.getStrategyName());
        }

        // Multi-strategy mode - use StrategyManagerTab instead of individual tabs
        if (strategyManager != null) {
            strategyManagerTab = new StrategyManagerTab(strategyManager);
            strategyManagerTab.wireManagers(orderManager, positionManager, marketManager);
            strategyManagerTab.setMarketInfoProvider(this::getMarketInfo);
            tabPane.getTabs().add(strategyManagerTab);
            log("Strategy Manager tab created with " + strategyManager.getStrategyCount() + " strategies");
        }
    }

    /**
     * Creates and wires up a StrategyTab for a given strategy.
     */
    private StrategyTab createStrategyTab(TradingStrategy s) {
        StrategyTab tab = new StrategyTab(s);
        tab.wireManagers(orderManager, positionManager, marketManager);
        tab.setMarketInfoProvider(this::getMarketInfo);
        return tab;
    }

    // ==================== Log Area ====================

    private VBox createLogArea() {
        VBox logBox = new VBox(5);
        logBox.setPadding(new Insets(10));

        Label logLabel = new Label("Activity Log");
        logLabel.setTextFill(Color.WHITE);
        logLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(80);
        logArea.setStyle("-fx-control-inner-background: #0f3460; -fx-text-fill: #e0e0e0; -fx-font-family: monospace; -fx-font-size: 10;");

        logBox.getChildren().addAll(logLabel, logArea);
        return logBox;
    }

    // ==================== Cell Factories ====================

    private TableCell<OrderRow, String> createWrappingCell() {
        return new TableCell<OrderRow, String>() {
            private final Text text = new Text();
            {
                text.wrappingWidthProperty().bind(getTableColumn().widthProperty().subtract(10));
                text.setFill(Color.WHITE);
                setGraphic(text);
                setPrefHeight(Control.USE_COMPUTED_SIZE);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                text.setText(empty || item == null ? null : item);
            }
        };
    }

    private TableCell<PositionRow, String> createWrappingCellPosition() {
        return new TableCell<PositionRow, String>() {
            private final Text text = new Text();
            {
                text.wrappingWidthProperty().bind(getTableColumn().widthProperty().subtract(10));
                text.setFill(Color.WHITE);
                setGraphic(text);
                setPrefHeight(Control.USE_COMPUTED_SIZE);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                text.setText(empty || item == null ? null : item);
            }
        };
    }

    private TableCell<OrderRow, String> createWhiteTextCell() {
        return new TableCell<OrderRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
            }
        };
    }

    private TableCell<PositionRow, String> createWhiteTextCellPosition() {
        return new TableCell<PositionRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
            }
        };
    }

    private TableCell<OrderRow, Number> createWhiteNumberCell() {
        return new TableCell<OrderRow, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setTextFill(Color.WHITE);
            }
        };
    }

    private TableCell<PositionRow, Number> createWhiteNumberCellPosition() {
        return new TableCell<PositionRow, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setTextFill(Color.WHITE);
            }
        };
    }

    // ==================== Initialization ====================

    /**
     * Initializes all clients asynchronously in a background thread.
     * Shows a loading overlay while initialization is in progress.
     */
    private void initializeClientsAsync() {
        showLoading("Initializing API clients...");
        log("Starting initialization in background thread...");

        CompletableFuture.runAsync(() -> {
            try {
                initializeClientsSync();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    hideLoading();
                    log("ERROR: Failed to initialize clients: " + e.getMessage());
                    showError("Initialization Failed", e.getMessage());
                });
            }
        }, backgroundExecutor);
    }

    /**
     * Synchronous client initialization - called from background thread.
     */
    private void initializeClientsSync() throws Exception {
        Path privateKeyPath = Path.of(privateKeyFile);
        Platform.runLater(() -> log("Loading private key from: " + privateKeyPath.toAbsolutePath()));

        updateLoadingStatus("Loading API credentials...");
        KalshiAuthenticator authenticator = KalshiAuthenticator.fromFile(apiKeyId, privateKeyPath);

        updateLoadingStatus("Creating WebSocket client...");
        wsClient = OrderbookWebSocketClient.builder()
                .authenticator(authenticator)
                .build();

        updateLoadingStatus("Creating REST API client...");
        kalshiApi = KalshiApi.builder()
                .baseUrl(KalshiClient.DEFAULT_BASE_URL)
                .authenticator(authenticator)
                .build();

        // Get singleton OrderManager
        updateLoadingStatus("Starting Order Manager...");
        orderManager = kalshiApi.getOrderManager();
        orderManager.addOrderChangeListener(event -> {
            Platform.runLater(() -> {
                refreshOrdersTableAsync();
                updateTabTitles();
            });
            if (strategy != null) {
                notifyStrategyOrderChange(event);
            }
        });
        orderManager.start();
        Platform.runLater(() -> log("OrderManager started (singleton)"));

        // Get singleton PositionManager
        updateLoadingStatus("Starting Position Manager...");
        positionManager = kalshiApi.getPositionManager();
        positionManager.addPositionChangeListener(event -> {
            Platform.runLater(() -> {
                refreshPositionsTableAsync();
                updateTabTitles();
            });
            if (strategy != null) {
                notifyStrategyPositionChange(event);
            }
        });
        positionManager.start();
        Platform.runLater(() -> log("PositionManager started (singleton)"));

        // Get singleton MarketManager
        updateLoadingStatus("Creating Market Manager...");
        marketManager = kalshiApi.getMarketManager();
        Platform.runLater(() -> log("MarketManager created (singleton)"));

        // Initialize external market data service if configured
        initializeMarketDataService();

        // Check launch mode: config file (multi-strategy) vs single strategy
        if (configFilePath != null && !configFilePath.isEmpty()) {
            // Multi-strategy mode via config file
            launchStrategiesFromConfigAsync();
        } else {
            // Single strategy mode
            initializeRiskChecker();
            initializeStrategy();

            // Initialize UI tabs on JavaFX thread
            Platform.runLater(() -> {
                initializeRiskTab();
                initializeStrategyTabs();
            });

            hideLoading();
        }

        Platform.runLater(() -> log("All clients initialized successfully"));
    }

    /**
     * Initialize the RiskChecker with application-level configuration.
     * Risk checks are controlled by the application, not by individual strategies.
     * Per-strategy limit overrides can be configured here.
     */
    private void initializeRiskChecker() {
        // Configure risk limits with global defaults and per-strategy overrides
        RiskConfig riskConfig = RiskConfig.builder()
                // Global defaults (apply to all strategies unless overridden)
                .maxOrderQuantity(100)           // Max 100 contracts per order
                .maxOrderNotional(5000)          // Max $50.00 per order (5000 cents)
                .maxPositionQuantity(500)        // Max 500 contracts per position
                .maxPositionNotional(25000)      // Max $250.00 per position (25000 cents)
                // Example: Strategy-specific overrides
                .forStrategy("ExampleStrategy")
                    .maxOrderQuantity(100)       // Same as global for example
                    .maxOrderNotional(5000)
                    .maxPositionQuantity(500)
                    .maxPositionNotional(25000)
                    .done()
                // Add more strategy-specific limits as needed:
                // .forStrategy("AggressiveStrategy")
                //     .maxOrderQuantity(50)     // Lower limit for aggressive strategies
                //     .maxOrderNotional(2500)
                //     .done()
                .build();

        riskChecker = new RiskChecker(riskConfig);

        // Configure position provider so risk checker can validate position limits
        riskChecker.setPositionProvider(ticker -> positionManager.getPosition(ticker));

        // Set the risk checker on the OrderService
        kalshiApi.orders().setRiskChecker(riskChecker);

        log("RiskChecker configured with global limits: maxOrderQty=100, maxOrderNotional=$50, maxPosQty=500, maxPosNotional=$250");
    }

    private void initializeStrategy() {
        if (strategyClassName == null || strategyClassName.isEmpty()) {
            log("No strategy specified. Use --strategy=<class.name> to enable.");
            return;
        }

        try {
            log("Loading strategy: " + strategyClassName);
            Class<?> strategyClass = Class.forName(strategyClassName);

            if (!TradingStrategy.class.isAssignableFrom(strategyClass)) {
                throw new IllegalArgumentException("Class " + strategyClassName + " does not extend TradingStrategy");
            }

            strategy = (TradingStrategy) strategyClass.getDeclaredConstructor().newInstance();
            strategy.initialize(kalshiApi, orderManager, positionManager, marketManager,
                    marketDataService != null ? marketDataService.getActiveManager() : null);

            // Set the current strategy context on the RiskChecker for strategy-specific limits
            if (riskChecker != null) {
                riskChecker.setCurrentStrategy(strategy.getStrategyName());
                log("Risk checker context set to strategy: " + strategy.getStrategyName());
            }

            log("Strategy initialized: " + strategyClassName);
        } catch (ClassNotFoundException e) {
            log("ERROR: Strategy class not found: " + strategyClassName);
            showError("Strategy Error", "Strategy class not found: " + strategyClassName);
            strategy = null;
        } catch (com.kalshi.client.strategy.NoMarketsTrackedException e) {
            // Strategy has no markets to track - don't create a tab for it
            log("WARN: Strategy has no markets to track: " + e.getMessage());
            strategy = null;
        } catch (Exception e) {
            log("ERROR: Failed to initialize strategy: " + e.getMessage());
            showError("Strategy Error", "Failed to initialize strategy: " + e.getMessage());
            strategy = null;
        }
    }

    /**
     * Initialize the external MarketDataService if a configuration file is specified.
     * Called from background thread during initialization.
     */
    private void initializeMarketDataService() {
        if (marketDataConfigPath == null || marketDataConfigPath.isEmpty()) {
            Platform.runLater(() -> log("No market data configuration specified (use --marketdata=config.properties)"));
            return;
        }

        updateLoadingStatus("Initializing market data service...");
        Platform.runLater(() -> log("Initializing MarketDataService from: " + marketDataConfigPath));

        try {
            // Get the singleton instance
            marketDataService = MarketDataService.getInstance();

            // Initialize from config file (passing null for Stage initially)
            marketDataService.initialize(marketDataConfigPath, null);

            if (marketDataService.isInitialized()) {
                Platform.runLater(() -> {
                    log("MarketDataService initialized successfully");
                    String provider = marketDataService.getActiveManager() != null ?
                        marketDataService.getActiveManager().getProviderName() : "Unknown";
                    log("  Provider: " + provider);
                    log("  Auth mode: " + marketDataService.getAuthMode());
                    log("  Auto-auth: " + marketDataService.isAutoAuthEnabled());
                    updateMarketDataStatus();

                    // Set the owner stage for UI dialogs
                    marketDataService.setOwnerStage(primaryStage);

                    // Auto-authenticate if enabled
                    if (marketDataService.isAutoAuthEnabled() && !marketDataService.isAuthenticated()) {
                        autoAuthenticateMarketData();
                    }
                });
            } else {
                Platform.runLater(() -> {
                    log("WARN: MarketDataService initialization incomplete");
                    updateMarketDataStatus();
                });
            }
        } catch (Exception e) {
            Platform.runLater(() -> {
                log("ERROR: Failed to initialize MarketDataService: " + e.getMessage());
                updateMarketDataStatus();
            });
        }
    }

    /**
     * Auto-authenticate market data service at startup.
     * Must be called from JavaFX Application Thread.
     */
    private void autoAuthenticateMarketData() {
        log("Auto-authenticating market data...");
        marketDataStatusLabel.setText("Authenticating...");
        marketDataStatusLabel.setTextFill(Color.YELLOW);
        marketDataConnectionLabel.setText("● Authenticating");
        marketDataConnectionLabel.setTextFill(Color.YELLOW);

        // Run authentication in background but the dialog will show on FX thread
        CompletableFuture.runAsync(() -> {
            try {
                marketDataService.authenticate();
                Platform.runLater(() -> {
                    log("Market data authenticated successfully");
                    updateMarketDataStatus();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    log("ERROR: Market data authentication failed: " + e.getMessage());
                    updateMarketDataStatus();
                });
            }
        }, backgroundExecutor);
    }

    /**
     * Launch multiple strategies from a configuration file asynchronously.
     * This is the multi-strategy mode triggered by --config=path parameter.
     * Already called from background thread, so we just update loading status.
     */
    private void launchStrategiesFromConfigAsync() {
        Platform.runLater(() -> log("=== Multi-Strategy Mode ==="));
        Platform.runLater(() -> log("Loading configuration from: " + configFilePath));

        try {
            // Load configuration
            updateLoadingStatus("Loading strategy configuration...");
            StrategyConfig config = StrategyLauncher.loadConfig(configFilePath);
            Platform.runLater(() -> {
                log("Configuration loaded successfully");
                log("  Series: " + config.getSeriesTickers());
                log("  Strategy class: " + config.getStrategyClassName());
                log("  Max strategies: " + config.getMaxStrategies());
            });

            // Create launcher
            updateLoadingStatus("Creating strategy launcher...");
            strategyLauncher = new StrategyLauncher(config, kalshiApi, orderManager, positionManager, marketManager, null,
                    marketDataService != null ? marketDataService.getActiveManager() : null);

            // Add progress listener to log updates and update loading status
            strategyLauncher.addProgressListener(msg -> {
                Platform.runLater(() -> log(msg));
                updateLoadingStatus(msg);
            });

            // Launch strategies (this handles filtering, strategy creation, and initialization)
            updateLoadingStatus("Filtering events from series...");
            Platform.runLater(() -> log("Launching strategies..."));
            StrategyLauncher.LaunchResult result = strategyLauncher.launch();

            if (result.isSuccess()) {
                strategyManager = result.getStrategyManager();
                riskChecker = strategyLauncher.getRiskChecker();

                Platform.runLater(() -> {
                    log("=== Launch Complete ===");
                    log("  Events matched: " + (result.getInterestList() != null ? result.getInterestList().size() : 0));
                    log("  Strategies created: " + result.getStrategiesCreated());

                    if (result.getMessage() != null) {
                        log("  Note: " + result.getMessage());
                    }

                    // Initialize UI tabs
                    initializeRiskTab();
                    initializeStrategyTabs();
                });
            } else {
                Platform.runLater(() -> {
                    log("ERROR: Strategy launch failed: " + result.getMessage());
                    if (result.getException() != null) {
                        log("  Exception: " + result.getException().getMessage());
                    }
                    showError("Strategy Launch Failed", result.getMessage());
                });
            }

        } catch (Exception e) {
            Platform.runLater(() -> {
                log("ERROR: Failed to launch strategies from config: " + e.getMessage());
                showError("Configuration Error", "Failed to load configuration: " + e.getMessage());
            });
        } finally {
            hideLoading();
        }
    }

    private void notifyStrategyOrderChange(OrderManager.OrderChangeEvent event) {
        if (event.getOrder() == null) return;

        // Notify single strategy
        if (strategy != null) {
            try {
                switch (event.getType()) {
                    case ADDED: strategy.onOrderCreated(event.getOrder()); break;
                    case MODIFIED: strategy.onOrderModified(event.getOrder()); break;
                    case REMOVED: strategy.onOrderRemoved(event.getOrder()); break;
                    default: break;
                }
            } catch (Exception e) {
                log("ERROR: Strategy order callback failed: " + e.getMessage());
            }
        }

        // Notify all strategies in multi-strategy mode
        if (strategyManager != null) {
            for (TradingStrategy s : strategyManager.getAllStrategies()) {
                try {
                    switch (event.getType()) {
                        case ADDED: s.onOrderCreated(event.getOrder()); break;
                        case MODIFIED: s.onOrderModified(event.getOrder()); break;
                        case REMOVED: s.onOrderRemoved(event.getOrder()); break;
                        default: break;
                    }
                } catch (Exception e) {
                    log("ERROR: Strategy " + s.getStrategyName() + " order callback failed: " + e.getMessage());
                }
            }
        }
    }

    private void notifyStrategyPositionChange(PositionManager.PositionChangeEvent event) {
        if (event.getPosition() == null) return;

        // Notify single strategy
        if (strategy != null) {
            try {
                switch (event.getType()) {
                    case ADDED: strategy.onPositionOpened(event.getPosition()); break;
                    case UPDATED: strategy.onPositionUpdated(event.getPosition()); break;
                    case CLOSED: strategy.onPositionClosed(event.getPosition()); break;
                    default: break;
                }
            } catch (Exception e) {
                log("ERROR: Strategy position callback failed: " + e.getMessage());
            }
        }

        // Notify all strategies in multi-strategy mode
        if (strategyManager != null) {
            for (TradingStrategy s : strategyManager.getAllStrategies()) {
                try {
                    switch (event.getType()) {
                        case ADDED: s.onPositionOpened(event.getPosition()); break;
                        case UPDATED: s.onPositionUpdated(event.getPosition()); break;
                        case CLOSED: s.onPositionClosed(event.getPosition()); break;
                        default: break;
                    }
                } catch (Exception e) {
                    log("ERROR: Strategy " + s.getStrategyName() + " position callback failed: " + e.getMessage());
                }
            }
        }
    }

    // ==================== Data Refresh ====================

    private void updateTabTitles() {
        ordersTab.setText("Orders(" + orderRows.size() + ")");
        positionsTab.setText("Positions(" + positionRows.size() + ")");
    }

    /**
     * Refreshes orders table asynchronously - fetches market info in background.
     * Must be called from JavaFX Application Thread.
     */
    private void refreshOrdersTableAsync() {
        List<Order> orders = orderManager.getAllOrders();
        orderRows.clear();

        if (orders.isEmpty()) {
            return;
        }

        // First pass: add rows with cached market info or null
        for (Order order : orders) {
            Market cached = marketInfoCache.get(order.getTicker());
            orderRows.add(new OrderRow(order, cached));
        }

        // Second pass: fetch missing market info in background
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < orders.size(); i++) {
                Order order = orders.get(i);
                String ticker = order.getTicker();
                if (ticker != null && !marketInfoCache.containsKey(ticker)) {
                    Market market = fetchMarketInfoSync(ticker);
                    if (market != null) {
                        final int index = i;
                        Platform.runLater(() -> {
                            if (index < orderRows.size()) {
                                orderRows.set(index, new OrderRow(order, market));
                            }
                        });
                    }
                }
            }
        }, backgroundExecutor);
    }

    /**
     * Refreshes positions table asynchronously - fetches market info in background.
     * Must be called from JavaFX Application Thread.
     */
    private void refreshPositionsTableAsync() {
        List<Position> positions = positionManager.getAllPositions();
        positionRows.clear();

        if (positions.isEmpty()) {
            return;
        }

        // First pass: add rows with cached market info or null
        for (Position position : positions) {
            Market cached = marketInfoCache.get(position.getMarketTicker());
            positionRows.add(new PositionRow(position, cached));
        }

        // Second pass: fetch missing market info in background
        CompletableFuture.runAsync(() -> {
            for (int i = 0; i < positions.size(); i++) {
                Position position = positions.get(i);
                String ticker = position.getMarketTicker();
                if (ticker != null && !marketInfoCache.containsKey(ticker)) {
                    Market market = fetchMarketInfoSync(ticker);
                    if (market != null) {
                        final int index = i;
                        Platform.runLater(() -> {
                            if (index < positionRows.size()) {
                                positionRows.set(index, new PositionRow(position, market));
                            }
                        });
                    }
                }
            }
        }, backgroundExecutor);
    }

    /**
     * Gets market info from local cache or API cache (returns null if not cached).
     * Use this for synchronous access (won't block on API call).
     */
    private Market getMarketInfo(String ticker) {
        if (ticker == null) return null;

        // Check local cache first
        Market cached = marketInfoCache.get(ticker);
        if (cached != null) return cached;

        // Check API cache (doesn't trigger fetch)
        if (kalshiApi != null) {
            cached = kalshiApi.cache().getMarketCached(ticker);
            if (cached != null) {
                marketInfoCache.put(ticker, cached);
                return cached;
            }
        }

        return null;
    }

    /**
     * Fetches market info synchronously using API cache (blocks until API responds if not cached).
     * Should only be called from background threads.
     */
    private Market fetchMarketInfoSync(String ticker) {
        if (ticker == null) return null;

        // Check local cache first
        Market cached = marketInfoCache.get(ticker);
        if (cached != null) return cached;

        try {
            // Use API's cached method - fetches if not cached, returns cached if available
            Market market = kalshiApi.getMarketCached(ticker);
            if (market != null) {
                marketInfoCache.put(ticker, market);
            }
            return market;
        } catch (Exception e) {
            Platform.runLater(() -> log("Failed to fetch market info for " + ticker));
            return null;
        }
    }

    /**
     * Fetches market info asynchronously.
     * Returns a CompletableFuture that completes with the Market or null.
     */
    private CompletableFuture<Market> fetchMarketInfoAsync(String ticker) {
        if (ticker == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Check local cache first
        Market cached = marketInfoCache.get(ticker);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Check API cache (doesn't trigger fetch)
        if (kalshiApi != null) {
            cached = kalshiApi.cache().getMarketCached(ticker);
            if (cached != null) {
                marketInfoCache.put(ticker, cached);
                return CompletableFuture.completedFuture(cached);
            }
        }

        return CompletableFuture.supplyAsync(() -> fetchMarketInfoSync(ticker), backgroundExecutor);
    }

    private void cancelOrder(OrderRow orderRow) {
        String orderId = orderRow.getOrderId();
        String ticker = orderRow.getTicker();

        if (orderId == null || orderId.isEmpty()) {
            log("ERROR: Cannot cancel order - no order ID");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Order");
        confirm.setHeaderText("Cancel Order?");
        confirm.setContentText("Are you sure you want to cancel this order?\n\nTicker: " + ticker + "\nOrder ID: " + orderId);

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        kalshiApi.orders().cancelOrder(orderId);
                        Platform.runLater(() -> {
                            log("Order canceled: " + orderId);
                            refreshOrdersTableAsync();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            log("ERROR: Failed to cancel order: " + e.getMessage());
                            showError("Cancel Failed", e.getMessage());
                        });
                    }
                }).start();
            }
        });
    }

    // ==================== Market Selection & Orderbook ====================

    private void searchSeries() {
        String ticker = seriesTickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) {
            showError("Invalid Input", "Please enter a series ticker");
            return;
        }

        treeStatusLabel.setText("Searching for series: " + ticker + "...");
        searchButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                // Use cached access for series
                Series series = kalshiApi.getSeriesCached(ticker);

                // Use cached access for events (auto-pagination with caching)
                List<Event> events = kalshiApi.getEventsBySeriesCached(ticker);

                Platform.runLater(() -> {
                    buildTree(series, events);
                    treeStatusLabel.setText("Found " + events.size() + " event(s) for " + ticker);
                    searchButton.setDisable(false);
                    log("Loaded series: " + series.getTitle() + " with " + events.size() + " events (cached)");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    treeStatusLabel.setText("Error: " + e.getMessage());
                    searchButton.setDisable(false);
                    log("ERROR: Failed to search series: " + e.getMessage());
                });
            }
        }, backgroundExecutor);
    }

    private void buildTree(Series series, List<Event> events) {
        TreeItemData seriesData = new TreeItemData(TreeItemType.SERIES, series.getTitle(), series);
        TreeItem<TreeItemData> rootItem = new TreeItem<>(seriesData);
        rootItem.setExpanded(true);

        Instant oneDayAgo = Instant.now().minusSeconds(24 * 60 * 60);
        Instant twoDaysAgo = Instant.now().minusSeconds(2 * 24 * 60 * 60);

        for (Event event : events) {
            if (event.getStrikeDate() != null && event.getStrikeDate().isBefore(oneDayAgo)) {
                continue;
            }

            TreeItemData eventData = new TreeItemData(TreeItemType.EVENT, event.getTitle(), event);
            TreeItem<TreeItemData> eventItem = new TreeItem<>(eventData);

            List<Market> markets = event.getMarkets();
            if (markets != null) {
                for (Market market : markets) {
                    Instant expiration = market.getExpectedExpirationTime();
                    if (expiration != null && expiration.isBefore(twoDaysAgo)) {
                        continue;
                    }

                    TreeItemData marketData = new TreeItemData(TreeItemType.MARKET, market.getTitle(), market);
                    TreeItem<TreeItemData> marketItem = new TreeItem<>(marketData);
                    eventItem.getChildren().add(marketItem);
                }
            }

            if (!eventItem.getChildren().isEmpty()) {
                eventItem.setExpanded(true);
                rootItem.getChildren().add(eventItem);
            }
        }

        treeView.setRoot(rootItem);
    }

    private void onMarketSelected(Market market) {
        if (market == null) return;

        // Unsubscribe from current if any
        if (currentTicker != null && isSubscribed) {
            wsClient.unsubscribe(currentTicker);
        }

        // Update market info in title bar
        displayMarketInfo(market);

        // Subscribe to orderbook
        subscribeToMarket(market.getTicker());
    }

    private void displayMarketInfo(Market market) {
        String title = market.getTitle() != null ? market.getTitle() : market.getTicker();
        marketTitleLabel.setText(title);

        String subtitle = market.getSubtitle();
        marketSubtitleLabel.setText(subtitle != null ? subtitle : "");

        String status = market.getStatus() != null ? market.getStatus().toUpperCase() : "UNKNOWN";
        marketStatusLabel.setText(" [" + status + "]");
        if ("ACTIVE".equalsIgnoreCase(status) || "OPEN".equalsIgnoreCase(status)) {
            marketStatusLabel.setTextFill(Color.LIGHTGREEN);
        } else if ("CLOSED".equalsIgnoreCase(status) || "SETTLED".equalsIgnoreCase(status)) {
            marketStatusLabel.setTextFill(Color.LIGHTCORAL);
        } else {
            marketStatusLabel.setTextFill(Color.YELLOW);
        }
    }

    private void subscribeToMarket(String ticker) {
        if (wsClient == null) {
            showError("Not Ready", "WebSocket client not initialized");
            return;
        }

        currentTicker = ticker;
        clearOrderbook();

        log("Subscribing to: " + ticker);

        wsClient.subscribe(ticker, new OrderbookUpdateConsumer() {
            @Override
            public void onConnected() {
                Platform.runLater(() -> {
                    connectionLabel.setText("● Connected");
                    connectionLabel.setTextFill(Color.LIGHTGREEN);
                    isSubscribed = true;
                    log("WebSocket connected for " + ticker);
                });
            }

            @Override
            public void onSnapshot(OrderbookSnapshot snapshot) {
                Platform.runLater(() -> {
                    handleSnapshot(snapshot);
                    log("Received orderbook snapshot for " + ticker);
                });
            }

            @Override
            public void onDelta(OrderbookDelta delta) {
                Platform.runLater(() -> handleDelta(delta));
            }

            @Override
            public void onDisconnected(int code, String reason) {
                Platform.runLater(() -> {
                    connectionLabel.setText("● Disconnected");
                    connectionLabel.setTextFill(Color.RED);
                    isSubscribed = false;
                    log("Disconnected: " + code + " - " + reason);
                });
            }

            @Override
            public void onError(Throwable error) {
                Platform.runLater(() -> log("ERROR: " + error.getMessage()));
            }
        });

        isSubscribed = true;
    }

    private void handleSnapshot(OrderbookSnapshot snapshot) {
        yesBids.clear();
        if (snapshot.getYes() != null) {
            for (List<Number> level : snapshot.getYes()) {
                yesBids.put(level.get(0).intValue(), level.get(1).intValue());
            }
        }

        noBids.clear();
        if (snapshot.getNo() != null) {
            for (List<Number> level : snapshot.getNo()) {
                noBids.put(level.get(0).intValue(), level.get(1).intValue());
            }
        }

        refreshAllOrderbooks();
    }

    private void handleDelta(OrderbookDelta delta) {
        Map<Integer, Integer> bids = delta.isYesSide() ? yesBids : noBids;
        int price = delta.getPrice();
        int currentQty = bids.getOrDefault(price, 0);
        int newQty = currentQty + delta.getDelta();

        if (newQty <= 0) {
            bids.remove(price);
        } else {
            bids.put(price, newQty);
        }

        refreshAllOrderbooks();
    }

    private void refreshAllOrderbooks() {
        // Refresh all three tabs' orderbook displays
        refreshOrderbookTab(ordersYesBidLevels, ordersYesAskLevels, ordersNoBidLevels, ordersNoAskLevels);
        refreshOrderbookTab(positionsYesBidLevels, positionsYesAskLevels, positionsNoBidLevels, positionsNoAskLevels);
        refreshOrderbookTab(marketsYesBidLevels, marketsYesAskLevels, marketsNoBidLevels, marketsNoAskLevels);
    }

    private void refreshOrderbookTab(ObservableList<PriceLevel> yesBidLevels, ObservableList<PriceLevel> yesAskLevels,
                                      ObservableList<PriceLevel> noBidLevels, ObservableList<PriceLevel> noAskLevels) {
        // YES BIDS
        yesBidLevels.clear();
        yesBids.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .limit(MAX_ORDERBOOK_LEVELS)
                .forEach(e -> yesBidLevels.add(new PriceLevel(e.getKey(), e.getValue())));

        // NO BIDS
        noBidLevels.clear();
        noBids.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .limit(MAX_ORDERBOOK_LEVELS)
                .forEach(e -> noBidLevels.add(new PriceLevel(e.getKey(), e.getValue())));

        // YES ASKS (derived from NO bids)
        yesAskLevels.clear();
        noBids.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .limit(MAX_ORDERBOOK_LEVELS)
                .forEach(e -> yesAskLevels.add(new PriceLevel(100 - e.getKey(), e.getValue())));

        // NO ASKS (derived from YES bids)
        noAskLevels.clear();
        yesBids.entrySet().stream()
                .sorted((a, b) -> b.getKey().compareTo(a.getKey()))
                .limit(MAX_ORDERBOOK_LEVELS)
                .forEach(e -> noAskLevels.add(new PriceLevel(100 - e.getKey(), e.getValue())));
    }

    private void clearOrderbook() {
        yesBids.clear();
        noBids.clear();
        refreshAllOrderbooks();
    }

    // ==================== Utilities ====================

    private String formatInstant(Instant instant) {
        if (instant == null) return "--";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(formatter);
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        String logMessage = "[" + timestamp + "] " + message + "\n";
        logArea.appendText(logMessage);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void shutdown() {
        // Dispose strategy tabs
        for (StrategyTab tab : strategyTabs) {
            try {
                tab.dispose();
            } catch (Exception e) {
                log("ERROR: StrategyTab dispose failed: " + e.getMessage());
            }
        }
        strategyTabs.clear();

        // Dispose risk config panel
        if (riskConfigPanel != null) {
            try {
                riskConfigPanel.dispose();
            } catch (Exception e) {
                log("ERROR: RiskConfigPanel dispose failed: " + e.getMessage());
            }
        }

        // Shutdown single strategy
        if (strategy != null) {
            try {
                strategy.shutdown();
                log("Strategy shutdown complete");
            } catch (Exception e) {
                log("ERROR: Strategy shutdown failed: " + e.getMessage());
            }
        }

        // Shutdown multi-strategy mode
        if (strategyLauncher != null) {
            try {
                strategyLauncher.shutdown();
                log("StrategyLauncher shutdown complete");
            } catch (Exception e) {
                log("ERROR: StrategyLauncher shutdown failed: " + e.getMessage());
            }
        }

        if (wsClient != null) {
            wsClient.close();
        }
        if (orderManager != null) {
            orderManager.shutdown();
        }
        if (positionManager != null) {
            positionManager.stop();
        }
        if (marketManager != null) {
            marketManager.shutdown();
        }

        // Shutdown external market data service
        if (marketDataService != null) {
            try {
                marketDataService.shutdown();
                log("MarketDataService shutdown complete");
            } catch (Exception e) {
                log("ERROR: MarketDataService shutdown failed: " + e.getMessage());
            }
        }

        // Shutdown background executor
        backgroundExecutor.shutdownNow();
    }

    // ==================== Inner Classes ====================

    enum TreeItemType {
        SERIES, EVENT, MARKET
    }

    static class TreeItemData {
        private final TreeItemType type;
        private final String displayText;
        private final Object data;

        TreeItemData(TreeItemType type, String displayText, Object data) {
            this.type = type;
            this.displayText = displayText;
            this.data = data;
        }

        TreeItemType getType() { return type; }
        String getDisplayText() { return displayText; }
        boolean isMarket() { return type == TreeItemType.MARKET; }
        Market getMarket() { return type == TreeItemType.MARKET ? (Market) data : null; }
        Event getEvent() { return type == TreeItemType.EVENT ? (Event) data : null; }
        Series getSeries() { return type == TreeItemType.SERIES ? (Series) data : null; }

        @Override
        public String toString() { return displayText; }
    }

    class TreeItemCell extends TreeCell<TreeItemData> {
        @Override
        protected void updateItem(TreeItemData item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
            } else {
                VBox content = new VBox(2);
                content.setPadding(new Insets(3));

                switch (item.getType()) {
                    case SERIES:
                        Series series = item.getSeries();
                        Label seriesTitle = new Label(series.getTitle());
                        seriesTitle.setFont(Font.font("Arial", FontWeight.BOLD, 13));
                        seriesTitle.setTextFill(Color.GOLD);
                        content.getChildren().add(seriesTitle);
                        break;

                    case EVENT:
                        Event event = item.getEvent();
                        Label eventTitle = new Label(event.getTitle());
                        eventTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
                        eventTitle.setTextFill(Color.LIGHTSKYBLUE);
                        eventTitle.setWrapText(true);
                        content.getChildren().add(eventTitle);
                        break;

                    case MARKET:
                        Market market = item.getMarket();
                        HBox titleRow = new HBox(8);
                        titleRow.setAlignment(Pos.CENTER_LEFT);

                        Label marketTitle = new Label(market.getTitle());
                        marketTitle.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
                        marketTitle.setTextFill(Color.WHITE);
                        marketTitle.setWrapText(true);

                        String status = market.getStatus() != null ? market.getStatus().toUpperCase() : "";
                        if (!status.isEmpty()) {
                            Label statusBadge = new Label(status);
                            statusBadge.setFont(Font.font("Arial", FontWeight.BOLD, 9));
                            statusBadge.setPadding(new Insets(1, 4, 1, 4));
                            if ("ACTIVE".equalsIgnoreCase(status) || "OPEN".equalsIgnoreCase(status)) {
                                statusBadge.setStyle("-fx-background-color: #2e7d32; -fx-background-radius: 3;");
                                statusBadge.setTextFill(Color.WHITE);
                            } else if ("CLOSED".equalsIgnoreCase(status) || "SETTLED".equalsIgnoreCase(status)) {
                                statusBadge.setStyle("-fx-background-color: #c62828; -fx-background-radius: 3;");
                                statusBadge.setTextFill(Color.WHITE);
                            } else {
                                statusBadge.setStyle("-fx-background-color: #f9a825; -fx-background-radius: 3;");
                                statusBadge.setTextFill(Color.BLACK);
                            }
                            titleRow.getChildren().addAll(marketTitle, statusBadge);
                        } else {
                            titleRow.getChildren().add(marketTitle);
                        }
                        content.getChildren().add(titleRow);
                        break;
                }

                setText(null);
                setGraphic(content);
                setStyle("-fx-background-color: transparent;");
            }
        }
    }

    public static class PriceLevel {
        private final SimpleStringProperty price;
        private final SimpleIntegerProperty quantity;

        public PriceLevel(int priceInCents, int quantity) {
            this.price = new SimpleStringProperty(priceInCents + "¢");
            this.quantity = new SimpleIntegerProperty(quantity);
        }

        public SimpleStringProperty priceProperty() { return price; }
        public SimpleIntegerProperty quantityProperty() { return quantity; }
    }

    public static class OrderRow {
        private final String orderId;
        private final SimpleStringProperty ticker;
        private final SimpleStringProperty marketDescription;
        private final SimpleStringProperty side;
        private final SimpleStringProperty action;
        private final SimpleIntegerProperty price;
        private final SimpleIntegerProperty quantity;

        public OrderRow(Order order, Market market) {
            this.orderId = order.getOrderId();
            this.ticker = new SimpleStringProperty(order.getTicker());
            this.marketDescription = new SimpleStringProperty(buildMarketDescription(market));
            this.side = new SimpleStringProperty(order.getSide() != null ? order.getSide().toUpperCase() : "");
            this.action = new SimpleStringProperty(order.getAction() != null ? order.getAction().toUpperCase() : "");
            Integer priceVal = order.getYesPrice() != null ? order.getYesPrice() : order.getNoPrice();
            this.price = new SimpleIntegerProperty(priceVal != null ? priceVal : 0);
            this.quantity = new SimpleIntegerProperty(order.getRemainingCount() != null ? order.getRemainingCount() : 0);
        }

        private static String buildMarketDescription(Market market) {
            if (market == null) return "";
            StringBuilder sb = new StringBuilder();
            if (market.getTitle() != null) sb.append(market.getTitle());
            if (market.getSubtitle() != null && !market.getSubtitle().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(market.getSubtitle());
            }
            if (market.getYesSubTitle() != null && !market.getYesSubTitle().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("Yes: ").append(market.getYesSubTitle());
            }
            return sb.toString();
        }

        public String getOrderId() { return orderId; }
        public String getTicker() { return ticker.get(); }
        public SimpleStringProperty marketDescriptionProperty() { return marketDescription; }
        public SimpleStringProperty sideProperty() { return side; }
        public SimpleStringProperty actionProperty() { return action; }
        public SimpleIntegerProperty priceProperty() { return price; }
        public SimpleIntegerProperty quantityProperty() { return quantity; }
    }

    public static class PositionRow {
        private final SimpleStringProperty ticker;
        private final SimpleStringProperty marketDescription;
        private final SimpleIntegerProperty position;
        private final SimpleStringProperty cost;
        private final SimpleStringProperty pnl;

        public PositionRow(Position pos, Market market) {
            this.ticker = new SimpleStringProperty(pos.getMarketTicker());
            this.marketDescription = new SimpleStringProperty(buildMarketDescription(market));
            this.position = new SimpleIntegerProperty(pos.getPosition() != null ? pos.getPosition() : 0);
            this.cost = new SimpleStringProperty("$" + pos.getPositionCostDollars().toString());
            this.pnl = new SimpleStringProperty("$" + pos.getRealizedPnlDollars().toString());
        }

        private static String buildMarketDescription(Market market) {
            if (market == null) return "";
            StringBuilder sb = new StringBuilder();
            if (market.getTitle() != null) sb.append(market.getTitle());
            if (market.getSubtitle() != null && !market.getSubtitle().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(market.getSubtitle());
            }
            if (market.getYesSubTitle() != null && !market.getYesSubTitle().isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("Yes: ").append(market.getYesSubTitle());
            }
            return sb.toString();
        }

        public String getTicker() { return ticker.get(); }
        public SimpleStringProperty marketDescriptionProperty() { return marketDescription; }
        public SimpleIntegerProperty positionProperty() { return position; }
        public SimpleStringProperty costProperty() { return cost; }
        public SimpleStringProperty pnlProperty() { return pnl; }
    }

    /**
     * Row model for displaying market data quotes in the table.
     */
    public static class QuoteRow {
        private final SimpleStringProperty symbol;
        private final SimpleStringProperty lastPrice;
        private final SimpleStringProperty change;
        private final SimpleStringProperty changePercent;
        private final SimpleStringProperty bid;
        private final SimpleStringProperty ask;
        private final SimpleStringProperty volume;
        private final SimpleStringProperty time;

        public QuoteRow(Quote quote) {
            this.symbol = new SimpleStringProperty(quote.getSymbol());
            this.lastPrice = new SimpleStringProperty(formatPrice(quote.getLastPrice()));
            this.change = new SimpleStringProperty(formatChange(quote.getChange()));
            this.changePercent = new SimpleStringProperty(formatChangePercent(quote.getChangePercent()));
            this.bid = new SimpleStringProperty(formatPrice(quote.getBid()));
            this.ask = new SimpleStringProperty(formatPrice(quote.getAsk()));
            this.volume = new SimpleStringProperty(formatVolume(quote.getVolume()));
            this.time = new SimpleStringProperty(formatTime(quote.getTimestamp()));
        }

        public void update(Quote quote) {
            lastPrice.set(formatPrice(quote.getLastPrice()));
            change.set(formatChange(quote.getChange()));
            changePercent.set(formatChangePercent(quote.getChangePercent()));
            bid.set(formatPrice(quote.getBid()));
            ask.set(formatPrice(quote.getAsk()));
            volume.set(formatVolume(quote.getVolume()));
            time.set(formatTime(quote.getTimestamp()));
        }

        private static String formatPrice(Double price) {
            if (price == null) return "--";
            return String.format("%.2f", price);
        }

        private static String formatChange(Double change) {
            if (change == null) return "--";
            return change >= 0 ? String.format("+%.2f", change) : String.format("%.2f", change);
        }

        private static String formatChangePercent(Double pct) {
            if (pct == null) return "--";
            return pct >= 0 ? String.format("+%.2f%%", pct) : String.format("%.2f%%", pct);
        }

        private static String formatVolume(Long volume) {
            if (volume == null) return "--";
            if (volume >= 1_000_000) {
                return String.format("%.2fM", volume / 1_000_000.0);
            } else if (volume >= 1_000) {
                return String.format("%.1fK", volume / 1_000.0);
            }
            return volume.toString();
        }

        private static String formatTime(java.time.Instant timestamp) {
            if (timestamp == null) return "--";
            return java.time.LocalDateTime.ofInstant(timestamp, java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        public SimpleStringProperty symbolProperty() { return symbol; }
        public SimpleStringProperty lastPriceProperty() { return lastPrice; }
        public SimpleStringProperty changeProperty() { return change; }
        public SimpleStringProperty changePercentProperty() { return changePercent; }
        public SimpleStringProperty bidProperty() { return bid; }
        public SimpleStringProperty askProperty() { return ask; }
        public SimpleStringProperty volumeProperty() { return volume; }
        public SimpleStringProperty timeProperty() { return time; }
    }
}
