package com.kalshi.sample.ui;

import com.kalshi.client.model.Market;
import com.kalshi.client.model.Order;
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
import javafx.scene.text.Text;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A TableView component for displaying orders.
 * Can filter orders by market tickers to show only orders for specific markets.
 */
public class OrderBlotterTable extends TableView<OrderBlotterTable.OrderRow> {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ObservableList<OrderRow> orderRows = FXCollections.observableArrayList();
    private Set<String> filterTickers;
    private Consumer<OrderRow> onOrderSelected;
    private Consumer<OrderRow> onOrderCancel;
    private Function<String, Market> marketInfoProvider;

    public OrderBlotterTable() {
        setItems(orderRows);
        setPlaceholder(new Label("No orders"));
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
        TableColumn<OrderRow, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(c -> c.getValue().timeProperty());
        timeCol.setPrefWidth(60);
        timeCol.setCellFactory(col -> createWhiteTextCell());

        TableColumn<OrderRow, String> tickerCol = new TableColumn<>("Ticker");
        tickerCol.setCellValueFactory(c -> c.getValue().tickerProperty());
        tickerCol.setPrefWidth(100);
        tickerCol.setCellFactory(col -> createWhiteTextCell());

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
                        setFont(Font.font("Arial", FontWeight.BOLD, 11));
                    } else if ("SELL".equalsIgnoreCase(item)) {
                        setTextFill(Color.LIGHTCORAL);
                        setFont(Font.font("Arial", FontWeight.BOLD, 11));
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

        TableColumn<OrderRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        statusCol.setPrefWidth(60);
        statusCol.setCellFactory(col -> createWhiteTextCell());

        getColumns().addAll(timeCol, tickerCol, sideCol, actionCol, priceCol, qtyCol, statusCol);
    }

    private void setupRowFactory() {
        setRowFactory(tv -> new TableRow<OrderRow>() {
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
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyRowItem = new MenuItem("Copy Row");
        copyRowItem.setOnAction(e -> {
            OrderRow selected = getSelectionModel().getSelectedItem();
            if (selected != null) {
                String rowText = String.format("%s\t%s\t%s\t%s\t%d¢\t%d\t%s",
                    selected.timeProperty().get(),
                    selected.tickerProperty().get(),
                    selected.sideProperty().get(),
                    selected.actionProperty().get(),
                    selected.priceProperty().get(),
                    selected.quantityProperty().get(),
                    selected.statusProperty().get());
                copyToClipboard(rowText);
            }
        });

        MenuItem copyTickerItem = new MenuItem("Copy Ticker");
        copyTickerItem.setOnAction(e -> {
            OrderRow selected = getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyToClipboard(selected.getTicker());
            }
        });

        MenuItem copyOrderIdItem = new MenuItem("Copy Order ID");
        copyOrderIdItem.setOnAction(e -> {
            OrderRow selected = getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyToClipboard(selected.getOrderId());
            }
        });

        MenuItem cancelItem = new MenuItem("Cancel Order");
        cancelItem.setOnAction(e -> {
            OrderRow selected = getSelectionModel().getSelectedItem();
            if (selected != null && onOrderCancel != null) {
                onOrderCancel.accept(selected);
            }
        });

        contextMenu.getItems().addAll(copyRowItem, copyTickerItem, copyOrderIdItem, new SeparatorMenuItem(), cancelItem);
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
            OrderRow selected = getSelectionModel().getSelectedItem();
            if (selected != null && e.getClickCount() == 1 && onOrderSelected != null) {
                onOrderSelected.accept(selected);
            }
        });
    }

    /**
     * Set the tickers to filter by. Only orders for these tickers will be shown.
     */
    public void setFilterTickers(Set<String> tickers) {
        this.filterTickers = tickers;
    }

    /**
     * Set a callback for when an order is selected.
     */
    public void setOnOrderSelected(Consumer<OrderRow> handler) {
        this.onOrderSelected = handler;
    }

    /**
     * Set a callback for canceling an order.
     */
    public void setOnOrderCancel(Consumer<OrderRow> handler) {
        this.onOrderCancel = handler;
    }

    /**
     * Set a provider for market info (for display purposes).
     */
    public void setMarketInfoProvider(Function<String, Market> provider) {
        this.marketInfoProvider = provider;
    }

    /**
     * Update the table with new orders.
     */
    public void updateOrders(List<Order> orders) {
        orderRows.clear();
        for (Order order : orders) {
            String ticker = order.getTicker();
            // Apply filter if set
            if (filterTickers != null && !filterTickers.isEmpty() && !filterTickers.contains(ticker)) {
                continue;
            }
            Market market = marketInfoProvider != null ? marketInfoProvider.apply(ticker) : null;
            orderRows.add(new OrderRow(order, market));
        }
    }

    /**
     * Get the number of displayed orders.
     */
    public int getOrderCount() {
        return orderRows.size();
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

    /**
     * Row data for the order blotter.
     */
    public static class OrderRow {
        private final String orderId;
        private final SimpleStringProperty time;
        private final SimpleStringProperty ticker;
        private final SimpleStringProperty side;
        private final SimpleStringProperty action;
        private final SimpleIntegerProperty price;
        private final SimpleIntegerProperty quantity;
        private final SimpleStringProperty status;

        public OrderRow(Order order, Market market) {
            this.orderId = order.getOrderId();
            this.time = new SimpleStringProperty(formatTime(order.getCreatedTime()));
            this.ticker = new SimpleStringProperty(order.getTicker());
            this.side = new SimpleStringProperty(order.getSide() != null ? order.getSide().toUpperCase() : "");
            this.action = new SimpleStringProperty(order.getAction() != null ? order.getAction().toUpperCase() : "");
            Integer priceVal = order.getYesPrice() != null ? order.getYesPrice() : order.getNoPrice();
            this.price = new SimpleIntegerProperty(priceVal != null ? priceVal : 0);
            this.quantity = new SimpleIntegerProperty(order.getRemainingCount() != null ? order.getRemainingCount() : 0);
            this.status = new SimpleStringProperty(order.getStatus() != null ? order.getStatus() : "");
        }

        private static String formatTime(Instant instant) {
            if (instant == null) return "--";
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TIME_FORMATTER);
        }

        public String getOrderId() { return orderId; }
        public String getTicker() { return ticker.get(); }
        public SimpleStringProperty timeProperty() { return time; }
        public SimpleStringProperty tickerProperty() { return ticker; }
        public SimpleStringProperty sideProperty() { return side; }
        public SimpleStringProperty actionProperty() { return action; }
        public SimpleIntegerProperty priceProperty() { return price; }
        public SimpleIntegerProperty quantityProperty() { return quantity; }
        public SimpleStringProperty statusProperty() { return status; }
    }
}
