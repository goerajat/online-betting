package com.betting.marketdata.factory;

import com.betting.marketdata.api.MarketDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A configurable factory that reads properties to determine which
 * MarketDataManagerFactory implementation to use.
 *
 * <p>This factory uses the {@code marketdata.provider} property to select
 * the appropriate factory implementation. Factory implementations are
 * discovered using Java's ServiceLoader mechanism or can be registered
 * programmatically.</p>
 *
 * <h2>Configuration Properties:</h2>
 * <ul>
 *   <li>{@code marketdata.provider} - The provider name (e.g., "ETRADE", "KALSHI")</li>
 *   <li>Provider-specific properties as required by the chosen factory</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Using properties file
 * MarketDataManager manager = ConfigurableMarketDataManagerFactory
 *     .getInstance()
 *     .createFromFile("marketdata.properties");
 *
 * // Or with Properties object
 * Properties props = new Properties();
 * props.setProperty("marketdata.provider", "ETRADE");
 * props.setProperty("consumer.key", "your-key");
 * props.setProperty("consumer.secret", "your-secret");
 *
 * MarketDataManager manager = ConfigurableMarketDataManagerFactory
 *     .getInstance()
 *     .create(props);
 * }</pre>
 */
public class ConfigurableMarketDataManagerFactory {

    private static final Logger log = LoggerFactory.getLogger(ConfigurableMarketDataManagerFactory.class);

    public static final String PROVIDER_PROPERTY = "marketdata.provider";

    private static final ConfigurableMarketDataManagerFactory INSTANCE = new ConfigurableMarketDataManagerFactory();

    private final Map<String, MarketDataManagerFactory> factories = new ConcurrentHashMap<>();

    private ConfigurableMarketDataManagerFactory() {
        // Load factories via ServiceLoader
        loadFactoriesFromServiceLoader();
    }

    /**
     * Get the singleton instance.
     *
     * @return the ConfigurableMarketDataManagerFactory instance
     */
    public static ConfigurableMarketDataManagerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Register a factory for a specific provider.
     *
     * @param factory the factory to register
     */
    public void registerFactory(MarketDataManagerFactory factory) {
        String providerName = factory.getProviderName().toUpperCase();
        factories.put(providerName, factory);
        log.info("Registered MarketDataManagerFactory for provider: {}", providerName);
    }

    /**
     * Unregister a factory for a specific provider.
     *
     * @param providerName the provider name to unregister
     * @return the removed factory, or null if not found
     */
    public MarketDataManagerFactory unregisterFactory(String providerName) {
        return factories.remove(providerName.toUpperCase());
    }

    /**
     * Get the factory for a specific provider.
     *
     * @param providerName the provider name
     * @return the factory, or null if not found
     */
    public MarketDataManagerFactory getFactory(String providerName) {
        return factories.get(providerName.toUpperCase());
    }

    /**
     * Check if a factory is registered for the given provider.
     *
     * @param providerName the provider name
     * @return true if a factory is registered
     */
    public boolean hasFactory(String providerName) {
        return factories.containsKey(providerName.toUpperCase());
    }

    /**
     * Get all registered provider names.
     *
     * @return set of registered provider names
     */
    public java.util.Set<String> getRegisteredProviders() {
        return new java.util.HashSet<>(factories.keySet());
    }

    /**
     * Create a MarketDataManager from a properties file.
     *
     * @param propertiesFilePath path to the configuration file
     * @return a new MarketDataManager instance
     * @throws IllegalArgumentException if provider is not specified or not supported
     * @throws RuntimeException if the file cannot be read
     */
    public MarketDataManager createFromFile(String propertiesFilePath) {
        return createFromFile(Path.of(propertiesFilePath));
    }

    /**
     * Create a MarketDataManager from a properties file.
     *
     * @param propertiesFilePath path to the configuration file
     * @return a new MarketDataManager instance
     * @throws IllegalArgumentException if provider is not specified or not supported
     * @throws RuntimeException if the file cannot be read
     */
    public MarketDataManager createFromFile(Path propertiesFilePath) {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(propertiesFilePath)) {
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties file: " + propertiesFilePath, e);
        }
        return create(props);
    }

    /**
     * Create a MarketDataManager from Properties.
     *
     * @param properties the configuration properties
     * @return a new MarketDataManager instance
     * @throws IllegalArgumentException if provider is not specified or not supported
     */
    public MarketDataManager create(Properties properties) {
        String providerName = properties.getProperty(PROVIDER_PROPERTY);

        if (providerName == null || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required property: " + PROVIDER_PROPERTY +
                    ". Available providers: " + factories.keySet());
        }

        providerName = providerName.trim().toUpperCase();
        MarketDataManagerFactory factory = factories.get(providerName);

        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown market data provider: " + providerName +
                    ". Available providers: " + factories.keySet());
        }

        log.info("Creating MarketDataManager for provider: {}", providerName);
        return factory.create(properties);
    }

    /**
     * Load factories using ServiceLoader.
     */
    private void loadFactoriesFromServiceLoader() {
        ServiceLoader<MarketDataManagerFactory> loader = ServiceLoader.load(MarketDataManagerFactory.class);
        for (MarketDataManagerFactory factory : loader) {
            registerFactory(factory);
        }
    }

    /**
     * Reload factories from ServiceLoader (useful after adding new implementations).
     */
    public void reloadFactories() {
        loadFactoriesFromServiceLoader();
    }
}
