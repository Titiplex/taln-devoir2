package udem.taln.api;

import io.github.ollama4j.Ollama;
import org.junit.jupiter.api.Test;
import udem.taln.api.utils.OllamaUtilities;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OllamaUtilitiesTest {

    @Test
    void setUp_usesTestProperties_whenEnvMissing_returnsClient() throws Exception {
        // Since tests typically run without the env vars, OllamaUtilities falls back to test-config.properties
        // where we set USE_EXTERNAL_OLLAMA_HOST=true and a dummy OLLAMA_HOST.
        Ollama client = OllamaUtilities.setUp();
        assertNotNull(client, "Expected Ollama client to be constructed using test-config.properties");
        // We don't call remote methods here; just ensure construction succeeds and no exception is thrown.
    }

    @Test
    void getFromEnvVar_missing_returnsNullAndDoesNotThrow() {
        // A random key that should not exist
        String val = OllamaUtilities.getFromEnvVar("THIS_ENV_KEY_SHOULD_NOT_EXIST_12345");
        assertNull(val, "Missing env var should return null");
        // no exception expected
    }
}