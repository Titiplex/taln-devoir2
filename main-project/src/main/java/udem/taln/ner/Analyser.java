package udem.taln.ner;

import org.jspecify.annotations.NonNull;

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

    /**
     * Formats the text for Spacy processing or LLM processing. Cleans and stores "gold" results, etc.
     *
     * @param text the text to process that will better be fed to spacy or an llm.
     * @param llm  different formatting if llm
     * @return a Map containing the id of an entry, the "gold" target and the associated formatted sentence to execute.
     */
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

    /**
     * Simple analysing by doing a ratio good results / total entries to process.
     *
     * @param result result of the processing to analyse.
     * @return a double between 0 and 1, 0 meaning no result, 1 meaning perfect result.
     */
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

    /**
     * F1 analysing.
     * <a href="https://www.geeksforgeeks.org/machine-learning/f1-score-in-machine-learning">See the inspiration.</a>
     *
     * @param result the executed text, containing the predicted labels.
     * @return three metrics : Precision, recall and F1.
     */
    public Metrics analyseF1(List<NER.PSentence> result) {
        if (result == null || result.isEmpty()) return new Metrics(0, 0, 0);
        Map<Integer, List<NER.TYPE>> predicted = new HashMap<>(result.size());
        for (var s : result) predicted.put(s.id(), s.types() == null ? List.of() : s.types());

        int tp = 0, fp = 0, fn = 0;
        for (Map.Entry<Integer, List<NER.TYPE>> goodEntry : labelsMap.entrySet()) {
            int id = goodEntry.getKey();
            Set<NER.TYPE> good = new HashSet<>(goodEntry.getValue() == null ? List.of() : goodEntry.getValue());
            Set<NER.TYPE> pred = new HashSet<>(predicted.getOrDefault(id, List.of()));

            Set<NER.TYPE> inter = new HashSet<>(pred);
            inter.retainAll(good);
            tp += inter.size();
            Set<NER.TYPE> onlyPred = new HashSet<>(pred);
            onlyPred.removeAll(good);
            fp += onlyPred.size();
            Set<NER.TYPE> onlyGold = new HashSet<>(good);
            onlyGold.removeAll(pred);
            fn += onlyGold.size();
        }
        return new Metrics(tp, fp, fn);
    }

    /**
     * Util class for F1 Metrics.
     *
     * @param tp
     * @param fp
     * @param fn
     */
    public record Metrics(int tp, int fp, int fn) {
        public double precision() {
            int d = tp + fp;
            return d == 0 ? 0.0 : (double) tp / d;
        }

        public double recall() {
            int d = tp + fn;
            return d == 0 ? 0.0 : (double) tp / d;
        }

        public double f1() {
            double p = precision(), r = recall();
            double d = p + r;
            return d == 0.0 ? 0.0 : 2 * p * r / d;
        }

        @Override
        @NonNull
        public String toString() {
            return "Metrics{tp=" + tp + ", fp=" + fp + ", fn=" + fn +
                    ", precision=" + precision() + ", recall=" + recall() + ", f1=" + f1() + "}";
        }
    }

    // -------------------------------------------------------------
    //
    // ===================== Advanced stats ========================
    //
    // -------------------------------------------------------------

    // Warning, the following were corrected by copilot, to much bugs initially. => The multiple helping class are not OC.

    // List version of the Label enum
    private final List<NER.TYPE> labelSet = List.of(NER.TYPE.PERSON, NER.TYPE.ORGANIZATION, NER.TYPE.LOCATION, NER.TYPE.NONE);

    /**
     * Accuracy, Precision, Recall, F1, Micro/Macro, Balanced accuracy, Kappa, C matrix.
     */
    public AdvancedReport analyseAdvanced(List<NER.PSentence> result) {
        if (result == null || result.isEmpty()) return AdvancedReport.empty();

        // Label Index
        List<NER.TYPE> labels = new ArrayList<>(labelSet.isEmpty()
                ? defaultLabelSet()
                : labelSet);
        // None if exists
        labels.sort((a, b) -> {
            boolean aNone = isNone(a), bNone = isNone(b);
            if (aNone && !bNone) return 1;
            if (!aNone && bNone) return -1;
            return a.ordinal() - b.ordinal();
        });
        Map<NER.TYPE, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < labels.size(); i++) idx.put(labels.get(i), i);

        // Alignment
        Map<Integer, List<NER.TYPE>> predicted = new HashMap<>(result.size());
        for (var s : result) predicted.put(s.id(), s.types() == null ? List.of() : s.types());

        int n = labelsMap.size();
        int[][] M = new int[labels.size()][labels.size()];
        int correct = 0;

        for (Map.Entry<Integer, List<NER.TYPE>> e : labelsMap.entrySet()) {
            int id = e.getKey();
            NER.TYPE g = firstOrNone(e.getValue());
            NER.TYPE p = firstOrNone(predicted.getOrDefault(id, List.of()));
            g = normalize(g);
            p = normalize(p);
            int gi = idx.get(g), pi = idx.get(p);
            M[gi][pi]++;
            if (gi == pi) correct++;
        }

        // Metrics
        double acc = n == 0 ? 0.0 : (double) correct / n;
        WilsonCI ci = wilson(acc, n, 1.96);

        Map<NER.TYPE, PRF1> perClass = perClassPRF1(M);
        MicroMacro mmAll = microMacroF1(M, labels, false);
        MicroMacro mmEnt = microMacroF1(M, labels, true);
        double balAll = balancedAccuracy(M, false);
        double balEnt = balancedAccuracy(M, true);
        double kappa = cohensKappa(M);

        return new AdvancedReport(labels, M, acc, ci.lo, ci.hi, perClass, mmAll, mmEnt, balAll, balEnt, kappa, n, correct);
    }

    /**
     * McNemar test to compare two prediction sets for the same "gold" set.
     * Enhanced by copilot with sub-functions, to correct errors and have more clarity.
     * <a href="https://en.wikipedia.org/wiki/McNemar%27s_test#Example_implementation_in_Python">Inspired by Wikipedia</a>
     */
    public McNemar mcnemar(List<NER.PSentence> predA, List<NER.PSentence> predB) {
        if (predA == null || predB == null || predA.size() != predB.size() || predA.size() != labelsMap.size()) {
            System.out.println(predA.size() + " " + predB.size() + " " + labelsMap.size());
            throw new IllegalArgumentException("PredA, PredB, and gold must have same size.");
        }

        if (labelSet.isEmpty()) {
            defaultLabelSet();
        }

        int n01 = 0, n10 = 0; // A correct & B wrong / A wrong & B correct

        Map<Integer, List<NER.TYPE>> A = new HashMap<>(), B = new HashMap<>();
        for (var s : predA) A.put(s.id(), s.types() == null ? List.of() : s.types());
        for (var s : predB) B.put(s.id(), s.types() == null ? List.of() : s.types());

        for (Map.Entry<Integer, List<NER.TYPE>> e : labelsMap.entrySet()) {
            int id = e.getKey();
            NER.TYPE g = firstOrNone(e.getValue());
            NER.TYPE a = firstOrNone(A.getOrDefault(id, List.of()));
            NER.TYPE b = firstOrNone(B.getOrDefault(id, List.of()));
            boolean ca = normalize(a) == normalize(g);
            boolean cb = normalize(b) == normalize(g);
            if (ca && !cb) n01++;
            else if (cb && !ca) n10++;
        }
        double p = exactBinomialTwoSided(n01, n10);
        return new McNemar(n01, n10, p);
    }

    private static NER.TYPE firstOrNone(List<NER.TYPE> list) {
        if (list == null || list.isEmpty()) return noneFallback();
        return list.getFirst() == null ? noneFallback() : list.getFirst();
    }

    private static boolean isNone(NER.TYPE t) {
        return t != null && t.name().equalsIgnoreCase("NONE");
    }

    private static NER.TYPE noneFallback() {
        try {
            return NER.TYPE.valueOf("NONE");
        } catch (Exception e) {
            return null;
        }
    }

    private static NER.TYPE normalize(NER.TYPE t) {
        var labels = defaultLabelSet();
        if (t == null) return labels.contains(noneFallback()) ? noneFallback() : labels.getLast();
        if (labels.contains(t)) return t;

        for (NER.TYPE x : labels) if (x.name().equalsIgnoreCase(t.name())) return x;
        return labels.contains(noneFallback()) ? noneFallback() : labels.getLast();
    }

    private static List<NER.TYPE> defaultLabelSet() {
        List<NER.TYPE> out = new ArrayList<>();
        for (String n : List.of("PERSON", "ORGANIZATION", "LOCATION", "NONE")) {
            try {
                out.add(NER.TYPE.valueOf(n));
            } catch (Exception ignored) {
            }
        }
        if (out.isEmpty()) out.addAll(Arrays.asList(NER.TYPE.values()));
        return out;
    }

    private static Map<NER.TYPE, PRF1> perClassPRF1(int[][] M) {
        var labels = defaultLabelSet();
        int L = labels.size();
        Map<NER.TYPE, PRF1> map = new LinkedHashMap<>();
        for (int i = 0; i < L; i++) {
            int tp = M[i][i];
            int fp = 0, fn = 0;
            for (int r = 0; r < L; r++) if (r != i) fp += M[r][i];
            for (int c = 0; c < L; c++) if (c != i) fn += M[i][c];
            double p = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            double r = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            double f1 = (p + r) > 0 ? 2 * p * r / (p + r) : 0.0;
            map.put(labels.get(i), new PRF1(tp, fp, fn, p, r, f1));
        }
        return map;
    }

    private static MicroMacro microMacroF1(int[][] M, List<NER.TYPE> labels, boolean ignoreNone) {
        int L = labels.size();
        List<Integer> keep = new ArrayList<>();
        for (int i = 0; i < L; i++) if (!(ignoreNone && isNone(labels.get(i)))) keep.add(i);

        long tp = 0, fp = 0, fn = 0;
        for (int i : keep) tp += M[i][i];
        for (int c : keep) for (int r = 0; r < L; r++) if (r != c) fp += M[r][c];
        for (int r : keep) for (int c = 0; c < L; c++) if (c != r) fn += M[r][c];

        double prec = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
        double rec = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
        double micro = (prec + rec) > 0 ? 2 * prec * rec / (prec + rec) : 0.0;

        double sumF1 = 0.0;
        int k = 0;
        Map<NER.TYPE, PRF1> pcs = perClassPRF1(M);
        for (int i : keep) {
            sumF1 += pcs.get(labels.get(i)).f1;
            k++;
        }
        double macro = k == 0 ? 0.0 : sumF1 / k;

        return new MicroMacro(micro, macro);
    }

    private static double balancedAccuracy(int[][] M, boolean ignoreNone) {
        var labels = defaultLabelSet();
        int L = labels.size();
        double sum = 0.0;
        int k = 0;
        for (int i = 0; i < L; i++) {
            if (ignoreNone && isNone(labels.get(i))) continue;
            int tp = M[i][i], fn = 0;
            for (int c = 0; c < L; c++) if (c != i) fn += M[i][c];
            double r = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            sum += r;
            k++;
        }
        return k == 0 ? 0.0 : sum / k;
    }

    /**
     * Computes Cohen's Kappa -> Agreement between two between Readers (i.e. here between two sets).
     * @param M Matrix containing results for each sentence x set
     * @return value
     */
    private static double cohensKappa(int[][] M) {
        long n = 0;
        int L = M.length;
        for (int[] ints : M) for (int j = 0; j < L; j++) n += ints[j];
        if (n == 0) return 0.0;
        long agree = 0;
        for (int i = 0; i < L; i++) agree += M[i][i];
        double p0 = (double) agree / n;

        long[] row = new long[L], col = new long[L];
        for (int i = 0; i < L; i++)
            for (int j = 0; j < L; j++) {
                row[i] += M[i][j];
                col[j] += M[i][j];
            }
        double pe = 0.0;
        for (int i = 0; i < L; i++) pe += ((double) row[i] * col[i]) / (n * (double) n);
        if (pe == 1.0) return 1.0;
        return (1 - pe) == 0 ? 0.0 : (p0 - pe) / (1 - pe);
    }

    private static WilsonCI wilson(double p, int n, double z) {
        if (n == 0) return new WilsonCI(0, 0);
        double denom = 1 + (z * z) / n;
        double center = (p + (z * z) / (2 * n)) / denom;
        double margin = (z * Math.sqrt((p * (1 - p)) / n + (z * z) / (4.0 * n * n))) / denom;
        double lo = Math.max(0.0, center - margin);
        double hi = Math.min(1.0, center + margin);
        return new WilsonCI(lo, hi);
    }

    private static double exactBinomialTwoSided(int n01, int n10) {
        int b = Math.min(n01, n10);
        int n = n01 + n10;
        if (n == 0) return 1.0;
        // p = 2 * sum_{k=0..b} C(n,k) / 2^n (log-space)
        double logTail = Double.NEGATIVE_INFINITY;
        for (int k = 0; k <= b; k++) {
            double term = logChoose(n, k) - n * Math.log(2);
            logTail = logSumExp(logTail, term);
        }
        return Math.min(1.0, 2 * Math.exp(logTail));
    }

    private static double logChoose(int n, int k) {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY;
        int kk = Math.min(k, n - k);
        double s = 0.0;
        for (int i = 1; i <= kk; i++) s += Math.log(n - kk + i) - Math.log(i);
        return s;
    }

    private static double logSumExp(double a, double b) {
        if (Double.isInfinite(a)) return b;
        if (Double.isInfinite(b)) return a;
        double m = Math.max(a, b);
        return m + Math.log(Math.exp(a - m) + Math.exp(b - m));
    }

    public record PRF1(int tp, int fp, int fn, double precision, double recall, double f1) {
    }

    public record MicroMacro(double microF1, double macroF1) {
    }

    public record WilsonCI(double lo, double hi) {
    }

    public record McNemar(int n01, int n10, double p) {
    }

    public record AdvancedReport(List<NER.TYPE> labels, int[][] confusion, double accuracy, double accLo, double accHi,
                                 Map<NER.TYPE, PRF1> perClass, MicroMacro microAll, MicroMacro microEnt, double balAll,
                                 double balEnt, double kappa, int n, int correct) {

        public static AdvancedReport empty() {
            return new AdvancedReport(List.of(), new int[0][0], 0, 0, 0, Map.of(),
                    new MicroMacro(0, 0), new MicroMacro(0, 0), 0, 0, 0, 0, 0);
        }

        @Override
        @NonNull
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("== Summary ==\n");
            sb.append(String.format(Locale.ROOT, "Accuracy:                %.4f  [%.4f, %.4f]  (%d/%d)\n", accuracy, accLo, accHi, correct, n));
            sb.append(String.format(Locale.ROOT, "Micro-F1 (all):          %.4f\n", microAll.microF1()));
            sb.append(String.format(Locale.ROOT, "Macro-F1 (all):          %.4f\n", microAll.macroF1()));
            sb.append(String.format(Locale.ROOT, "Micro-F1 (ents):         %.4f (excl. NONE)\n", microEnt.microF1()));
            sb.append(String.format(Locale.ROOT, "Macro-F1 (ents):         %.4f (excl. NONE)\n", microEnt.macroF1()));
            sb.append(String.format(Locale.ROOT, "Balanced Acc (all):      %.4f\n", balAll));
            sb.append(String.format(Locale.ROOT, "Balanced Acc (entities): %.4f\n", balEnt));
            sb.append(String.format(Locale.ROOT, "Cohen's kappa:           %.4f\n\n", kappa));

            sb.append("== Per-class metrics ==\n");
            for (var lab : labels) {
                var m = perClass.get(lab);
                sb.append(String.format(Locale.ROOT, "%-14s P=%.4f  R=%.4f  F1=%.4f  (tp=%d fp=%d fn=%d)\n",
                        lab.name(), m.precision(), m.recall(), m.f1(), m.tp(), m.fp(), m.fn()));
            }
            sb.append("\n== Confusion Matrix ==\ntrue\\pred  ");
            for (var lab : labels) sb.append(String.format("%-12s  ", lab.name()));
            sb.append("\n");
            for (int i = 0; i < labels.size(); i++) {
                sb.append(String.format("%-9s ", labels.get(i).name()));
                for (int j = 0; j < labels.size(); j++) sb.append(String.format("%12d  ", confusion[i][j]));
                sb.append("\n");
            }
            return sb.toString();
        }
    }
}
