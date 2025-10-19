package udem.taln.ner;

import udem.taln.wrapper.SpacyWrapperService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NER {
    private static final SpacyWrapperService wrapper = new SpacyWrapperService();
    private static volatile boolean initialized = false;

    // Remove static initialization and use lazy initialization instead
    private static synchronized void ensureInitialized() {
        if (!initialized) {
            try {
                wrapper.start();
                initialized = true;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize SpaCy wrapper", e);
            }
        }
    }

    public enum MODE {
        LG,
        MD,
        SM,
        TRF,
    }

    public enum TYPE {
        PERSON,
        ORGANIZATION,
        LOCATION,
        NONE;
    }

    public record PSentence(int id, List<TYPE> types) {
    }

    public static List<PSentence> execute(MODE type, Map<Integer, Analyser.Pair> text) {
        ensureInitialized(); // Ensure wrapper is initialized before use
        return switch (type) {
            case LG -> executeLG(text);
            case MD -> executeMD(text);
            case SM -> executeSM(text);
            case TRF -> new ArrayList<>();
        };
    }

    private static List<PSentence> executeLG(Map<Integer, Analyser.Pair> text) {
        List<PSentence> result = new ArrayList<>();
        for (Map.Entry<Integer, Analyser.Pair> sentence : text.entrySet()) {
//            System.out.println("[NER] LG -> calling Python for id=" + sentence.getKey());
            var labels = wrapper.getLG(sentence.getValue().sentence, sentence.getValue().target).labels;
//            System.out.println("[NER] LG <- received for id=" + sentence.getKey() + " labels=" + labels);
            var psentence = new PSentence(sentence.getKey(), mapTypes(labels));
            result.add(psentence);
        }
        return result;
    }

    private static List<PSentence> executeMD(Map<Integer, Analyser.Pair> text) {
        List<PSentence> result = new ArrayList<>();
        for (Map.Entry<Integer, Analyser.Pair> sentence : text.entrySet()) {
//            System.out.println("[NER] MD -> calling Python for id=" + sentence.getKey());
            var labels = wrapper.getMD(sentence.getValue().sentence, sentence.getValue().target).labels;
//            System.out.println("[NER] MD <- received for id=" + sentence.getKey() + " labels=" + labels);
            var psentence = new PSentence(sentence.getKey(), mapTypes(labels));
            result.add(psentence);
        }
        return result;
    }

    private static List<PSentence> executeSM(Map<Integer, Analyser.Pair> text) {
        List<PSentence> result = new ArrayList<>();
        for (Map.Entry<Integer, Analyser.Pair> sentence : text.entrySet()) {
//            System.out.println("[NER] SM -> calling Python for id=" + sentence.getKey());
            var labels = wrapper.getSM(sentence.getValue().sentence, sentence.getValue().target).labels;
//            System.out.println("[NER] SM <- received for id=" + sentence.getKey() + " labels=" + labels);
            var psentence = new PSentence(sentence.getKey(), mapTypes(labels));
            result.add(psentence);
        }
        return result;
    }

//    private static List<PSentence> executeTRF(Map<Integer, String> text) {
//        List<PSentence> result = new ArrayList<>();
//        for (Map.Entry<Integer, String> sentence : text.entrySet()) {
//            var psentence = new PSentence(sentence.getKey(), mapTypes(wrapper.getTRF(sentence.getValue()).labels));
//            result.add(psentence);
//        }
//        return result;
//    }

    /**
     * Maps Spacy labels to NER types.
     * You can find labels descriptions <a href="https://medium.com/data-science/named-entity-recognition-with-nltk-and-spacy-8c4a7d88e7da">here</a>
     *
     * @param labels the spacy extracted labels
     * @return the same list with mapped types to {@link TYPE TYPE}
     */
    private static List<TYPE> mapTypes(List<String> labels) {
        List<TYPE> result = new ArrayList<>();
        for (String label : labels) {
            result.add(mapType(label));
        }
        return result;
    }

    public static TYPE mapType(String s) {
        return switch (s) {
            case "PERSON" -> TYPE.PERSON;
            case "ORG", "NORP" -> TYPE.ORGANIZATION;
            case "GPE", "LOC" -> TYPE.LOCATION;
            default -> TYPE.NONE;
        };
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                wrapper.close();
            } catch (Exception ignored) {
            }
        }));
    }
}
