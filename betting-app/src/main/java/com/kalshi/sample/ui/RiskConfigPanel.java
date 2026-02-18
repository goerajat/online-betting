package com.kalshi.sample.ui;

import com.kalshi.client.risk.RiskChecker;
import com.kalshi.client.risk.RiskConfig;
import com.kalshi.client.risk.RiskViolation;
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

import java.util.function.Consumer;

/**
 * A panel for viewing and managing risk checker configuration.
 * Displays current limits, per-strategy overrides, and recent violations.
 */
public class RiskConfigPanel extends VBox {

    private final RiskChecker riskChecker;

    // Global limits display
    private Label enabledLabel;
    private Label maxOrderQtyLabel;
    private Label maxOrderNotionalLabel;
    private Label maxPosQtyLabel;
    private Label maxPosNotionalLabel;

    // Violations log
    private ListView<RiskViolation> violationsListView;
    private ObservableList<RiskViolation> violationItems = FXCollections.observableArrayList();

    // Violation listener for cleanup
    private Consumer<RiskViolation> violationListener;

    public RiskConfigPanel(RiskChecker riskChecker) {
        this.riskChecker = riskChecker;

        setSpacing(15);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #1a1a2e;");

        buildUI();
        setupViolationListener();
        refreshLimitsDisplay();
    }

    private void buildUI() {
        // Title
        Label title = new Label("Risk Management");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        // Global limits section
        VBox globalLimitsSection = createGlobalLimitsSection();

        // Violations log section
        VBox violationsSection = createViolationsSection();

        getChildren().addAll(title, globalLimitsSection, violationsSection);
        VBox.setVgrow(violationsSection, Priority.ALWAYS);
    }

    private VBox createGlobalLimitsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        Label sectionTitle = new Label("GLOBAL RISK LIMITS");
        sectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        sectionTitle.setTextFill(Color.GOLD);

        // Enabled status
        HBox enabledRow = new HBox(10);
        enabledRow.setAlignment(Pos.CENTER_LEFT);
        Label enabledTextLabel = new Label("Risk Checking:");
        enabledTextLabel.setTextFill(Color.LIGHTGRAY);
        enabledLabel = new Label("--");
        enabledLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        enabledRow.getChildren().addAll(enabledTextLabel, enabledLabel);

        // Grid for limits
        GridPane limitsGrid = new GridPane();
        limitsGrid.setHgap(20);
        limitsGrid.setVgap(10);
        limitsGrid.setPadding(new Insets(10, 0, 0, 0));

        // Max Order Quantity
        limitsGrid.add(createLimitLabel("Max Order Quantity:"), 0, 0);
        maxOrderQtyLabel = createValueLabel();
        limitsGrid.add(maxOrderQtyLabel, 1, 0);

        // Max Order Notional
        limitsGrid.add(createLimitLabel("Max Order Notional:"), 0, 1);
        maxOrderNotionalLabel = createValueLabel();
        limitsGrid.add(maxOrderNotionalLabel, 1, 1);

        // Max Position Quantity
        limitsGrid.add(createLimitLabel("Max Position Quantity:"), 0, 2);
        maxPosQtyLabel = createValueLabel();
        limitsGrid.add(maxPosQtyLabel, 1, 2);

        // Max Position Notional
        limitsGrid.add(createLimitLabel("Max Position Notional:"), 0, 3);
        maxPosNotionalLabel = createValueLabel();
        limitsGrid.add(maxPosNotionalLabel, 1, 3);

