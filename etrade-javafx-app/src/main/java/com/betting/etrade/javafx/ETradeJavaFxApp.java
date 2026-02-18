package com.betting.etrade.javafx;

import com.betting.etrade.manager.JavaFxAuthorizationDialog;
import com.betting.etrade.manager.MarketDataManager;
import com.betting.etrade.model.QuoteData;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * JavaFX sample application demonstrating E*TRADE MarketDataManager usage.
 */
public class ETradeJavaFxApp extends Application {

    private static final String CONFIG_FILE = "etrade-config.properties";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private MarketDataManager marketDataManager;
    private String activeSubscriptionId;

    // UI Components
    private Label statusLabel;
    private Label connectionStatus;
    private Button authButton;
    private TextField symbolsField;
    private Button subscribeButton;
    private Button unsubscribeButton;
    private Button fetchButton;
    private TableView<QuoteRow> quotesTable;
    private ObservableList<QuoteRow> quoteData;
    private TextArea logArea;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("E*TRADE Market Data - JavaFX Demo");

        // Create main layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Top - Header and connection status
        root.setTop(createHeader());

        // Center - Main content
        root.setCenter(createMainContent());

        // Bottom - Log area
        root.setBottom(createLogArea());

        Scene scene = new Scene(root, 900, 700);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize
        initializeManager();
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(0, 0, 10, 0));

        // Title
        Label titleLabel = new Label("E*TRADE Market Data Manager");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 20));

        // Status bar
        HBox statusBar = new HBox(20);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        connectionStatus = new Label("● Disconnected");
        connectionStatus.setTextFill(Color.RED);
        connectionStatus.setFont(Font.font("System", FontWeight.BOLD, 12));

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-text-fill: gray;");

        authButton = new Button("Authenticate");
        authButton.setOnAction(e -> authenticate());

        statusBar.getChildren().addAll(connectionStatus, new Separator(), statusLabel,
                new Region(), authButton);
        HBox.setHgrow(statusBar.getChildren().get(3), Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, new Separator(), statusBar);
        return header;
    }

    private VBox createMainContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(10, 0, 10, 0));

        // Subscription controls
        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label symbolsLabel = new Label("Symbols:");
        symbolsField = new TextField("AAPL,GOOGL,MSFT,AMZN,TSLA");
        symbolsField.setPrefWidth(300);
        symbolsField.setPromptText("Enter comma-separated symbols");

        fetchButton = new Button("Fetch Once");
        fetchButton.setOnAction(e -> fetchQuotes());
        fetchButton.setDisable(true);

        subscribeButton = new Button("Subscribe");
        subscribeButton.setOnAction(e -> subscribe());
        subscribeButton.setDisable(true);

        unsubscribeButton = new Button("Unsubscribe");
        unsubscribeButton.setOnAction(e -> unsubscribe());
        unsubscribeButton.setDisable(true);

        controls.getChildren().addAll(symbolsLabel, symbolsField, fetchButton,
                subscribeButton, unsubscribeButton);

        // Quotes table
        quotesTable = createQuotesTable();
        VBox.setVgrow(quotesTable, Priority.ALWAYS);

        content.getChildren().addAll(controls, quotesTable);
        return content;
    }

    private TableView<QuoteRow> createQuotesTable() {
        TableView<QuoteRow> table = new TableView<>();
        quoteData = FXCollections.observableArrayList();
        table.setItems(quoteData);

        TableColumn<QuoteRow, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        symbolCol.setPrefWidth(80);

        TableColumn<QuoteRow, String> lastCol = new TableColumn<>("Last");
        lastCol.setCellValueFactory(new PropertyValueFactory<>("last"));
        lastCol.setPrefWidth(100);
        lastCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<QuoteRow, String> changeCol = new TableColumn<>("Change");
        changeCol.setCellValueFactory(new PropertyValueFactory<>("change"));
        changeCol.setPrefWidth(100);
        changeCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        changeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: green;");
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: red;");
                    } else {
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                }
            }
        });

        TableColumn<QuoteRow, String> pctCol = new TableColumn<>("Change %");
        pctCol.setCellValueFactory(new PropertyValueFactory<>("changePercent"));
        pctCol.setPrefWidth(100);
        pctCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        pctCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                } else {
                    setText(item);
                    if (item.startsWith("+")) {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: green;");
                    } else if (item.startsWith("-")) {
                        setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: red;");
                    } else {
                        setStyle("-fx-alignment: CENTER-RIGHT;");
                    }
                }
            }
        });

        TableColumn<QuoteRow, String> bidCol = new TableColumn<>("Bid");
        bidCol.setCellValueFactory(new PropertyValueFactory<>("bid"));
        bidCol.setPrefWidth(90);
        bidCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<QuoteRow, String> askCol = new TableColumn<>("Ask");
        askCol.setCellValueFactory(new PropertyValueFactory<>("ask"));
        askCol.setPrefWidth(90);
        askCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<QuoteRow, String> volumeCol = new TableColumn<>("Volume");
        volumeCol.setCellValueFactory(new PropertyValueFactory<>("volume"));
        volumeCol.setPrefWidth(100);
        volumeCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<QuoteRow, String> timeCol = new TableColumn<>("Updated");
        timeCol.setCellValueFactory(new PropertyValueFactory<>("updateTime"));
        timeCol.setPrefWidth(100);

        table.getColumns().addAll(symbolCol, lastCol, changeCol, pctCol,
                bidCol, askCol, volumeCol, timeCol);
        table.setPlaceholder(new Label("No quotes - authenticate and subscribe to see data"));

        return table;
    }

    private VBox createLogArea() {
        VBox logBox = new VBox(5);
        logBox.setPadding(new Insets(10, 0, 0, 0));

        Label logLabel = new Label("Activity Log:");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(6);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        logBox.getChildren().addAll(logLabel, logArea);
        return logBox;
    }

    private void initializeManager() {
        try {
            Path configPath = Path.of(CONFIG_FILE);
            if (!Files.exists(configPath)) {
                // Create default config if not exists
                Properties props = new Properties();
                props.setProperty("consumer.key", "your-consumer-key");
                props.setProperty("consumer.secret", "your-consumer-secret");
                props.setProperty("use.sandbox", "true");
                props.setProperty("poll.interval.seconds", "5");

                log("Config file not found. Please create: " + CONFIG_FILE);
                showAlert(Alert.AlertType.WARNING, "Configuration Required",
                        "Please create " + CONFIG_FILE + " with your E*TRADE credentials.");
                return;
            }

            marketDataManager = MarketDataManager.fromPropertiesFile(CONFIG_FILE);
            log("MarketDataManager initialized from " + CONFIG_FILE);
            updateStatus("Ready - Click 'Authenticate' to connect");

        } catch (Exception e) {
            log("Error initializing: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Initialization Error", e.getMessage());
        }
    }

    private void authenticate() {
        if (marketDataManager == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "MarketDataManager not initialized");
            return;
        }

        if (marketDataManager.isAuthenticated()) {
            log("Already authenticated");
            return;
        }

        authButton.setDisable(true);
        updateStatus("Authenticating...");

        // Run authentication in background thread
        new Thread(() -> {
            try {
                marketDataManager.authenticate(
                        new JavaFxAuthorizationDialog(
                                (Stage) authButton.getScene().getWindow(),
                                "E*TRADE Authorization",
                                true
                        )
                );

                Platform.runLater(() -> {
                    connectionStatus.setText("● Connected");
                    connectionStatus.setTextFill(Color.GREEN);
                    authButton.setText("Connected");
                    authButton.setDisable(true);
                    fetchButton.setDisable(false);
                    subscribeButton.setDisable(false);
                    updateStatus("Authenticated successfully");
                    log("Authentication successful!");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    authButton.setDisable(false);
                    updateStatus("Authentication failed");
                    log("Authentication error: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Authentication Failed", e.getMessage());
                });
            }
        }).start();
    }

    private void fetchQuotes() {
        String input = symbolsField.getText().trim().toUpperCase();
        if (input.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Required", "Please enter symbols");
            return;
        }

        String[] symbols = input.split(",");
        log("Fetching quotes for: " + Arrays.toString(symbols));
        updateStatus("Fetching quotes...");

        new Thread(() -> {
            try {
                List<QuoteData> quotes = marketDataManager.getQuotes(symbols);
                Platform.runLater(() -> {
                    updateQuotesTable(quotes);
                    updateStatus("Fetched " + quotes.size() + " quotes");
                    log("Received " + quotes.size() + " quotes");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("Fetch failed");
                    log("Fetch error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void subscribe() {
        if (activeSubscriptionId != null) {
            showAlert(Alert.AlertType.WARNING, "Already Subscribed",
                    "Please unsubscribe first");
            return;
        }

        String input = symbolsField.getText().trim().toUpperCase();
        if (input.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Input Required", "Please enter symbols");
            return;
        }

        List<String> symbols = Arrays.asList(input.split(","));
        log("Subscribing to: " + symbols);

        activeSubscriptionId = marketDataManager.subscribe(
                symbols,
                quotes -> Platform.runLater(() -> {
                    updateQuotesTable(quotes);
                    log("Quote update: " + quotes.size() + " quotes received");
                }),
                error -> Platform.runLater(() -> {
                    log("Subscription error: " + error.getMessage());
                })
        );

        subscribeButton.setDisable(true);
        unsubscribeButton.setDisable(false);
        symbolsField.setDisable(true);
        updateStatus("Subscribed - polling every " +
                (marketDataManager.getPollIntervalMs() / 1000) + "s");
        log("Subscription started: " + activeSubscriptionId);
    }

    private void unsubscribe() {
        if (activeSubscriptionId == null) {
            return;
        }

        marketDataManager.unsubscribe(activeSubscriptionId);
        log("Unsubscribed: " + activeSubscriptionId);

        activeSubscriptionId = null;
        subscribeButton.setDisable(false);
        unsubscribeButton.setDisable(true);
        symbolsField.setDisable(false);
        updateStatus("Unsubscribed");
    }

    private void updateQuotesTable(List<QuoteData> quotes) {
        String updateTime = LocalDateTime.now().format(TIME_FORMAT);

        for (QuoteData quote : quotes) {
            // Find existing row or create new one
            QuoteRow existingRow = quoteData.stream()
                    .filter(r -> r.getSymbol().equals(quote.getSymbol()))
                    .findFirst()
                    .orElse(null);

            QuoteRow newRow = new QuoteRow(quote, updateTime);

            if (existingRow != null) {
                int index = quoteData.indexOf(existingRow);
                quoteData.set(index, newRow);
            } else {
                quoteData.add(newRow);
            }
        }
    }

    private void updateStatus(String status) {
        statusLabel.setText(status);
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        Platform.runLater(() -> {
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (marketDataManager != null) {
            log("Shutting down...");
            marketDataManager.shutdown();
        }
    }

    // Inner class for table rows
    public static class QuoteRow {
        private final SimpleStringProperty symbol;
        private final SimpleStringProperty last;
        private final SimpleStringProperty change;
        private final SimpleStringProperty changePercent;
        private final SimpleStringProperty bid;
        private final SimpleStringProperty ask;
        private final SimpleStringProperty volume;
        private final SimpleStringProperty updateTime;

        public QuoteRow(QuoteData quote, String updateTime) {
            this.symbol = new SimpleStringProperty(quote.getSymbol());
            this.updateTime = new SimpleStringProperty(updateTime);

            if (quote.getIntraday() != null) {
                var intraday = quote.getIntraday();
                this.last = new SimpleStringProperty(formatPrice(intraday.getLastTrade()));
                this.change = new SimpleStringProperty(formatChange(intraday.getChangeClose()));
                this.changePercent = new SimpleStringProperty(formatChangePercent(intraday.getChangeClosePercentage()));
                this.bid = new SimpleStringProperty(formatPrice(intraday.getBid()));
                this.ask = new SimpleStringProperty(formatPrice(intraday.getAsk()));
                this.volume = new SimpleStringProperty(formatVolume(intraday.getTotalVolume()));
            } else {
                this.last = new SimpleStringProperty(formatPrice(quote.getLastPrice()));
                this.change = new SimpleStringProperty("-");
                this.changePercent = new SimpleStringProperty("-");
                this.bid = new SimpleStringProperty("-");
                this.ask = new SimpleStringProperty("-");
                this.volume = new SimpleStringProperty("-");
            }
        }

        private static String formatPrice(Double price) {
            return price != null ? String.format("$%.2f", price) : "-";
        }

        private static String formatChange(Double change) {
            if (change == null) return "-";
            String prefix = change >= 0 ? "+" : "";
            return prefix + String.format("$%.2f", change);
        }

        private static String formatChangePercent(Double pct) {
            if (pct == null) return "-";
            String prefix = pct >= 0 ? "+" : "";
            return prefix + String.format("%.2f%%", pct);
        }

        private static String formatVolume(Long volume) {
            if (volume == null) return "-";
            if (volume >= 1_000_000) return String.format("%.2fM", volume / 1_000_000.0);
            if (volume >= 1_000) return String.format("%.1fK", volume / 1_000.0);
            return volume.toString();
        }

        // Getters for PropertyValueFactory
        public String getSymbol() { return symbol.get(); }
        public String getLast() { return last.get(); }
        public String getChange() { return change.get(); }
        public String getChangePercent() { return changePercent.get(); }
        public String getBid() { return bid.get(); }
        public String getAsk() { return ask.get(); }
        public String getVolume() { return volume.get(); }
        public String getUpdateTime() { return updateTime.get(); }
    }
}
