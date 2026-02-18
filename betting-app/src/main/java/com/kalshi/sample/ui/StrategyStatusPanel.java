package com.kalshi.sample.ui;

import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.ManagedMarket;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.strategy.EventStrategy;
import com.kalshi.client.strategy.LogEntry;
import com.kalshi.client.strategy.StrategyActivityLog;
import com.kalshi.client.strategy.TradingStrategy;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A panel for displaying the complete status of a trading strategy.
 * Includes:
 * - Event title, open time, expected close time
 * - Activity log (strategy-specific)
 * - Order blotter for event markets
 * - Position blotter for event markets
 * - Scrollable orderbook displays for all strategy markets (sorted by volume)
 */
public class StrategyStatusPanel extends VBox {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TradingStrategy strategy;

    // Header labels
    private Label eventTitleLabel;
    private Label marketDataLabel;  // Real-time underlying data display
    private Label strategyTypeLabel;
    private Label openTimeLabel;
    private Label closeTimeLabel;
    private Label statusLabel;
    private Button viewEventDetailsButton;

    // Activity log
    private ListView<LogEntry> activityLogView;
    private ObservableList<LogEntry> activityLogItems = FXCollections.observableArrayList();
    private Consumer<LogEntry> logListener;

    // Blotters
    private OrderBlotterTable orderBlotter;
    private PositionBlotterTable positionBlotter;

    // Orderbooks - scrollable container for all markets
    private ScrollPane orderbookScrollPane;
    private HBox orderbookContainer;
    private Map<String, OrderbookMiniPanel> orderbookPanelMap = new LinkedHashMap<>();

    // Data providers
    private Function<String, Market> marketInfoProvider;
    private MarketManager marketManager;

    // Cached market data for sorting by volume
    private Map<String, Market> marketCache = new HashMap<>();

    public StrategyStatusPanel(TradingStrategy strategy) {
        this.strategy = strategy;

        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #1a1a2e;");

        buildUI();
        setupLogListener();
        refreshHeader();
    }

    private void buildUI() {
        // Header section
        VBox header = createHeaderSection();

        // Activity log section
        VBox activityLogSection = createActivityLogSection();

        // Blotters section (side by side)
        HBox blottersSection = createBlottersSection();

        // Orderbooks section with scroll
        VBox orderbooksSection = createOrderbooksSection();

        getChildren().addAll(header, activityLogSection, blottersSection, orderbooksSection);

        // Allow orderbooks to grow
        VBox.setVgrow(orderbooksSection, Priority.ALWAYS);
    }

