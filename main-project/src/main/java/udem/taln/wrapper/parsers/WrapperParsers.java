package udem.taln.wrapper.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import udem.taln.wrapper.dto.NerDTO;

public class WrapperParsers {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static NerDTO parseNER(String json) {
        try {
            return MAPPER.readValue(json, NerDTO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
