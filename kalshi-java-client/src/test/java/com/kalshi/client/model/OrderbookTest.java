package com.kalshi.client.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderbookTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testGetBestYesBid() {
        Orderbook orderbook = new Orderbook();
        orderbook.setYes(Arrays.asList(
            Arrays.asList(55, 100),
            Arrays.asList(54, 50),
            Arrays.asList(53, 25)
        ));

        assertEquals(55, orderbook.getBestYesBid());
    }

    @Test
    void testGetBestNoBid() {
        Orderbook orderbook = new Orderbook();
        orderbook.setNo(Arrays.asList(
            Arrays.asList(45, 200),
            Arrays.asList(44, 100)
        ));

        assertEquals(45, orderbook.getBestNoBid());
    }

    @Test
    void testGetBestBidEmpty() {
        Orderbook orderbook = new Orderbook();
        assertNull(orderbook.getBestYesBid());
        assertNull(orderbook.getBestNoBid());
    }

    @Test
    void testGetTotalYesDepth() {
        Orderbook orderbook = new Orderbook();
        orderbook.setYes(Arrays.asList(
            Arrays.asList(55, 100),
            Arrays.asList(54, 50),
            Arrays.asList(53, 25)
        ));

        assertEquals(175, orderbook.getTotalYesDepth());
    }

    @Test
    void testGetTotalNoDepth() {
        Orderbook orderbook = new Orderbook();
        orderbook.setNo(Arrays.asList(
            Arrays.asList(45, 200),
            Arrays.asList(44, 100)
        ));

        assertEquals(300, orderbook.getTotalNoDepth());
    }

    @Test
    void testGetTotalDepthEmpty() {
        Orderbook orderbook = new Orderbook();
        assertEquals(0, orderbook.getTotalYesDepth());
        assertEquals(0, orderbook.getTotalNoDepth());
    }

    @Test
    void testJsonDeserialization() throws Exception {
        String json = """
            {
                "yes": [[55, 100], [54, 50]],
                "no": [[45, 200]],
                "yes_dollars": [["0.55", 100], ["0.54", 50]],
                "no_dollars": [["0.45", 200]]
            }
            """;

        Orderbook orderbook = objectMapper.readValue(json, Orderbook.class);

        assertEquals(55, orderbook.getBestYesBid());
        assertEquals(45, orderbook.getBestNoBid());
        assertEquals(150, orderbook.getTotalYesDepth());
        assertEquals(200, orderbook.getTotalNoDepth());
    }

    @Test
    void testToString() {
        Orderbook orderbook = new Orderbook();
        orderbook.setYes(Arrays.asList(
            Arrays.asList(55, 100),
            Arrays.asList(54, 50)
        ));
        orderbook.setNo(List.of(Arrays.asList(45, 200)));

        String str = orderbook.toString();
        assertTrue(str.contains("yesLevels=2"));
        assertTrue(str.contains("noLevels=1"));
        assertTrue(str.contains("bestYesBid=55"));
        assertTrue(str.contains("bestNoBid=45"));
    }
}