    private VBox createHeaderSection() {
        VBox header = new VBox(5);
        header.setPadding(new Insets(8));
        header.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        HBox topRow = new HBox(15);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Event title (or strategy name for non-EventStrategy)
        String displayTitle;
        if (strategy instanceof EventStrategy) {
            displayTitle = ((EventStrategy) strategy).getEventTitle();
        } else {
            displayTitle = strategy.getStrategyName();
        }
        eventTitleLabel = new Label(displayTitle);
        eventTitleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        eventTitleLabel.setTextFill(Color.GOLD);
        addCopyContextMenu(eventTitleLabel);

        // Market data label (real-time underlying data)
        marketDataLabel = new Label("");
        marketDataLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        marketDataLabel.setTextFill(Color.LIGHTSKYBLUE);
        marketDataLabel.setPadding(new Insets(0, 10, 0, 10));
        addCopyContextMenu(marketDataLabel);

        // Strategy type badge
        strategyTypeLabel = new Label(strategy.getClass().getSimpleName());
        strategyTypeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        strategyTypeLabel.setTextFill(Color.LIGHTGRAY);
        strategyTypeLabel.setPadding(new Insets(2, 6, 2, 6));
        strategyTypeLabel.setStyle("-fx-background-color: #333; -fx-background-radius: 3;");
        addCopyContextMenu(strategyTypeLabel);

        statusLabel = new Label("RUNNING");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        statusLabel.setTextFill(Color.LIGHTGREEN);
        statusLabel.setPadding(new Insets(2, 8, 2, 8));
        statusLabel.setStyle("-fx-background-color: #2e7d32; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // View Event Details button
        viewEventDetailsButton = new Button("View Event Details");
        viewEventDetailsButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-font-size: 10;");
        viewEventDetailsButton.setOnAction(e -> showEventDetails());
        if (!(strategy instanceof EventStrategy)) {
            viewEventDetailsButton.setVisible(false);
        }

        topRow.getChildren().addAll(eventTitleLabel, marketDataLabel, strategyTypeLabel, statusLabel, spacer, viewEventDetailsButton);

        HBox timeRow = new HBox(20);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        openTimeLabel = new Label("Open: --");
        openTimeLabel.setTextFill(Color.LIGHTGRAY);
        openTimeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        addCopyContextMenu(openTimeLabel);

        closeTimeLabel = new Label("Close: --");
        closeTimeLabel.setTextFill(Color.LIGHTGRAY);
        closeTimeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        addCopyContextMenu(closeTimeLabel);

        timeRow.getChildren().addAll(openTimeLabel, closeTimeLabel);

        header.getChildren().addAll(topRow, timeRow);
        return header;
    }

    private VBox createActivityLogSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label title = new Label("ACTIVITY LOG");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        title.setTextFill(Color.LIGHTSKYBLUE);

        activityLogView = new ListView<>(activityLogItems);
        activityLogView.setPrefHeight(100);
        activityLogView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        activityLogView.setCellFactory(lv -> new LogEntryCell());

        // Add copy context menu to activity log
        ContextMenu logContextMenu = new ContextMenu();
        MenuItem copySelectedItem = new MenuItem("Copy Selected");
        copySelectedItem.setOnAction(e -> {
            LogEntry selected = activityLogView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyToClipboard(selected.toString());
            }
        });
        MenuItem copyAllItem = new MenuItem("Copy All");
        copyAllItem.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            for (LogEntry entry : activityLogItems) {
                sb.append(entry.toString()).append("\n");
            }
            copyToClipboard(sb.toString());
        });
        logContextMenu.getItems().addAll(copySelectedItem, copyAllItem);
        activityLogView.setContextMenu(logContextMenu);

        section.getChildren().addAll(title, activityLogView);
        return section;
    }

    private HBox createBlottersSection() {
        HBox section = new HBox(10);

        // Orders
        VBox ordersBox = new VBox(5);
        ordersBox.setPadding(new Insets(8));
        ordersBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label ordersTitle = new Label("ORDERS");
        ordersTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        ordersTitle.setTextFill(Color.LIGHTSKYBLUE);

        orderBlotter = new OrderBlotterTable();
        orderBlotter.setPrefHeight(100);

        ordersBox.getChildren().addAll(ordersTitle, orderBlotter);
        VBox.setVgrow(orderBlotter, Priority.ALWAYS);
        HBox.setHgrow(ordersBox, Priority.ALWAYS);

        // Positions
        VBox positionsBox = new VBox(5);
        positionsBox.setPadding(new Insets(8));
        positionsBox.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label positionsTitle = new Label("POSITIONS");
        positionsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        positionsTitle.setTextFill(Color.LIGHTGREEN);

        positionBlotter = new PositionBlotterTable();
        positionBlotter.setPrefHeight(100);

        positionsBox.getChildren().addAll(positionsTitle, positionBlotter);
        VBox.setVgrow(positionBlotter, Priority.ALWAYS);
        HBox.setHgrow(positionsBox, Priority.ALWAYS);

        section.getChildren().addAll(ordersBox, positionsBox);
        return section;
    }

    private VBox createOrderbooksSection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("MARKET ORDERBOOKS");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("(sorted by volume, click for details)");
        subtitle.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        subtitle.setTextFill(Color.GRAY);

        titleRow.getChildren().addAll(title, subtitle);

        // Scrollable container for orderbooks
        orderbookContainer = new HBox(10);
        orderbookContainer.setAlignment(Pos.CENTER_LEFT);
        orderbookContainer.setPadding(new Insets(5));

        orderbookScrollPane = new ScrollPane(orderbookContainer);
        orderbookScrollPane.setFitToHeight(true);
        orderbookScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        orderbookScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        orderbookScrollPane.setStyle("-fx-background: #16213e; -fx-background-color: #16213e;");
        orderbookScrollPane.setPrefHeight(200);

        section.getChildren().addAll(titleRow, orderbookScrollPane);
        VBox.setVgrow(orderbookScrollPane, Priority.ALWAYS);

        return section;
    }

    private void setupLogListener() {
        StrategyActivityLog activityLog = strategy.getActivityLog();
        if (activityLog != null) {
            // Add existing entries
            List<LogEntry> existing = activityLog.getAllEntries();
            activityLogItems.addAll(existing);

            // Listen for new entries
            logListener = entry -> Platform.runLater(() -> {
                activityLogItems.add(0, entry);
                // Keep max 50 entries displayed
                while (activityLogItems.size() > 50) {
                    activityLogItems.remove(activityLogItems.size() - 1);
                }
            });
            activityLog.addListener(logListener);
        }
    }

    /**
     * Update the market data label text.
     * This should be called from the JavaFX Application Thread.
     *
     * @param labelText The text to display
     */
    public void updateMarketDataLabel(String labelText) {
        if (marketDataLabel != null) {
            marketDataLabel.setText(labelText != null ? labelText : "");
        }
    }

    /**
     * Refresh the header with current strategy timing.
     */
    public void refreshHeader() {
        // Update event title in case it wasn't loaded yet
        if (strategy instanceof EventStrategy) {
            String eventTitle = ((EventStrategy) strategy).getEventTitle();
            eventTitleLabel.setText(eventTitle);
        }

        // Update market data label with current value
        updateMarketDataLabel(strategy.getMarketDataLabelText());

        Instant openTime = strategy.getOpenTime();
        Instant closeTime = strategy.getExpectedCloseTime();

        openTimeLabel.setText("Open: " + formatDateTime(openTime));
        closeTimeLabel.setText("Close: " + formatDateTime(closeTime));
    }

    /**
     * Set the market info provider for blotters.
     */
    public void setMarketInfoProvider(Function<String, Market> provider) {
        this.marketInfoProvider = provider;
        orderBlotter.setMarketInfoProvider(provider);
        positionBlotter.setMarketInfoProvider(provider);
    }

    /**
     * Set the market manager for orderbook data.
     */
    public void setMarketManager(MarketManager marketManager) {
        this.marketManager = marketManager;
    }

    /**
     * Update orders display (filtered to strategy's tracked markets).
     */
    public void updateOrders(List<Order> allOrders) {
        // Use all tracked markets from EventStrategy
        Set<String> trackedTickers;
        if (strategy instanceof EventStrategy) {
            trackedTickers = ((EventStrategy) strategy).getTrackedTickers();
        } else {
            trackedTickers = strategy.getDisplayMarketTickers();
        }
        orderBlotter.setFilterTickers(trackedTickers);
        orderBlotter.updateOrders(allOrders);
    }

    /**
     * Update positions display (filtered to strategy's tracked markets).
     */
    public void updatePositions(List<Position> allPositions) {
        // Use all tracked markets from EventStrategy
        Set<String> trackedTickers;
        if (strategy instanceof EventStrategy) {
            trackedTickers = ((EventStrategy) strategy).getTrackedTickers();
        } else {
            trackedTickers = strategy.getDisplayMarketTickers();
        }
        positionBlotter.setFilterTickers(trackedTickers);
        positionBlotter.updatePositions(allPositions);
    }

    /**
     * Update the orderbook displays for all strategy markets, sorted by volume.
     */
    public void updateOrderbooks() {
        // Use all tracked markets from EventStrategy, not just limited display markets
        Set<String> displayTickers;
        if (strategy instanceof EventStrategy) {
            displayTickers = ((EventStrategy) strategy).getTrackedTickers();
        } else {
            displayTickers = strategy.getDisplayMarketTickers();
        }

        // Build list of markets with volume info
        List<MarketWithVolume> marketsWithVolume = new ArrayList<>();
        for (String ticker : displayTickers) {
            Market market = null;

            // Try event's markets list first (has all fields including subtitle)
            if (strategy instanceof EventStrategy) {
                Event event = ((EventStrategy) strategy).getEvent();
                if (event != null && event.getMarkets() != null) {
                    for (Market m : event.getMarkets()) {
                        if (ticker.equals(m.getTicker())) {
                            market = m;
                            break;
                        }
                    }
                }
            }

            // Fall back to marketInfoProvider
            if (market == null && marketInfoProvider != null) {
                market = marketInfoProvider.apply(ticker);
            }

            // Fall back to cache
            if (market == null) {
                market = marketCache.get(ticker);
            }

            // Cache for future use
            if (market != null) {
                marketCache.put(ticker, market);
            }

            long volume = 0;
            if (market != null && market.getVolume() != null) {
                volume = market.getVolume();
            }
            marketsWithVolume.add(new MarketWithVolume(ticker, market, volume));
        }

        // Sort by volume descending
        marketsWithVolume.sort((a, b) -> Long.compare(b.volume, a.volume));

        // Clear and rebuild orderbook panels
        orderbookContainer.getChildren().clear();
        orderbookPanelMap.clear();

        for (MarketWithVolume mwv : marketsWithVolume) {
            OrderbookMiniPanel panel = new OrderbookMiniPanel();
            panel.setMarketTicker(mwv.ticker);

            if (mwv.market != null) {
                panel.setMarketInfo(mwv.market);
            }

            // Get orderbook data from market manager
            if (marketManager != null) {
                ManagedMarket managed = marketManager.getMarket(mwv.ticker);
                if (managed != null) {
                    panel.updateOrderbook(managed.getYesBids(), managed.getNoBids());
                }
            }

            // Click handler for market details
            panel.setOnMouseClicked(e -> {
                if (e.getClickCount() == 1) {
                    showMarketDetails(mwv.ticker, mwv.market);
                }
            });
            panel.setCursor(javafx.scene.Cursor.HAND);

            orderbookPanelMap.put(mwv.ticker, panel);
            orderbookContainer.getChildren().add(panel);
        }
    }

    /**
     * Show event details in a dialog with all fields and all markets.
     */
    private void showEventDetails() {
        if (!(strategy instanceof EventStrategy)) {
            return;
        }

        EventStrategy eventStrategy = (EventStrategy) strategy;
        Event event = eventStrategy.getEvent();
        Set<String> trackedTickers = strategy.getDisplayMarketTickers();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Event Details");
        dialog.setHeaderText(eventStrategy.getEventTitle());
        dialog.setResizable(true);

        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(15));
        mainContent.setStyle("-fx-background-color: #1a1a2e;");

        if (event != null) {
            // Event Details Section
            TitledPane eventSection = createEventDetailsSection(event);
            eventSection.setExpanded(true);

            // Markets Section
            TitledPane marketsSection = createMarketsSection(event, trackedTickers);
            marketsSection.setExpanded(true);

            mainContent.getChildren().addAll(eventSection, marketsSection);
        } else {
            Label noData = new Label("Event data not yet loaded");
            noData.setTextFill(Color.GRAY);
            noData.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
            mainContent.getChildren().add(noData);
        }

        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");
        scrollPane.setPrefSize(750, 600);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: #1a1a2e;");
        dialog.getDialogPane().setPrefSize(800, 650);
        dialog.showAndWait();
    }

    /**
     * Create the Event Details section with all event fields.
     */
    private TitledPane createEventDetailsSection(Event event) {
        VBox content = new VBox(5);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #16213e;");

        addDetailRow(content, "Event Ticker", event.getEventTicker());
        addDetailRow(content, "Series Ticker", event.getSeriesTicker());
        addDetailRow(content, "Title", event.getTitle());
        addDetailRow(content, "Subtitle", event.getSubTitle());
        addDetailRow(content, "Category", event.getCategory());
        addDetailRow(content, "Strike Date", formatDateTime(event.getStrikeDate()));
        addDetailRow(content, "Strike Period", event.getStrikePeriod());
        addDetailRow(content, "Collateral Return Type", event.getCollateralReturnType());
        addDetailRow(content, "Mutually Exclusive", event.getMutuallyExclusive() != null ? event.getMutuallyExclusive().toString() : null);
        addDetailRow(content, "Available on Brokers", event.getAvailableOnBrokers() != null ? event.getAvailableOnBrokers().toString() : null);
        addDetailRow(content, "Markets Count", String.valueOf(event.getMarkets() != null ? event.getMarkets().size() : 0));

        if (event.getProductMetadata() != null && !event.getProductMetadata().isEmpty()) {
            addDetailRow(content, "Product Metadata", event.getProductMetadata().toString());
        }

        Label titleLabel = new Label("EVENT DETAILS");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.DARKORANGE);

        TitledPane pane = new TitledPane();
        pane.setGraphic(titleLabel);
        pane.setContent(content);
        pane.setCollapsible(true);
        return pane;
    }

    /**
     * Create the Markets section with all markets (tracked and untracked).
     */
    private TitledPane createMarketsSection(Event event, Set<String> trackedTickers) {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #16213e;");

        List<Market> markets = event.getMarkets();
        if (markets == null || markets.isEmpty()) {
            Label noMarkets = new Label("No markets available");
            noMarkets.setTextFill(Color.GRAY);
            content.getChildren().add(noMarkets);
        } else {
            // Sort markets: tracked first, then by volume
            List<Market> sortedMarkets = new ArrayList<>(markets);
            sortedMarkets.sort((a, b) -> {
                boolean aTracked = trackedTickers.contains(a.getTicker());
                boolean bTracked = trackedTickers.contains(b.getTicker());
                if (aTracked != bTracked) {
                    return aTracked ? -1 : 1; // Tracked first
                }
                // Then by volume
                long volA = a.getVolume() != null ? a.getVolume() : 0;
                long volB = b.getVolume() != null ? b.getVolume() : 0;
                return Long.compare(volB, volA);
            });

            for (Market market : sortedMarkets) {
                boolean isTracked = trackedTickers.contains(market.getTicker());
                TitledPane marketPane = createMarketDetailPane(market, isTracked);
                content.getChildren().add(marketPane);
            }
        }

        Label titleLabel = new Label("ALL MARKETS (" + (markets != null ? markets.size() : 0) + ")");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.STEELBLUE);

        TitledPane pane = new TitledPane();
        pane.setGraphic(titleLabel);
        pane.setContent(content);
        pane.setCollapsible(true);
        return pane;
    }

    /**
     * Create a detailed pane for a single market with all fields.
     */
    private TitledPane createMarketDetailPane(Market market, boolean isTracked) {
        VBox content = new VBox(4);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: #0f3460;");

        // Basic Info
        addSectionHeader(content, "Basic Information");
        addDetailRow(content, "Ticker", market.getTicker());
        addDetailRow(content, "Event Ticker", market.getEventTicker());
        addDetailRow(content, "Market Type", market.getMarketType());
        addDetailRow(content, "Title", market.getTitle());
        addDetailRow(content, "Subtitle", market.getSubtitle());
        addDetailRow(content, "Yes Subtitle", market.getYesSubTitle());
        addDetailRow(content, "No Subtitle", market.getNoSubTitle());
        addDetailRow(content, "Status", market.getStatus());

        // Timing
        addSectionHeader(content, "Timing");
        addDetailRow(content, "Created Time", formatDateTime(market.getCreatedTime()));
        addDetailRow(content, "Open Time", formatDateTime(market.getOpenTime()));
        addDetailRow(content, "Close Time", formatDateTime(market.getCloseTime()));
        addDetailRow(content, "Expiration Time", formatDateTime(market.getExpirationTime()));
        addDetailRow(content, "Expected Expiration", formatDateTime(market.getExpectedExpirationTime()));
        addDetailRow(content, "Latest Expiration", formatDateTime(market.getLatestExpirationTime()));
        addDetailRow(content, "Settlement Timer (sec)", market.getSettlementTimerSeconds() != null ? market.getSettlementTimerSeconds().toString() : null);
        addDetailRow(content, "Fee Waiver Expiration", formatDateTime(market.getFeeWaiverExpirationTime()));

        // Current Prices
        addSectionHeader(content, "Current Prices");
        addDetailRow(content, "Yes Bid", formatPrice(market.getYesBid()));
        addDetailRow(content, "Yes Ask", formatPrice(market.getYesAsk()));
        addDetailRow(content, "No Bid", formatPrice(market.getNoBid()));
        addDetailRow(content, "No Ask", formatPrice(market.getNoAsk()));
        addDetailRow(content, "Last Price", formatPrice(market.getLastPrice()));
        addDetailRow(content, "Response Price Units", market.getResponsePriceUnits());

        // Previous Prices
        addSectionHeader(content, "Previous Prices");
        addDetailRow(content, "Previous Yes Bid", formatPrice(market.getPreviousYesBid()));
        addDetailRow(content, "Previous Yes Ask", formatPrice(market.getPreviousYesAsk()));
        addDetailRow(content, "Previous Price ($)", market.getPreviousPriceDollars());

        // Volume & Interest
        addSectionHeader(content, "Volume & Interest");
        addDetailRow(content, "Volume (Total)", market.getVolume() != null ? String.format("%,d", market.getVolume()) : null);
        addDetailRow(content, "Volume (24h)", market.getVolume24h() != null ? String.format("%,d", market.getVolume24h()) : null);
        addDetailRow(content, "Open Interest", market.getOpenInterest() != null ? String.format("%,d", market.getOpenInterest()) : null);
        addDetailRow(content, "Notional Value", market.getNotionalValue() != null ? String.format("%,d", market.getNotionalValue()) : null);
        addDetailRow(content, "Notional Value ($)", market.getNotionalValueDollars());
        addDetailRow(content, "Liquidity ($)", market.getLiquidityDollars());

        // Settlement
        addSectionHeader(content, "Settlement");
        addDetailRow(content, "Result", market.getResult());
        addDetailRow(content, "Settlement Value", market.getSettlementValue() != null ? market.getSettlementValue().toString() : null);
        addDetailRow(content, "Settlement Value ($)", market.getSettlementValueDollars());
        addDetailRow(content, "Settlement Time", formatDateTime(market.getSettlementTs()));
        addDetailRow(content, "Expiration Value", market.getExpirationValue());

        // Strike Configuration
        addSectionHeader(content, "Strike Configuration");
        addDetailRow(content, "Strike Type", market.getStrikeType());
        addDetailRow(content, "Floor Strike", market.getFloorStrike() != null ? market.getFloorStrike().toString() : null);
        addDetailRow(content, "Cap Strike", market.getCapStrike() != null ? market.getCapStrike().toString() : null);
        addDetailRow(content, "Functional Strike", market.getFunctionalStrike());
        addDetailRow(content, "Price Level Structure", market.getPriceLevelStructure());

        // Rules
        addSectionHeader(content, "Rules");
        addDetailRow(content, "Rules Primary", market.getRulesPrimary());
        addDetailRow(content, "Rules Secondary", market.getRulesSecondary());
        addDetailRow(content, "Early Close Condition", market.getEarlyCloseCondition());

        // Flags
        addSectionHeader(content, "Flags");
        addDetailRow(content, "Can Close Early", market.getCanCloseEarly() != null ? market.getCanCloseEarly().toString() : null);
        addDetailRow(content, "Is Provisional", market.getIsProvisional() != null ? market.getIsProvisional().toString() : null);

        // Other
        addSectionHeader(content, "Other");
        addDetailRow(content, "MVE Collection Ticker", market.getMveCollectionTicker());

        // Create title with tracking status
        String title = market.getTitle();
        if (title != null && title.length() > 50) {
            title = title.substring(0, 47) + "...";
        }
        String statusBadge = market.getStatus() != null ? " (" + market.getStatus().toUpperCase() + ")" : "";

        // Build title label with proper styling
        HBox titleBox = new HBox(8);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        if (isTracked) {
            Label trackedLabel = new Label("●");
            trackedLabel.setTextFill(Color.DARKGREEN);
            trackedLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
            titleBox.getChildren().add(trackedLabel);
        }

        Label titleLabel = new Label(title + statusBadge);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.DARKSLATEGRAY);
        titleBox.getChildren().add(titleLabel);

        if (isTracked) {
            Label badge = new Label("TRACKED");
            badge.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            badge.setTextFill(Color.WHITE);
            badge.setPadding(new Insets(1, 4, 1, 4));
            badge.setStyle("-fx-background-color: #2e7d32; -fx-background-radius: 3;");
            titleBox.getChildren().add(badge);
        }

        TitledPane pane = new TitledPane();
        pane.setGraphic(titleBox);
        pane.setContent(content);
        pane.setExpanded(false); // Collapse by default to save space
        pane.setCollapsible(true);
        return pane;
    }

    private void addSectionHeader(VBox container, String headerText) {
        Label header = new Label("── " + headerText + " ──");
        header.setTextFill(Color.GOLD);
        header.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        header.setPadding(new Insets(8, 0, 2, 0));
        container.getChildren().add(header);
    }

    private String formatPrice(Integer priceInCents) {
        if (priceInCents == null) return null;
        return priceInCents + "¢ ($" + String.format("%.2f", priceInCents / 100.0) + ")";
    }

    /**
     * Show market details in a dialog.
     */
    private void showMarketDetails(String ticker, Market passedMarket) {
        // Try multiple sources to get market data
        Market market = passedMarket;

        // Try from provider
        if (market == null && marketInfoProvider != null) {
            market = marketInfoProvider.apply(ticker);
        }

        // Try from cache
        if (market == null) {
            market = marketCache.get(ticker);
        }

        // Try from event's markets list
        if (market == null && strategy instanceof EventStrategy) {
            Event event = ((EventStrategy) strategy).getEvent();
            if (event != null && event.getMarkets() != null) {
                for (Market m : event.getMarkets()) {
                    if (ticker.equals(m.getTicker())) {
                        market = m;
                        break;
                    }
                }
            }
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Market Details");
        dialog.setHeaderText(ticker);
        dialog.setResizable(true);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #1a1a2e;");
        content.setPrefWidth(600);

        if (market != null) {
            // Use the same detailed view as in the event details dialog
            Set<String> trackedTickers = strategy.getDisplayMarketTickers();
            boolean isTracked = trackedTickers.contains(ticker);
            TitledPane marketPane = createMarketDetailPane(market, isTracked);
            marketPane.setExpanded(true);
            content.getChildren().add(marketPane);
        } else {
            Label noData = new Label("Market data not available for: " + ticker);
            noData.setTextFill(Color.GRAY);
            content.getChildren().add(noData);
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");
        scrollPane.setPrefSize(650, 500);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: #1a1a2e;");
        dialog.getDialogPane().setPrefSize(700, 550);
        dialog.showAndWait();
    }

    private void addDetailRow(VBox container, String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label labelNode = new Label(label + ":");
        labelNode.setTextFill(Color.LIGHTGRAY);
        labelNode.setFont(Font.font("Arial", FontWeight.BOLD, 11));
        labelNode.setMinWidth(130);

        // Use TextField for copyable text
        String displayValue = value != null ? value : "--";
        TextField valueNode = createCopyableTextField(displayValue);
        HBox.setHgrow(valueNode, Priority.ALWAYS);

        row.getChildren().addAll(labelNode, valueNode);
        container.getChildren().add(row);
    }

    /**
     * Create a TextField styled to look like a label but with selectable/copyable text.
     */
    private TextField createCopyableTextField(String text) {
        TextField field = new TextField(text);
        field.setEditable(false);
        field.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        field.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-background-insets: 0; " +
            "-fx-padding: 0; " +
            "-fx-text-fill: white; " +
            "-fx-display-caret: false;"
        );
        // Remove focus highlighting
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                field.deselect();
            }
        });
        return field;
    }

    /**
     * Copy text to system clipboard.
     */
    private static void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
        }
    }

    /**
     * Add a copy context menu to a Label.
     */
    private static void addCopyContextMenu(Label label) {
        ContextMenu menu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> copyToClipboard(label.getText()));
        menu.getItems().add(copyItem);
        label.setContextMenu(menu);
    }

    /**
     * Clean up resources when the panel is no longer needed.
     */
    public void dispose() {
        if (logListener != null) {
            StrategyActivityLog activityLog = strategy.getActivityLog();
            if (activityLog != null) {
                activityLog.removeListener(logListener);
            }
        }
    }

    private String formatDateTime(Instant instant) {
        if (instant == null) return "--";
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }

    /**
     * Helper class for sorting markets by volume.
     */
    private static class MarketWithVolume {
        final String ticker;
        final Market market;
        final long volume;

        MarketWithVolume(String ticker, Market market, long volume) {
            this.ticker = ticker;
            this.market = market;
            this.volume = volume;
        }
    }

    /**
     * Custom cell for rendering log entries with color coding.
     */
    private static class LogEntryCell extends ListCell<LogEntry> {
        @Override
        protected void updateItem(LogEntry item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                setText(item.toString());
                setFont(Font.font("Monospace", 10));

                switch (item.getLevel()) {
                    case INFO:
                        setTextFill(Color.WHITE);
                        break;
                    case WARN:
                        setTextFill(Color.YELLOW);
                        break;
                    case ERROR:
                        setTextFill(Color.LIGHTCORAL);
                        break;
                    case TRADE:
                        setTextFill(Color.CYAN);
                        break;
                    default:
                        setTextFill(Color.LIGHTGRAY);
                }
            }
        }
    }

    /**
     * Mini orderbook panel showing market info and top 3 levels of bids and asks.
     */
    public static class OrderbookMiniPanel extends VBox {
        private static final int MAX_LEVELS = 3;

        private Label titleLabel;
        private Label descriptionLabel;
        private Label volumeLabel;
        private String marketTicker;
        private VBox bidsBox;
        private VBox asksBox;

        public OrderbookMiniPanel() {
            setSpacing(4);
            setPadding(new Insets(8));
            setStyle("-fx-background-color: #0f3460; -fx-background-radius: 6;");
            setMinWidth(200);
            setPrefWidth(220);
            setMaxWidth(250);

            // Title - displays subtitle (e.g., "5765.00 to 5774.99")
            titleLabel = new Label("--");
            titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            titleLabel.setTextFill(Color.WHITE);
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(240);
            addCopyMenu(titleLabel);

            // Description (yes_subtitle) - secondary info
            descriptionLabel = new Label("");
            descriptionLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
            descriptionLabel.setTextFill(Color.LIGHTYELLOW);
            descriptionLabel.setWrapText(true);
            descriptionLabel.setMaxWidth(240);
            addCopyMenu(descriptionLabel);

            // Volume info
            volumeLabel = new Label("Vol: --");
            volumeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
            volumeLabel.setTextFill(Color.GRAY);
            addCopyMenu(volumeLabel);

            // Separator
            Separator separator = new Separator();
            separator.setStyle("-fx-background-color: #333;");

            // Bids and Asks side by side
            HBox bookRow = new HBox(12);
            bookRow.setAlignment(Pos.TOP_LEFT);

            // Bids column
            VBox bidsContainer = new VBox(3);
            Label bidLabel = new Label("BIDS");
            bidLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            bidLabel.setTextFill(Color.LIGHTGREEN);
            bidsBox = new VBox(2);
            bidsContainer.getChildren().addAll(bidLabel, bidsBox);

            // Asks column
            VBox asksContainer = new VBox(3);
            Label askLabel = new Label("ASKS");
            askLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            askLabel.setTextFill(Color.LIGHTCORAL);
            asksBox = new VBox(2);
            asksContainer.getChildren().addAll(askLabel, asksBox);

            bookRow.getChildren().addAll(bidsContainer, asksContainer);
            HBox.setHgrow(bidsContainer, Priority.ALWAYS);
            HBox.setHgrow(asksContainer, Priority.ALWAYS);

            getChildren().addAll(titleLabel, descriptionLabel, volumeLabel, separator, bookRow);
        }

        public void setMarketTicker(String ticker) {
            this.marketTicker = ticker;
            titleLabel.setText(ticker);
        }

        public void setMarketInfo(Market market) {
            if (market == null) {
                titleLabel.setText(marketTicker);
                descriptionLabel.setText("");
                volumeLabel.setText("Vol: --");
                return;
            }

            // Set title to subtitle (e.g., "5765.00 to 5774.99") - this is the primary display
            String subtitle = market.getSubtitle();
            if (subtitle != null && !subtitle.isEmpty()) {
                if (subtitle.length() > 45) {
                    subtitle = subtitle.substring(0, 42) + "...";
                }
                titleLabel.setText(subtitle);
            } else {
                // Fall back to title if no subtitle
                String title = market.getTitle();
                if (title != null && title.length() > 45) {
                    title = title.substring(0, 42) + "...";
                }
                titleLabel.setText(title != null ? title : marketTicker);
            }

            // Set description to yes_subtitle (e.g., "S&P 500 closes between...")
            String yesSubtitle = market.getYesSubTitle();
            if (yesSubtitle != null && !yesSubtitle.isEmpty()) {
                if (yesSubtitle.length() > 55) {
                    yesSubtitle = yesSubtitle.substring(0, 52) + "...";
                }
                descriptionLabel.setText(yesSubtitle);
                descriptionLabel.setVisible(true);
                descriptionLabel.setManaged(true);
            } else {
                descriptionLabel.setText("");
                descriptionLabel.setVisible(false);
                descriptionLabel.setManaged(false);
            }

            // Set volume
            if (market.getVolume() != null) {
                volumeLabel.setText(String.format("Vol: %,d", market.getVolume()));
            } else {
                volumeLabel.setText("Vol: --");
            }
        }

        public void updateOrderbook(List<ManagedMarket.PriceLevel> yesBids, List<ManagedMarket.PriceLevel> noBids) {
            bidsBox.getChildren().clear();
            asksBox.getChildren().clear();

            if (yesBids != null) {
                yesBids.stream()
                        .limit(MAX_LEVELS)
                        .forEach(pl -> {
                            Label level = new Label(pl.getPrice() + "¢ x" + pl.getQuantity());
                            level.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
                            level.setTextFill(Color.LIGHTGREEN);
                            bidsBox.getChildren().add(level);
                        });
            }

            if (noBids != null) {
                // Asks are derived from NO bids (100 - noPrice = yesAsk)
                noBids.stream()
                        .limit(MAX_LEVELS)
                        .forEach(pl -> {
                            int askPrice = 100 - pl.getPrice();
                            Label level = new Label(askPrice + "¢ x" + pl.getQuantity());
                            level.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
                            level.setTextFill(Color.LIGHTCORAL);
                            asksBox.getChildren().add(level);
                        });
            }

            // Fill empty slots
            while (bidsBox.getChildren().size() < MAX_LEVELS) {
                Label empty = new Label("--");
                empty.setFont(Font.font("Monospace", 12));
                empty.setTextFill(Color.GRAY);
                bidsBox.getChildren().add(empty);
            }
            while (asksBox.getChildren().size() < MAX_LEVELS) {
                Label empty = new Label("--");
                empty.setFont(Font.font("Monospace", 12));
                empty.setTextFill(Color.GRAY);
                asksBox.getChildren().add(empty);
            }
        }

        /**
         * Add a copy context menu to a label.
         */
        private static void addCopyMenu(Label label) {
            ContextMenu menu = new ContextMenu();
            MenuItem copyItem = new MenuItem("Copy");
            copyItem.setOnAction(e -> {
                String text = label.getText();
                if (text != null && !text.isEmpty()) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    clipboard.setContent(content);
                }
            });
            menu.getItems().add(copyItem);
            label.setContextMenu(menu);
        }
    }
}
