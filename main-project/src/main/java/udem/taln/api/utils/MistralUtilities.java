package udem.taln.api.utils;

import java.io.InputStream;
import java.util.Properties;

public class MistralUtilities {

    private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_MAX_RETRIES = 3;

    public static String getApiKey() {

        String key = System.getenv("mistral_key");
        if (key != null && !key.isBlank()) return key;

        try (InputStream is = MistralUtilities.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(is);
                key = p.getProperty("mistral_key");
                if (key != null && !key.isBlank()) return key;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static int getRequestTimeoutSeconds() {
        String env = System.getenv("REQUEST_TIMEOUT_SECONDS");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        try (InputStream is = MistralUtilities.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(is);
                String val = p.getProperty("REQUEST_TIMEOUT_SECONDS");
                if (val != null && !val.isBlank()) return Integer.parseInt(val);
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_REQUEST_TIMEOUT_SECONDS;
    }

    public static int getMaxRetries() {
        String env = System.getenv("MISTRAL_MAX_RETRIES");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        try (InputStream is = MistralUtilities.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                Properties p = new Properties();
                p.load(is);
                String val = p.getProperty("MISTRAL_MAX_RETRIES");
                if (val != null && !val.isBlank()) return Integer.parseInt(val);
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_MAX_RETRIES;
    }
}