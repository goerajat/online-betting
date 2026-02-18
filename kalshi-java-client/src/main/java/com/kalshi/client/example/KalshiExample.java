package com.kalshi.client.example;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.model.*;
import com.kalshi.client.service.EventService;
import com.kalshi.client.service.MarketService;
import com.kalshi.client.service.OrderService;

import java.nio.file.Paths;
import java.util.List;

/**
 * Example demonstrating usage of the Kalshi Java Client library.
 */
public class KalshiExample {

    public static void main(String[] args) {
        // Configuration - replace with your actual credentials
        String apiKeyId = "YOUR_API_KEY_ID";
        String privateKeyPath = "path/to/your/private_key.pem";

        // Build the API client
        KalshiApi api = KalshiApi.builder()
                .credentialsFromFile(apiKeyId, Paths.get(privateKeyPath))
                // Use .useDemo() for sandbox environment
                .build();

        try {
            // Example 1: Get series list
            System.out.println("=== Series ===");
            List<Series> seriesList = api.series().getSeriesList();
            for (Series series : seriesList.subList(0, Math.min(5, seriesList.size()))) {
                System.out.println(series.getTicker() + " - " + series.getTitle());
            }

            // Example 2: Get events
            System.out.println("\n=== Events ===");
            List<Event> events = api.events().getEvents(
                EventService.EventQuery.builder()
                    .limit(5)
                    .build()
            );
            for (Event event : events) {
                System.out.println(event.getEventTicker() + " - " + event.getTitle());
            }

            // Example 3: Get markets
            System.out.println("\n=== Markets ===");
            List<Market> markets = api.markets().getMarkets(
                MarketService.MarketQuery.builder()
                    .status("open")
                    .limit(5)
                    .build()
            );
            for (Market market : markets) {
                System.out.printf("%s - %s (Yes: %s, No: %s)%n",
                    market.getTicker(),
                    market.getTitle(),
                    market.getYesBidDollars(),
                    market.getNoBidDollars()
                );
            }

            // Example 4: Get orderbook for a specific market
            if (!markets.isEmpty()) {
                String ticker = markets.get(0).getTicker();
                System.out.println("\n=== Orderbook for " + ticker + " ===");
                Orderbook orderbook = api.markets().getOrderbook(ticker);
                System.out.println("Best Yes Bid: " + orderbook.getBestYesBid() + " cents");
                System.out.println("Best No Bid: " + orderbook.getBestNoBid() + " cents");
                System.out.println("Total Yes Depth: " + orderbook.getTotalYesDepth());
                System.out.println("Total No Depth: " + orderbook.getTotalNoDepth());
            }

            // Example 5: Get open orders
            System.out.println("\n=== Open Orders ===");
            List<Order> openOrders = api.orders().getOpenOrders();
            System.out.println("Number of open orders: " + openOrders.size());
            for (Order order : openOrders) {
                System.out.printf("Order %s: %s %s %s @ %d cents (%d/%d filled)%n",
                    order.getOrderId(),
                    order.getAction(),
                    order.getSide(),
                    order.getTicker(),
                    order.getSide().equals("yes") ? order.getYesPrice() : order.getNoPrice(),
                    order.getFillCount(),
                    order.getInitialCount()
                );
            }

            // Example 6: Get recent fills
            System.out.println("\n=== Recent Fills ===");
            List<Trade> fills = api.orders().getFills(
                OrderService.FillQuery.builder()
                    .limit(10)
                    .build()
            );
            System.out.println("Number of recent fills: " + fills.size());
            for (Trade fill : fills) {
                System.out.printf("Fill %s: %s %s %s x%d @ %d cents%n",
                    fill.getEffectiveId(),
                    fill.getAction(),
                    fill.getSide(),
                    fill.getEffectiveTicker(),
                    fill.getCount(),
                    fill.getYesPrice()
                );
            }

            // Example 7: Create an order (commented out to prevent accidental execution)
            /*
            System.out.println("\n=== Creating Order ===");
            Order newOrder = api.orders().createOrder(
                CreateOrderRequest.builder()
                    .ticker("SOME-TICKER")
                    .side(OrderSide.YES)
                    .action(OrderAction.BUY)
                    .count(10)
                    .yesPrice(50) // 50 cents
                    .timeInForce(TimeInForce.GOOD_TILL_CANCELED)
                    .build()
            );
            System.out.println("Created order: " + newOrder.getOrderId());

            // Or use convenience methods:
            Order buyOrder = api.orders().buyYes("SOME-TICKER", 10, 50);
            Order sellOrder = api.orders().sellNo("SOME-TICKER", 5, 40);
            */

            // Example 8: Cancel an order (commented out)
            /*
            String orderIdToCancel = "some-order-id";
            Order canceledOrder = api.orders().cancelOrder(orderIdToCancel);
            System.out.println("Canceled order: " + canceledOrder.getOrderId());
            */

            // Example 9: Amend an order (commented out)
            /*
            String orderIdToAmend = "some-order-id";
            Order amendedOrder = api.orders().amendOrder(orderIdToAmend,
                AmendOrderRequest.builder()
                    .yesPrice(55)  // New price: 55 cents
                    .count(15)     // New quantity: 15 contracts
                    .build()
            );
            System.out.println("Amended order: " + amendedOrder.getOrderId());
            */

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
