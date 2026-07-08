package com.waferrobot.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application configuration from a Java .properties file.
 * Reusable by both WaferRobotController and WaferRobotSimulator.
 */
public class AppConfig {

    private final Properties properties = new Properties();

    /**
     * Loads a .properties file from the given file path.
     *
     * @param filename path to the .properties file
     * @throws RuntimeException if the file cannot be opened or read
     */
    public void load(String filename) {
        try (InputStream in = new FileInputStream(filename)) {
            properties.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config file: " + filename + " — " + e.getMessage(), e);
        }
    }

    /**
     * Returns the value for the given key.
     *
     * @param key property key
     * @return the value, or null if the key does not exist
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Returns the value for the given key, or a default if not found.
     *
     * @param key          property key
     * @param defaultValue fallback value
     * @return the value or defaultValue
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Returns the integer value for the given key.
     *
     * @param key property key
     * @return integer value
     * @throws NumberFormatException if the value is not a valid integer
     */
    public int getInt(String key) {
        String value = getProperty(key);
        if (value == null) {
            throw new RuntimeException("Missing required config key: " + key);
        }
        return Integer.parseInt(value.trim());
    }
}
