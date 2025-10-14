package udem.taln.ner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NERMapTypeTest {

    @Test
    void mapType_person() {
        assertEquals(NER.TYPE.PERSON, NER.mapType("PERSON"));
    }

    @Test
    void mapType_organization() {
        assertEquals(NER.TYPE.ORGANIZATION, NER.mapType("ORG"));
        assertEquals(NER.TYPE.ORGANIZATION, NER.mapType("GPE"));
    }

    @Test
    void mapType_location() {
        assertEquals(NER.TYPE.LOCATION, NER.mapType("LOC"));
        assertEquals(NER.TYPE.LOCATION, NER.mapType("FAC"));
    }

    @Test
    void mapType_default_none() {
        assertEquals(NER.TYPE.NONE, NER.mapType("PRODUCT"));
        assertEquals(NER.TYPE.NONE, NER.mapType("EVENT"));
        assertEquals(NER.TYPE.NONE, NER.mapType("SOMETHING_UNKNOWN"));
    }
}