        section.getChildren().addAll(sectionTitle, enabledRow, limitsGrid);
        return section;
    }

    private VBox createViolationsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(15));
        section.setStyle("-fx-background-color: #16213e; -fx-background-radius: 8;");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label("RISK VIOLATIONS LOG");
        sectionTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        sectionTitle.setTextFill(Color.LIGHTCORAL);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearButton = new Button("Clear");
        clearButton.setStyle("-fx-background-color: #555; -fx-text-fill: white;");
        clearButton.setOnAction(e -> violationItems.clear());

        header.getChildren().addAll(sectionTitle, spacer, clearButton);

        violationsListView = new ListView<>(violationItems);
        violationsListView.setPlaceholder(new Label("No violations recorded"));
        violationsListView.setStyle("-fx-background-color: #0f3460; -fx-control-inner-background: #0f3460;");
        violationsListView.setCellFactory(lv -> new ViolationCell());

        section.getChildren().addAll(header, violationsListView);
        VBox.setVgrow(violationsListView, Priority.ALWAYS);

        return section;
    }

    private void setupViolationListener() {
        if (riskChecker != null) {
            violationListener = violation -> Platform.runLater(() -> {
                violationItems.add(0, violation);
                // Keep max 100 violations
                while (violationItems.size() > 100) {
                    violationItems.remove(violationItems.size() - 1);
                }
            });
            riskChecker.addViolationListener(violationListener);
        }
    }

    /**
     * Refresh the limits display with current configuration.
     */
    public void refreshLimitsDisplay() {
        if (riskChecker == null) {
            enabledLabel.setText("Not Configured");
            enabledLabel.setTextFill(Color.GRAY);
            return;
        }

        RiskConfig config = riskChecker.getConfig();

        // Enabled status
        if (config.isEnabled()) {
            enabledLabel.setText("ENABLED");
            enabledLabel.setTextFill(Color.LIGHTGREEN);
        } else {
            enabledLabel.setText("DISABLED");
            enabledLabel.setTextFill(Color.LIGHTCORAL);
        }

        // Order limits
        if (config.hasMaxOrderQuantity(null)) {
            maxOrderQtyLabel.setText(String.valueOf(config.getMaxOrderQuantity(null)));
        } else {
            maxOrderQtyLabel.setText("No Limit");
        }

        if (config.hasMaxOrderNotional(null)) {
            int cents = config.getMaxOrderNotional(null);
            maxOrderNotionalLabel.setText(String.format("%d¢ ($%.2f)", cents, cents / 100.0));
        } else {
            maxOrderNotionalLabel.setText("No Limit");
        }

        // Position limits
        if (config.hasMaxPositionQuantity(null)) {
            maxPosQtyLabel.setText(String.valueOf(config.getMaxPositionQuantity(null)));
        } else {
            maxPosQtyLabel.setText("No Limit");
        }

        if (config.hasMaxPositionNotional(null)) {
            int cents = config.getMaxPositionNotional(null);
            maxPosNotionalLabel.setText(String.format("%d¢ ($%.2f)", cents, cents / 100.0));
        } else {
            maxPosNotionalLabel.setText("No Limit");
        }
    }

    /**
     * Get the current violation count.
     */
    public int getViolationCount() {
        return violationItems.size();
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        if (riskChecker != null && violationListener != null) {
            riskChecker.removeViolationListener(violationListener);
        }
    }

    private Label createLimitLabel(String text) {
        Label label = new Label(text);
        label.setTextFill(Color.LIGHTGRAY);
        label.setFont(Font.font("Arial", FontWeight.NORMAL, 12));
        return label;
    }

    private Label createValueLabel() {
        Label label = new Label("--");
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        return label;
    }

    /**
     * Custom cell for rendering violations with color coding.
     */
    private static class ViolationCell extends ListCell<RiskViolation> {
        @Override
        protected void updateItem(RiskViolation item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setStyle("-fx-background-color: transparent;");
            } else {
                setText(item.toString());
                setFont(Font.font("Monospace", 10));
                setTextFill(Color.LIGHTCORAL);

                // Alternate row colors
                boolean isEven = getIndex() % 2 == 0;
                setStyle(isEven ? "-fx-background-color: #2a1a1a;" : "-fx-background-color: #3a2a2a;");
            }
        }
    }
}
