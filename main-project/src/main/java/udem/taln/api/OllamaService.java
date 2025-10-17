package udem.taln.api;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.exceptions.OllamaException;
import io.github.ollama4j.models.generate.OllamaGenerateRequest;
import io.github.ollama4j.models.response.OllamaResult;
import udem.taln.api.utils.OllamaUtilities;
import udem.taln.api.utils.ResponseHelper;
import udem.taln.ner.Analyser;
import udem.taln.ner.NER;

import java.util.List;

public class OllamaService implements LLMService {

    private final String model;
    private final Ollama ollama;

    public OllamaService(String model) {
        this.model = model;
        try {
            ollama = OllamaUtilities.setUp();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("Ollama API initialized");
        System.out.println(ollama);
        try {
            ollama.pullModel(model);
        } catch (OllamaException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Model pulled : " + model);
    }

    @Override
    public List<NER.TYPE> process(Analyser.Pair sentence) {
        try {
            String fullPrompt = getFullPrompt(sentence);

            OllamaResult result =
                    ollama.generate(
                            new OllamaGenerateRequest(model, fullPrompt),
                            null);

            String response = result.getResponse();
            // extract and map
            return ResponseHelper.getTypes(response);
//            return Arrays.stream(result.getResponse().split(",")).map(NER::mapType).toList();
        } catch (Exception e) {
            System.err.println("Could not setup Ollama API: " + e.getMessage());
        }
        return null;
    }

    public String getModel() {
        return model;
    }
}
