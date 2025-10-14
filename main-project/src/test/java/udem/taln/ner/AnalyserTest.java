package udem.taln.ner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyserTest {

    @Test
    void format_and_analyse_withoutLLM_correctPrediction_scores1() {
        Analyser analyser = new Analyser();
        List<String> input = List.of("The capital of [Paris_{LOC}] is nice.");
        Map<Integer, Analyser.Pair> formatted = analyser.format(input, false);

        assertEquals(1, formatted.size());
        assertEquals("The capital of Paris is nice.", formatted.get(0).sentence);

        List<NER.PSentence> predicted = List.of(new NER.PSentence(0, List.of(NER.TYPE.LOCATION)));
        double score = analyser.analyse(predicted);
        assertEquals(1.0, score, 1e-9);
    }

    @Test
    void format_and_analyse_withoutLLM_incorrectPrediction_scores0() {
        Analyser analyser = new Analyser();
        List<String> input = List.of("Meet [Alice_{PERSON}] at [Montreal_{LOC}].");
        Map<Integer, Analyser.Pair> formatted = analyser.format(input, false);

        assertEquals("Meet Alice at Montreal.", formatted.get(0).sentence);

        // Wrong labels on purpose (NONE)
        List<NER.PSentence> predictedWrong = List.of(new NER.PSentence(0, List.of(NER.TYPE.NONE, NER.TYPE.NONE)));
        double scoreWrong = analyser.analyse(predictedWrong);
        assertEquals(0.0, scoreWrong, 1e-9);
    }

    @Test
    void format_withLLM_keepsDoubleBrackets_and_analyse_stillWorks() {
        Analyser analyser = new Analyser();
        // With llm=true, '[' and ']' are doubled in the formatted text
        List<String> input = List.of("Target [Bob_{PERSON}] is here.", "Company [OpenAI_{ORG}] grows.");
        Map<Integer, Analyser.Pair> formatted = analyser.format(input, true);

        assertEquals(2, formatted.size());
        // First sentence contains already "[[" in input, analyser doubles only when it sees '[' from its own parsing,
        // so we assert that the label content is removed but text preserved.
        assertEquals("Target [[Bob]] is here.", formatted.get(0).sentence);
        assertEquals("Company [[OpenAI]] grows.", formatted.get(1).sentence);

        List<NER.PSentence> predicted = List.of(
                new NER.PSentence(0, List.of(NER.TYPE.PERSON)),
                new NER.PSentence(1, List.of(NER.TYPE.ORGANIZATION))
        );
        double score = analyser.analyse(predicted);
        assertEquals(1.0, score, 1e-9);
    }

    @Test
    void analyse_emptyOrNull_returns0() {
        Analyser analyser = new Analyser();
        analyser.format(List.of(), false);
        assertEquals(0.0, analyser.analyse(List.of()), 1e-9);
        assertEquals(0.0, analyser.analyse(null), 1e-9);
    }
}