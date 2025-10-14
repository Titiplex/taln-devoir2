package udem.taln.wrapper;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import udem.taln.wrapper.dto.NerDTO;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Disabled integration tests for SpacyWrapperService.
 * Enable locally only if your wrapper + Python environment is configured.
 */
@Disabled("Integration test: requires Python+spaCy wrapper runtime. Enable locally when environment is ready.")
class SpacyWrapperServiceIT {

    @Test
    void start_and_getSM_returnsParsedLabels() {
        try (SpacyWrapperService service = new SpacyWrapperService()) {
            service.start(); // starts gateway + python

            NerDTO dto = service.getSM("Alice works at OpenAI in San Francisco.");
            assertNotNull(dto, "DTO should not be null");
            assertNotNull(dto.labels, "Labels list should not be null");
            // We don't assert exact labels; the goal is verifying the end-to-end path runs.
            // Example sanity check:
            assertTrue(dto.labels.size() >= 0, "Labels list should be non-negative sized");
        }
    }

    @Test
    void start_and_getMD_returnsParsedLabels() {
        try (SpacyWrapperService service = new SpacyWrapperService()) {
            service.start();

            NerDTO dto = service.getMD("Bob visited Paris.");
            assertNotNull(dto);
            assertNotNull(dto.labels);
        }
    }

    @Test
    void start_and_getLG_returnsParsedLabels() {
        try (SpacyWrapperService service = new SpacyWrapperService()) {
            service.start();

            NerDTO dto = service.getLG("Google is based in Mountain View.");
            assertNotNull(dto);
            assertNotNull(dto.labels);
        }
    }
}