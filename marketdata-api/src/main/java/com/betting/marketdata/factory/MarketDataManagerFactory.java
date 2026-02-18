package com.betting.marketdata.factory;

import com.betting.marketdata.api.MarketDataManager;

import java.util.Properties;

/**
 * Factory interface for creating MarketDataManager instances.
 *
 * <p>Implementations of this interface create MarketDataManager instances
 * for specific market data providers (e.g., E*TRADE, Kalshi).</p>
 */
public interface MarketDataManagerFactory {

    /**
     * Get the provider name this factory creates managers for.
     *
     * @return the provider name (e.g., "ETRADE", "KALSHI")
     */
    String getProviderName();

    /**
     * Create a MarketDataManager from the given properties.
     *
     * @param properties the configuration properties
     * @return a new MarketDataManager instance
     */
    MarketDataManager create(Properties properties);

    /**
     * Create a MarketDataManager from a properties file.
     *
     * @param propertiesFilePath path to the configuration file
     * @return a new MarketDataManager instance
     */
    MarketDataManager create(String propertiesFilePath);
}
