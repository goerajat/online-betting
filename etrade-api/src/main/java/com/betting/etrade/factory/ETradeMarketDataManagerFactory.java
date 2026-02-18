package com.betting.etrade.factory;

import com.betting.etrade.adapter.ETradeMarketDataManager;
import com.betting.etrade.manager.MarketDataManager;
import com.betting.marketdata.factory.MarketDataManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Factory for creating E*TRADE MarketDataManager instances.
 *
 * <p>This factory creates ETradeMarketDataManager instances that implement
 * the generic MarketDataManager interface.</p>
 *
 * <h2>Configuration Properties:</h2>
 * <ul>
 *   <li>{@code consumer.key} - E*TRADE API consumer key (required)</li>
 *   <li>{@code consumer.secret} - E*TRADE API consumer secret (required)</li>
 *   <li>{@code use.sandbox} - Use sandbox environment (default: true)</li>
 *   <li>{@code poll.interval.seconds} - Quote polling interval (default: 5)</li>
 * </ul>
 */
public class ETradeMarketDataManagerFactory implements MarketDataManagerFactory {

    private static final Logger log = LoggerFactory.getLogger(ETradeMarketDataManagerFactory.class);
    private static final String PROVIDER_NAME = "ETRADE";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public com.betting.marketdata.api.MarketDataManager create(Properties properties) {
        log.info("Creating E*TRADE MarketDataManager");

        // Create the underlying E*TRADE MarketDataManager
        MarketDataManager etradeManager = MarketDataManager.fromProperties(properties);

        // Wrap it in the adapter
        return new ETradeMarketDataManager(etradeManager);
    }

    @Override
    public com.betting.marketdata.api.MarketDataManager create(String propertiesFilePath) {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(Path.of(propertiesFilePath))) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file: " + propertiesFilePath, e);
        }
        return create(props);
    }
}
