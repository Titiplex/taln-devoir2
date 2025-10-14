package udem.taln.ner;

import java.util.*;

public class Analyser {
    private final HashMap<Integer, List<NER.TYPE>> labelsMap;

    public Analyser() {
        labelsMap = new HashMap<>();
    }

    public static class Pair {
        public String target;
        public String sentence;

        public Pair(String target, String sentence) {
            this.target = target;
            this.sentence = sentence;
        }
    }

    public Map<Integer, Pair> format(List<String> text, boolean llm) {
        Map<Integer, Pair> result = new HashMap<>();
        int id = 0;
        for (var sentence : text) {
            StringBuilder properSentence = new StringBuilder();
            StringBuilder currentLabel = new StringBuilder();
            StringBuilder target = new StringBuilder();

            List<NER.TYPE> labels = new ArrayList<>();

            boolean hasLabel = false;
            boolean isLabel = false;
            boolean isTarget = false;

            for (char c : sentence.toCharArray()) {
                if (c == '[' && !hasLabel) {
                    hasLabel = true;
                    isTarget = true;
                    if (llm) properSentence.append(c).append(c);
                    continue;
                }
                if (c == ']' && hasLabel && isTarget) {
                    isTarget = false;
                    if (llm)
                        properSentence.append(c).append(c);
                    continue;
                }
                if (c == '_' && hasLabel && !isTarget) continue;
                if (c == '{' && hasLabel) {
                    isLabel = true;
                    continue;
                }
                if (c == '}' && hasLabel && isLabel) {
                    isLabel = false;
                    labels.add(NER.mapType(currentLabel.toString()));
                    currentLabel = new StringBuilder();
                    continue;
                }
                if (hasLabel && isLabel) {
                    currentLabel.append(c);
                    continue;
                }
                if (hasLabel && isTarget) {
                    target.append(c);
                }

                properSentence.append(c);
            }
            result.put(id, new Pair(target.toString(), properSentence.toString()));
            labelsMap.put(id, labels);
            id++;
        }
        return result;
    }

    public double analyse(List<NER.PSentence> result) {
        if (result == null || result.isEmpty()) return 0;
        if (result.size() != labelsMap.size())
            throw new IllegalArgumentException("Result and pre-processed sentences map size must be equal");

        Map<Integer, List<NER.TYPE>> predicted = new HashMap<>(result.size());
        for (var s : result) {
            predicted.put(s.id(), s.types() == null ? List.of() : s.types());
        }
        double score = 0;
        for (Map.Entry<Integer, List<NER.TYPE>> entry : labelsMap.entrySet()) {
            int id = entry.getKey();
            List<NER.TYPE> initList = entry.getValue() == null ? List.of() : entry.getValue();
            List<NER.TYPE> predList = predicted.getOrDefault(id, List.of());

            Set<NER.TYPE> good = new HashSet<>(initList);
            Set<NER.TYPE> pred = new HashSet<>(predList);

            if (good.equals(pred)) score++;
        }
        return score / (double) labelsMap.size();
    }
}
