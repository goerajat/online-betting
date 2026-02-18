package com.kalshi.sample.ui;

import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.strategy.EventStrategy;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A dialog for viewing and controlling a single EventStrategy.
 * Similar to StrategyTab content but in a dialog with activate/deactivate controls.
 */
public class StrategyDialog extends Dialog<Void> {

    private final EventStrategy strategy;
    private final StrategyStatusPanel statusPanel;

    // Controls
    private Button activateButton;
    private Button deactivateButton;
    private Label statusLabel;

    // Manager wiring
    private OrderManager orderManager;
    private PositionManager positionManager;
    private MarketManager marketManager;

    // Listeners for cleanup
    private Consumer<OrderManager.OrderChangeEvent> orderListener;
    private Consumer<PositionManager.PositionChangeEvent> positionListener;
    private Consumer<MarketManager.MarketChangeEvent> marketListener;

    public StrategyDialog(EventStrategy strategy) {
        this.strategy = strategy;
        this.statusPanel = new StrategyStatusPanel(strategy);

        setTitle("Strategy: " + strategy.getEventTitle());
        initModality(Modality.NONE); // Allow interaction with main window
        setResizable(true);

        // Build dialog content
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #1a1a2e;");

        // Control bar at top
        HBox controlBar = createControlBar();

        // Status panel (main content)
        VBox.setVgrow(statusPanel, Priority.ALWAYS);

        content.getChildren().addAll(controlBar, statusPanel);

        // Wrap in scroll pane
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");
        scrollPane.setPrefSize(900, 700);

        getDialogPane().setContent(scrollPane);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setStyle("-fx-background-color: #1a1a2e;");
        getDialogPane().setPrefSize(950, 750);

        // Update status initially
        updateStatus();

        // Cleanup on close
        setOnCloseRequest(e -> dispose());
    }

    private HBox createControlBar() {
        HBox controlBar = new HBox(15);
        controlBar.setPadding(new Insets(10));
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        // Status label
        Label statusTitle = new Label("Status:");
        statusTitle.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        statusTitle.setTextFill(Color.WHITE);

        statusLabel = new Label("--");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        statusLabel.setPadding(new Insets(4, 10, 4, 10));
        statusLabel.setStyle("-fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Action buttons
        activateButton = new Button("Activate");
        activateButton.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        activateButton.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white;");
        activateButton.setPrefWidth(100);
        activateButton.setOnAction(e -> {
            strategy.makeActive();
            updateStatus();
            // Refresh data after activation
            if (orderManager != null) {
                Platform.runLater(() -> statusPanel.updateOrders(orderManager.getAllOrders()));
            }
            if (positionManager != null) {
                Platform.runLater(() -> statusPanel.updatePositions(positionManager.getAllPositions()));
            }
            Platform.runLater(() -> statusPanel.updateOrderbooks());
        });

        deactivateButton = new Button("Deactivate");
        deactivateButton.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        deactivateButton.setStyle("-fx-background-color: #c62828; -fx-text-fill: white;");
        deactivateButton.setPrefWidth(100);
        deactivateButton.setOnAction(e -> {
            strategy.makeInactive();
            updateStatus();
        });

        // Info section
        VBox infoBox = new VBox(2);
        Label eventTickerLabel = new Label("Event: " + strategy.getEventTicker());
        eventTickerLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        eventTickerLabel.setTextFill(Color.LIGHTGRAY);

        Label marketsLabel = new Label("Markets: " + strategy.getTrackedMarketCount());
        marketsLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 10));
        marketsLabel.setTextFill(Color.LIGHTGRAY);

        infoBox.getChildren().addAll(eventTickerLabel, marketsLabel);

        controlBar.getChildren().addAll(statusTitle, statusLabel, infoBox, spacer, activateButton, deactivateButton);

        return controlBar;
    }

    private void updateStatus() {
        boolean active = strategy.isActive();

        if (active) {
            statusLabel.setText("ACTIVE");
            statusLabel.setTextFill(Color.WHITE);
            statusLabel.setStyle("-fx-background-color: #2e7d32; -fx-background-radius: 4;");
            activateButton.setDisable(true);
            deactivateButton.setDisable(false);
        } else {
            statusLabel.setText("INACTIVE");
            statusLabel.setTextFill(Color.WHITE);
            statusLabel.setStyle("-fx-background-color: #616161; -fx-background-radius: 4;");
            activateButton.setDisable(false);
            deactivateButton.setDisable(true);
        }
    }

    /**
     * Wire up managers for data updates.
     */
    public void wireManagers(OrderManager orderManager, PositionManager positionManager, MarketManager marketManager) {
        this.orderManager = orderManager;
        this.positionManager = positionManager;
        this.marketManager = marketManager;

        statusPanel.setMarketManager(marketManager);

        // Setup order listener
        orderListener = event -> Platform.runLater(() -> {
            List<Order> orders = orderManager.getAllOrders();
            statusPanel.updateOrders(orders);
        });
        orderManager.addOrderChangeListener(orderListener);

        // Setup position listener
        positionListener = event -> Platform.runLater(() -> {
            List<Position> positions = positionManager.getAllPositions();
            statusPanel.updatePositions(positions);
        });
        positionManager.addPositionChangeListener(positionListener);

        // Setup market listener for orderbook updates
        marketListener = event -> Platform.runLater(() -> {
            statusPanel.updateOrderbooks();
        });
        marketManager.addMarketChangeListener(marketListener);

        // Initial data load
        Platform.runLater(() -> {
            statusPanel.updateOrders(orderManager.getAllOrders());
            statusPanel.updatePositions(positionManager.getAllPositions());
            statusPanel.updateOrderbooks();
        });
    }

    /**
     * Set market info provider for blotters.
     */
    public void setMarketInfoProvider(Function<String, Market> provider) {
        statusPanel.setMarketInfoProvider(provider);
    }

    /**
     * Clean up resources when the dialog is closed.
     */
    public void dispose() {
        // Remove listeners
        if (orderManager != null && orderListener != null) {
            orderManager.removeOrderChangeListener(orderListener);
        }
        if (positionManager != null && positionListener != null) {
            positionManager.removePositionChangeListener(positionListener);
        }
        if (marketManager != null && marketListener != null) {
            marketManager.removeMarketChangeListener(marketListener);
        }

        // Dispose panel
        statusPanel.dispose();
    }
}
