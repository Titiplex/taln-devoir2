package udem.taln.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OllamaServiceTest {

    @Test
    void getModel_returnsExpected() {
        OllamaService service = new OllamaService();
        assertEquals("mistral:7b", service.getModel());
    }
}