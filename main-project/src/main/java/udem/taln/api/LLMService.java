package udem.taln.api;

import udem.taln.ner.Analyser;
import udem.taln.ner.NER;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface LLMService {
    List<NER.TYPE> process(Analyser.Pair sentence);

    default List<NER.PSentence> execute(Map<Integer, Analyser.Pair> text) {
        List<NER.PSentence> result = new ArrayList<>();

        for (var sentence : text.entrySet()) {
            result.add(new NER.PSentence(sentence.getKey(), this.process(sentence.getValue())));
        }

        return result;
    }
}
