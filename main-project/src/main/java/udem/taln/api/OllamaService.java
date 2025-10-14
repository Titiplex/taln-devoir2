package udem.taln.api;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.generate.OllamaGenerateRequest;
import io.github.ollama4j.models.response.OllamaResult;
import udem.taln.ner.Analyser;
import udem.taln.ner.NER;

import java.util.List;

public class OllamaService implements LLMService {
    private final String model = "mistral:7b";

    @Override
    public List<NER.TYPE> process(Analyser.Pair sentence) {
        try {
            Ollama ollama = OllamaUtilities.setUp();
            ollama.pullModel(model);
            String fullPrompt = getFullPrompt(sentence);

            OllamaResult result =
                    ollama.generate(
                            new OllamaGenerateRequest(model, fullPrompt),
                            null);

            String response = result.getResponse();
            // extract and map
            String typeToken = extractBetweenDoubleBrackets(response);
            if (typeToken == null || typeToken.isBlank()) {
                typeToken = response.trim();
            }
            typeToken = typeToken
                    .replace("ANSWER", "")
                    .replace("answer", "")
                    .replaceAll("[^A-Za-z]", "")
                    .toUpperCase();

            NER.TYPE mapped = NER.mapType(typeToken);
            return List.of(mapped);
//            return Arrays.stream(result.getResponse().split(",")).map(NER::mapType).toList();
        } catch (Exception e) {
            System.err.println("Could not setup Ollama API: " + e.getMessage());
        }
        return null;
    }

    private static String getFullPrompt(Analyser.Pair sentence) {
        String prompt = """
                You are an expert in named-entity tagging.
                You will be presented sentences where a target word is presented in double square brackets,
                and your task is to predict its type, which could be either:
                 - LOC if the target word designates a location,
                 - PERSON if the target word designates a person,
                 - ORG if the target word designates an organization.
                
                 Your answer should start by: ANSWER followed by your the target type in between [[ and ]], as in: ANSWER [[PERSON]]
                 Be brief, do not think! I do not want or need any explanation, just the solution.
                """;

        String sentenceString = sentence.sentence;
        return prompt + "\nSentence:\n" + sentenceString + "\n";
    }

    private String extractBetweenDoubleBrackets(String s) {
        if (s == null) return null;
        int open1 = s.indexOf('[');
        while (open1 >= 0 && open1 + 1 < s.length() && s.charAt(open1 + 1) != '[') {
            open1 = s.indexOf('[', open1 + 1);
        }
        if (open1 < 0 || open1 + 1 >= s.length()) return null;
        int open2 = open1 + 1;
        if (s.charAt(open2) != '[') return null;

        int close2 = s.indexOf("]]", open2 + 1);
        if (close2 < 0) return null;

        return s.substring(open2 + 1, close2).trim();
    }

    public String getModel() {
        return model;
    }
}
