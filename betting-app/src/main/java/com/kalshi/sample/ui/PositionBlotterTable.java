package com.kalshi.sample.ui;

import com.kalshi.client.model.Market;
import com.kalshi.client.model.Position;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A TableView component for displaying positions.
 * Can filter positions by market tickers to show only positions for specific markets.
 */
public class PositionBlotterTable extends TableView<PositionBlotterTable.PositionRow> {

    private final ObservableList<PositionRow> positionRows = FXCollections.observableArrayList();
    private Set<String> filterTickers;
    private Consumer<PositionRow> onPositionSelected;
    private Function<String, Market> marketInfoProvider;

    public PositionBlotterTable() {
        setItems(positionRows);
        setPlaceholder(new Label("No positions"));
        setStyle("-fx-background-color: #0f3460;");
        setPrefHeight(150);
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setFixedCellSize(-1);

        setupColumns();
        setupRowFactory();
        setupContextMenu();
        setupClickHandler();
    }

    @SuppressWarnings("unchecked")
    private void setupColumns() {
        TableColumn<PositionRow, String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(c -> c.getValue().tickerProperty());
        tickerCol.setPrefWidth(100);
        tickerCol.setCellFactory(col -> createWhiteTextCell());

        TableColumn<PositionRow, String> sideCol = new TableColumn<>("Side");
        sideCol.setCellValueFactory(c -> c.getValue().sideProperty());
        sideCol.setPrefWidth(50);
        sideCol.setCellFactory(col -> new TableCell<PositionRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if ("LONG".equalsIgnoreCase(item)) {
                        setTextFill(Color.LIGHTGREEN);
                        setFont(Font.font("Arial", FontWeight.BOLD, 11));
                    } else if ("SHORT".equalsIgnoreCase(item)) {
                        setTextFill(Color.LIGHTCORAL);
                        setFont(Font.font("Arial", FontWeight.BOLD, 11));
                    } else {
                        setTextFill(Color.WHITE);
                    }
                }
            }
        });

        TableColumn<PositionRow, Number> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(c -> c.getValue().quantityProperty());
        qtyCol.setPrefWidth(50);
        qtyCol.setCellFactory(col -> createWhiteNumberCell());

        TableColumn<PositionRow, String> avgPriceCol = new TableColumn<>("Avg Price");
        avgPriceCol.setCellValueFactory(c -> c.getValue().avgPriceProperty());
        avgPriceCol.setPrefWidth(70);
        avgPriceCol.setCellFactory(col -> createWhiteTextCell());

        TableColumn<PositionRow, String> pnlCol = new TableColumn<>("P&L");
        pnlCol.setCellValueFactory(c -> c.getValue().pnlProperty());
        pnlCol.setPrefWidth(70);
        pnlCol.setCellFactory(col -> new TableCell<PositionRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    // Color based on P&L
                    if (item.startsWith("-")) {
                        setTextFill(Color.LIGHTCORAL);
                    } else if (!item.equals("$0.00")) {
                        setTextFill(Color.LIGHTGREEN);
                    } else {
                        setTextFill(Color.WHITE);
                    }
                }
            }
        });

        getColumns().addAll(tickerCol, sideCol, qtyCol, avgPriceCol, pnlCol);
    }

    private void setupRowFactory() {
        setRowFactory(tv -> new TableRow<PositionRow>() {
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
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyRowItem = new MenuItem("Copy Row");
        copyRowItem.setOnAction(e -> {
            PositionRow selected = getSelectionModel().getSelectedItem();
            if (selected != null) {
                String rowText = String.format("%s\t%s\t%d\t%s\t%s",
                    selected.tickerProperty().get(),
                    selected.sideProperty().get(),
                    selected.quantityProperty().get(),
                    selected.avgPriceProperty().get(),
                    selected.pnlProperty().get());
                copyToClipboard(rowText);
            }
        });

        MenuItem copyTickerItem = new MenuItem("Copy Ticker");
        copyTickerItem.setOnAction(e -> {
            PositionRow selected = getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyToClipboard(selected.getTicker());
            }
        });

        contextMenu.getItems().addAll(copyRowItem, copyTickerItem);
        setContextMenu(contextMenu);
    }

    private static void copyToClipboard(String text) {
        if (text != null && !text.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            clipboard.setContent(content);
        }
    }

    private void setupClickHandler() {
        setOnMouseClicked(e -> {
            PositionRow selected = getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 1 && onPositionSelected != null) {
                onPositionSelected.accept(selected);
            }
        });
    }

    /**
     * Set the tickers to filter by. Only positions for these tickers will be shown.
     */
    public void setFilterTickers(Set<String> tickers) {
        this.filterTickers = tickers;
    }

    /**
     * Set a callback for when a position is selected.
     */
    public void setOnPositionSelected(Consumer<PositionRow> handler) {
        this.onPositionSelected = handler;
    }

    /**
     * Set a provider for market info (for display purposes).
     */
    public void setMarketInfoProvider(Function<String, Market> provider) {
        this.marketInfoProvider = provider;
    }

    /**
     * Update the table with new positions.
     */
    public void updatePositions(List<Position> positions) {
        positionRows.clear();
        for (Position position : positions) {
            String ticker = position.getMarketTicker();
            // Apply filter if set
            if (filterTickers != null && !filterTickers.isEmpty() && !filterTickers.contains(ticker)) {
                continue;
            }
            // Skip flat positions
            if (position.getPosition() == null || position.getPosition() == 0) {
                continue;
            }
            Market market = marketInfoProvider != null ? marketInfoProvider.apply(ticker) : null;
            positionRows.add(new PositionRow(position, market));
        }
    }

    /**
     * Get the number of displayed positions.
     */
    public int getPositionCount() {
        return positionRows.size();
    }

    private TableCell<PositionRow, String> createWhiteTextCell() {
        return new TableCell<PositionRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
            }
        };
    }

    private TableCell<PositionRow, Number> createWhiteNumberCell() {
        return new TableCell<PositionRow, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setTextFill(Color.WHITE);
            }
        };
    }

    /**
     * Row data for the position blotter.
     */
    public static class PositionRow {
        private final SimpleStringProperty ticker;
        private final SimpleStringProperty side;
        private final SimpleIntegerProperty quantity;
        private final SimpleStringProperty avgPrice;
        private final SimpleStringProperty pnl;

        public PositionRow(Position position, Market market) {
            this.ticker = new SimpleStringProperty(position.getMarketTicker());

            int pos = position.getPosition() != null ? position.getPosition() : 0;
            this.side = new SimpleStringProperty(pos > 0 ? "LONG" : pos < 0 ? "SHORT" : "FLAT");
            this.quantity = new SimpleIntegerProperty(Math.abs(pos));

            // Calculate average price from cost / quantity
            BigDecimal costDollars = position.getPositionCostDollars();
            if (costDollars != null && pos != 0) {
                double avgPriceCents = Math.abs(costDollars.doubleValue() * 100 / pos);
                this.avgPrice = new SimpleStringProperty(String.format("%.0f¢", avgPriceCents));
            } else {
                this.avgPrice = new SimpleStringProperty("--");
            }

            BigDecimal realizedPnl = position.getRealizedPnlDollars();
            if (realizedPnl != null) {
                this.pnl = new SimpleStringProperty(String.format("$%.2f", realizedPnl.doubleValue()));
            } else {
                this.pnl = new SimpleStringProperty("$0.00");
            }
        }

        public String getTicker() { return ticker.get(); }
        public SimpleStringProperty tickerProperty() { return ticker; }
        public SimpleStringProperty sideProperty() { return side; }
        public SimpleIntegerProperty quantityProperty() { return quantity; }
        public SimpleStringProperty avgPriceProperty() { return avgPrice; }
        public SimpleStringProperty pnlProperty() { return pnl; }
    }
}
