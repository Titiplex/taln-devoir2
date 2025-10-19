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
        int i = 1;
        for (var sentence : text.entrySet()) {
            System.out.println("Ollama process nb : "+i);
            result.add(new NER.PSentence(sentence.getKey(), this.process(sentence.getValue())));
            i++;
        }

        return result;
    }

    default String getFullPrompt(Analyser.Pair sentence) {
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
}
