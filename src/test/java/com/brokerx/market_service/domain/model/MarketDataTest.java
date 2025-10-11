package com.brokerx.market_service.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class MarketDataTest {

    @Test
    void shouldCalculateSpreadCorrectly() {
        MarketData marketData = MarketData.builder()
                .id(1L)
                .symbol("AAPL")
                .name("Apple Inc.")
                .bid(new BigDecimal("150.00"))
                .ask(new BigDecimal("150.50"))
                .lastPrice(new BigDecimal("150.25"))
                .volume(1000000L)
                .timestamp(LocalDateTime.now())
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, new BigDecimal("0.50").compareTo(spread));
    }

    @Test
    void shouldReturnZeroSpreadWhenBidIsNull() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(null)
                .ask(new BigDecimal("150.50"))
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, BigDecimal.ZERO.compareTo(spread));
    }

    @Test
    void shouldReturnZeroSpreadWhenAskIsNull() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(new BigDecimal("150.00"))
                .ask(null)
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, BigDecimal.ZERO.compareTo(spread));
    }

    @Test
    void shouldReturnZeroSpreadWhenBothBidAndAskAreNull() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(null)
                .ask(null)
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, BigDecimal.ZERO.compareTo(spread));
    }

    @Test
    void shouldCalculateMidPriceCorrectly() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(new BigDecimal("150.00"))
                .ask(new BigDecimal("150.50"))
                .lastPrice(new BigDecimal("150.25"))
                .build();

        BigDecimal midPrice = marketData.getMidPrice();

        assertEquals(0, new BigDecimal("150.25").compareTo(midPrice));
    }

    @Test
    void shouldReturnLastPriceWhenBidIsNullForMidPrice() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(null)
                .ask(new BigDecimal("150.50"))
                .lastPrice(new BigDecimal("149.75"))
                .build();

        BigDecimal midPrice = marketData.getMidPrice();

        assertEquals(0, new BigDecimal("149.75").compareTo(midPrice));
    }

    @Test
    void shouldReturnLastPriceWhenAskIsNullForMidPrice() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(new BigDecimal("150.00"))
                .ask(null)
                .lastPrice(new BigDecimal("149.75"))
                .build();

        BigDecimal midPrice = marketData.getMidPrice();

        assertEquals(0, new BigDecimal("149.75").compareTo(midPrice));
    }

    @Test
    void shouldReturnZeroWhenAllPricesAreNull() {
        MarketData marketData = MarketData.builder()
                .symbol("AAPL")
                .bid(null)
                .ask(null)
                .lastPrice(null)
                .build();

        BigDecimal midPrice = marketData.getMidPrice();

        assertEquals(0, BigDecimal.ZERO.compareTo(midPrice));
    }

    @Test
    void shouldCreateMarketDataWithBuilder() {
        LocalDateTime timestamp = LocalDateTime.now();
        
        MarketData marketData = MarketData.builder()
                .id(1L)
                .symbol("TSLA")
                .name("Tesla Inc.")
                .lastPrice(new BigDecimal("250.00"))
                .bid(new BigDecimal("249.50"))
                .ask(new BigDecimal("250.50"))
                .volume(5000000L)
                .timestamp(timestamp)
                .build();

        assertNotNull(marketData);
        assertEquals(1L, marketData.getId());
        assertEquals("TSLA", marketData.getSymbol());
        assertEquals("Tesla Inc.", marketData.getName());
        assertEquals(0, new BigDecimal("250.00").compareTo(marketData.getLastPrice()));
        assertEquals(0, new BigDecimal("249.50").compareTo(marketData.getBid()));
        assertEquals(0, new BigDecimal("250.50").compareTo(marketData.getAsk()));
        assertEquals(5000000L, marketData.getVolume());
        assertEquals(timestamp, marketData.getTimestamp());
    }

    @Test
    void shouldHandleLargeSpread() {
        MarketData marketData = MarketData.builder()
                .symbol("VOLATILE")
                .bid(new BigDecimal("100.00"))
                .ask(new BigDecimal("110.00"))
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, new BigDecimal("10.00").compareTo(spread));
    }

    @Test
    void shouldHandleSmallSpread() {
        MarketData marketData = MarketData.builder()
                .symbol("STABLE")
                .bid(new BigDecimal("100.00"))
                .ask(new BigDecimal("100.01"))
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, new BigDecimal("0.01").compareTo(spread));
    }

    @Test
    void shouldHandleZeroSpread() {
        MarketData marketData = MarketData.builder()
                .symbol("LOCKED")
                .bid(new BigDecimal("100.00"))
                .ask(new BigDecimal("100.00"))
                .build();

        BigDecimal spread = marketData.getSpread();

        assertEquals(0, BigDecimal.ZERO.compareTo(spread));
    }

    @Test
    void shouldHandleDecimalPrices() {
        MarketData marketData = MarketData.builder()
                .symbol("GOOGL")
                .bid(new BigDecimal("2750.25"))
                .ask(new BigDecimal("2751.75"))
                .lastPrice(new BigDecimal("2751.00"))
                .build();

        BigDecimal spread = marketData.getSpread();
        BigDecimal midPrice = marketData.getMidPrice();

        assertEquals(0, new BigDecimal("1.50").compareTo(spread));
        assertEquals(0, new BigDecimal("2751.00").compareTo(midPrice));
    }

    @Test
    void shouldSetAndGetAllFields() {
        MarketData marketData = new MarketData();
        LocalDateTime timestamp = LocalDateTime.now();

        marketData.setId(100L);
        marketData.setSymbol("MSFT");
        marketData.setName("Microsoft Corporation");
        marketData.setLastPrice(new BigDecimal("350.00"));
        marketData.setBid(new BigDecimal("349.75"));
        marketData.setAsk(new BigDecimal("350.25"));
        marketData.setVolume(3000000L);
        marketData.setTimestamp(timestamp);

        assertEquals(100L, marketData.getId());
        assertEquals("MSFT", marketData.getSymbol());
        assertEquals("Microsoft Corporation", marketData.getName());
        assertEquals(0, new BigDecimal("350.00").compareTo(marketData.getLastPrice()));
        assertEquals(0, new BigDecimal("349.75").compareTo(marketData.getBid()));
        assertEquals(0, new BigDecimal("350.25").compareTo(marketData.getAsk()));
        assertEquals(3000000L, marketData.getVolume());
        assertEquals(timestamp, marketData.getTimestamp());
    }

    @Test
    void shouldHandleHighVolumeStocks() {
        MarketData marketData = MarketData.builder()
                .symbol("SPY")
                .lastPrice(new BigDecimal("450.00"))
                .bid(new BigDecimal("449.99"))
                .ask(new BigDecimal("450.01"))
                .volume(100000000L) // 100M volume
                .build();

        assertEquals(100000000L, marketData.getVolume());
        assertEquals(0, new BigDecimal("0.02").compareTo(marketData.getSpread()));
    }
}
