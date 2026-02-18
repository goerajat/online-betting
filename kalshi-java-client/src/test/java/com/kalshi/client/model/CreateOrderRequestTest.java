package com.kalshi.client.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreateOrderRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testBuilderWithRequiredFields() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .ticker("TEST-TICKER")
                .side(OrderSide.YES)
                .action(OrderAction.BUY)
                .count(10)
                .yesPrice(50)
                .build();

        assertEquals("TEST-TICKER", request.getTicker());
        assertEquals("yes", request.getSide());
        assertEquals("buy", request.getAction());
        assertEquals(10, request.getCount());
        assertEquals(50, request.getYesPrice());
        assertEquals("limit", request.getType());
    }

    @Test
    void testBuilderWithAllFields() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .ticker("TEST-TICKER")
                .side(OrderSide.NO)
                .action(OrderAction.SELL)
                .count(5)
                .noPrice(40)
                .type(OrderType.LIMIT)
                .timeInForce(TimeInForce.GOOD_TILL_CANCELED)
                .clientOrderId("client-123")
                .postOnly(true)
                .reduceOnly(false)
                .cancelOrderOnPause(true)
                .build();

        assertEquals("TEST-TICKER", request.getTicker());
        assertEquals("no", request.getSide());
        assertEquals("sell", request.getAction());
        assertEquals(5, request.getCount());
        assertEquals(40, request.getNoPrice());
        assertEquals("limit", request.getType());
        assertEquals("good_till_canceled", request.getTimeInForce());
        assertEquals("client-123", request.getClientOrderId());
        assertTrue(request.getPostOnly());
        assertFalse(request.getReduceOnly());
        assertTrue(request.getCancelOrderOnPause());
    }

    @Test
    void testBuilderValidationMissingTicker() {
        assertThrows(IllegalStateException.class, () ->
            CreateOrderRequest.builder()
                .side(OrderSide.YES)
                .action(OrderAction.BUY)
                .count(10)
                .build()
        );
    }

    @Test
    void testBuilderValidationMissingSide() {
        assertThrows(IllegalStateException.class, () ->
            CreateOrderRequest.builder()
                .ticker("TEST")
                .action(OrderAction.BUY)
                .count(10)
                .build()
        );
    }

    @Test
    void testBuilderValidationInvalidCount() {
        assertThrows(IllegalStateException.class, () ->
            CreateOrderRequest.builder()
                .ticker("TEST")
                .side(OrderSide.YES)
                .action(OrderAction.BUY)
                .count(0)
                .build()
        );
    }

    @Test
    void testJsonSerialization() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .ticker("TEST-TICKER")
                .side(OrderSide.YES)
                .action(OrderAction.BUY)
                .count(10)
                .yesPrice(50)
                .build();

        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("\"ticker\":\"TEST-TICKER\""));
        assertTrue(json.contains("\"side\":\"yes\""));
        assertTrue(json.contains("\"action\":\"buy\""));
        assertTrue(json.contains("\"count\":10"));
        assertTrue(json.contains("\"yes_price\":50"));
    }
}
