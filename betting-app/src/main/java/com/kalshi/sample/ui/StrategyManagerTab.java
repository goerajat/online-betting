package com.kalshi.sample.ui;

import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Market;
import com.kalshi.client.strategy.EventStrategy;
import com.kalshi.client.strategy.StrategyManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;

/**
 * A Tab that displays a list of all strategies managed by StrategyManager.
 * Shows strategy status (active/inactive) and allows clicking to open a detail dialog.
 */
public class StrategyManagerTab extends Tab {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StrategyManager strategyManager;
    private final ObservableList<EventStrategy> strategyList = FXCollections.observableArrayList();
    private ListView<EventStrategy> listView;

    // Status labels
    private Label totalStrategiesLabel;
    private Label activeStrategiesLabel;
    private Label inactiveStrategiesLabel;

    // Manager wiring
    private OrderManager orderManager;
    private PositionManager positionManager;
    private MarketManager marketManager;
    private Function<String, Market> marketInfoProvider;

    // Refresh timer
    private Timer refreshTimer;

    public StrategyManagerTab(StrategyManager strategyManager) {
        this.strategyManager = strategyManager;

        setText("Strategy Manager");
        setClosable(false);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));
        content.setStyle("-fx-background-color: #1a1a2e;");

        // Header section
        VBox header = createHeader();

        // Strategy list section
        VBox listSection = createListSection();
        VBox.setVgrow(listSection, Priority.ALWAYS);

        content.getChildren().addAll(header, listSection);
        setContent(content);

        // Initial load
        refreshStrategies();

        // Start auto-refresh
        startRefreshTimer();

        // Cleanup on close
        setOnClosed(e -> stopRefreshTimer());
    }

    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label title = new Label("STRATEGY MANAGER");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.GOLD);

        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        totalStrategiesLabel = createStatLabel("Total: 0", Color.WHITE);
        activeStrategiesLabel = createStatLabel("Active: 0", Color.LIGHTGREEN);
        inactiveStrategiesLabel = createStatLabel("Inactive: 0", Color.GRAY);

        statsRow.getChildren().addAll(totalStrategiesLabel, activeStrategiesLabel, inactiveStrategiesLabel);

        // Action buttons
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        Button refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> refreshStrategies());

        Button activateAllButton = new Button("Activate All");
        activateAllButton.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
        activateAllButton.setOnAction(e -> activateAllStrategies());

        Button deactivateAllButton = new Button("Deactivate All");
        deactivateAllButton.setStyle("-fx-background-color: #c62828; -fx-text-fill: white;");
        deactivateAllButton.setOnAction(e -> deactivateAllStrategies());

        buttonRow.getChildren().addAll(refreshButton, activateAllButton, deactivateAllButton);

        header.getChildren().addAll(title, statsRow, buttonRow);
        return header;
    }

    private Label createStatLabel(String text, Color color) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        label.setTextFill(color);
        return label;
    }

    private VBox createListSection() {
        VBox section = new VBox(5);
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");
        section.setPadding(new Insets(10));

        Label listTitle = new Label("Strategies (click to manage)");
        listTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        listTitle.setTextFill(Color.LIGHTSKYBLUE);

        listView = new ListView<>(strategyList);
        listView.setCellFactory(lv -> new StrategyListCell());
        listView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");

        // Double-click to open dialog
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                EventStrategy selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openStrategyDialog(selected);
                }
            }
        });

        VBox.setVgrow(listView, Priority.ALWAYS);

        section.getChildren().addAll(listTitle, listView);
        return section;
    }

    /**
     * Wire up managers for strategy dialogs.
     */
    public void wireManagers(OrderManager orderManager, PositionManager positionManager, MarketManager marketManager) {
        this.orderManager = orderManager;
        this.positionManager = positionManager;
        this.marketManager = marketManager;
    }

    /**
     * Set market info provider for strategy dialogs.
     */
    public void setMarketInfoProvider(Function<String, Market> provider) {
        this.marketInfoProvider = provider;
    }

    /**
     * Refresh the strategy list from StrategyManager.
     */
    public void refreshStrategies() {
        Collection<EventStrategy> strategies = strategyManager.getAllStrategies();

        Platform.runLater(() -> {
            strategyList.clear();
            strategyList.addAll(strategies);

            // Update stats
            int total = strategies.size();
            int active = strategyManager.getActiveStrategyCount();
            int inactive = total - active;

            totalStrategiesLabel.setText("Total: " + total);
            activeStrategiesLabel.setText("Active: " + active);
            inactiveStrategiesLabel.setText("Inactive: " + inactive);
        });
    }

    private void activateAllStrategies() {
        for (EventStrategy strategy : strategyManager.getAllStrategies()) {
            if (!strategy.isActive()) {
                strategy.makeActive();
            }
        }
        refreshStrategies();
    }

    private void deactivateAllStrategies() {
        for (EventStrategy strategy : strategyManager.getAllStrategies()) {
            if (strategy.isActive()) {
                strategy.makeInactive();
            }
        }
        refreshStrategies();
    }

    private void openStrategyDialog(EventStrategy strategy) {
        StrategyDialog dialog = new StrategyDialog(strategy);

        if (orderManager != null && positionManager != null && marketManager != null) {
            dialog.wireManagers(orderManager, positionManager, marketManager);
        }
        if (marketInfoProvider != null) {
            dialog.setMarketInfoProvider(marketInfoProvider);
        }

        dialog.showAndWait();

        // Refresh list after dialog closes (status may have changed)
        refreshStrategies();
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer("StrategyManagerRefresh", true);
        refreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshStrategies();
            }
        }, 5000, 5000); // Refresh every 5 seconds
    }

    private void stopRefreshTimer() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
    }

    /**
     * Custom cell renderer for strategy list items.
     */
    private class StrategyListCell extends ListCell<EventStrategy> {
        @Override
        protected void updateItem(EventStrategy strategy, boolean empty) {
            super.updateItem(strategy, empty);

            if (empty || strategy == null) {
                setText(null);
                setGraphic(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(8));

                // Status indicator
                Label statusIndicator = new Label(strategy.isActive() ? "\u25CF" : "\u25CB");
                statusIndicator.setFont(Font.font("Arial", FontWeight.BOLD, 16));
                statusIndicator.setTextFill(strategy.isActive() ? Color.LIGHTGREEN : Color.GRAY);
                statusIndicator.setMinWidth(20);

                // Event title
                VBox infoBox = new VBox(2);

                Label titleLabel = new Label(strategy.getEventTitle());
                titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
                titleLabel.setTextFill(Color.WHITE);

                Label detailLabel = new Label(String.format(
                        "%s | Markets: %d | %s",
                        strategy.getEventTicker(),
                        strategy.getTrackedMarketCount(),
                        strategy.isActive() ? "ACTIVE" : "INACTIVE"
                ));
                detailLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
                detailLabel.setTextFill(strategy.isActive() ? Color.LIGHTGREEN : Color.GRAY);

                infoBox.getChildren().addAll(titleLabel, detailLabel);
                HBox.setHgrow(infoBox, Priority.ALWAYS);

                // Close time
                Instant closeTime = strategy.getExpectedCloseTime();
                String closeTimeStr = closeTime != null
                        ? LocalDateTime.ofInstant(closeTime, ZoneId.systemDefault()).format(TIME_FORMATTER)
                        : "--";
                Label closeLabel = new Label("Close: " + closeTimeStr);
                closeLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
                closeLabel.setTextFill(Color.LIGHTGRAY);

                // Quick action buttons
                Button toggleButton = new Button(strategy.isActive() ? "Deactivate" : "Activate");
                toggleButton.setStyle(strategy.isActive()
                        ? "-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-size: 10;"
                        : "-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-size: 10;");
                toggleButton.setOnAction(e -> {
                    if (strategy.isActive()) {
                        strategy.makeInactive();
                    } else {
                        strategy.makeActive();
                    }
                    refreshStrategies();
                });

                container.getChildren().addAll(statusIndicator, infoBox, closeLabel, toggleButton);

                setGraphic(container);
                setText(null);
                setStyle("-fx-background-color: #0f3460; -fx-padding: 0;");
            }
        }
    }
}
