package udem.taln;

import udem.taln.api.OllamaService;
import udem.taln.ner.Analyser;
import udem.taln.ner.NER;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    private static final HashMap<String, String> args_map = new HashMap<>();

    public static void main(String[] args) {
        for (String arg : args) {
            var cleanArg = cleanArg(arg);
            args_map.put(cleanArg.split("=")[0], cleanArg.split("=")[1]);
        }

        List<String> text = new ArrayList<>();

        if (args_map.get("text") != null && !args_map.get("text").isEmpty()) {
            text = Arrays.stream(args_map.get("text").split("\n")).toList();
        }

        if (text.isEmpty() && !args_map.get("file").isEmpty()) {
            List<String> finalText = new ArrayList<>(text);
            Thread thread = new Thread(() -> {
                try {
                    var in = new BufferedReader(new FileReader(args_map.get("file")));
                    String line;
                    while ((line = in.readLine()) != null) {
                        finalText.add(line);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            thread.start();
            try {
                thread.join(); // Wait for the thread to complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("File reading was interrupted", e);
            }
            System.out.println("File " + args_map.get("file") + " read successfully");
            text = finalText;
        }

        if (text.isEmpty()) {
            StringBuilder inputPipe = new StringBuilder();
            // The following segment was proposed by gemini while entering a google search
            // Reader thread
            Thread thread = new Thread(() -> {
                try {
                    PipedInputStream in = new PipedInputStream();
                    int data;
                    while ((data = in.read()) != -1) {
                        // System.out.print((char) data);
                        inputPipe.append((char) data);
                    }
                    in.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            thread.start();
            try {
                thread.join(); // Wait for the thread to complete
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("File reading was interrupted", e);
            }
            // end of the said segment

            text = Arrays.stream(inputPipe.toString().split("\n")).toList();

            if (text.isEmpty()) {
                System.err.println("No text provided, please try again.");
                return;
            }
        }

        if (args_map.get("method").equals("spacy")) {
            Analyser analyser = new Analyser();
            var processedText = analyser.format(text, false);
            List<NER.PSentence> executed = new ArrayList<>();
            switch (args_map.get("model")) {
                case "en_core_web_lg": {
                    executed = process(NER.MODE.LG, processedText, analyser);
                    break;
                }
                case "en_core_web_md": {
                    executed = process(NER.MODE.MD, processedText, analyser);
                    break;
                }
                case "en_core_web_sm": {
                    executed = process(NER.MODE.SM, processedText, analyser);
                    break;
                }
                case "en_core_web_trf": {
                    executed = process(NER.MODE.TRF, processedText, analyser);
                    break;
                }
            }
            Map<String, String> toFile = new HashMap<>();
            if (executed != null) {
                for (var result : executed) {
                    toFile.put(processedText.get(result.id()).sentence, result.types().stream().map(Enum::toString).collect(Collectors.joining(",")));
                }
            }
            writeOutput("en-" + args_map.get("file").split("\\.")[1] + "-spacy-" + args_map.get("model").split("_")[3] + ".out", toFile);
        } else if (args_map.get("method").equals("ollama")) {
            Analyser analyser = new Analyser();
            var processedText = analyser.format(text, true);

            var ollama = new OllamaService(args_map.get("model"));
            long before = System.nanoTime();
            var executed = ollama.execute(processedText);
            long after = System.nanoTime();
//            System.out.println(executed);
            System.out.println("Success (%) : " + analyser.analyse(executed));
            System.out.println("Time (ms) : " + (after - before) / 1000000.0);

            Analyser.Metrics m = analyser.analyseF1(executed);
            double precision = m.precision();
            double recall = m.recall();
            double f1 = m.f1();
            System.out.println("Precision: " + precision + ", Recall: " + recall + ", F1: " + f1);

            Map<String, String> toFile = new HashMap<>();
            if (executed != null) {
                for (var result : executed) {
                    toFile.put(processedText.get(result.id()).sentence, result.types().stream().map(Enum::toString).collect(Collectors.joining(",")));
                }
            }
            writeOutput("en-" + args_map.get("file").split("\\.")[1] + "ollama.out", toFile);
        }
    }

    private static void writeOutput(String fileName, Map<String, String> output) {
        try {
            System.out.println("Writing output to file " + fileName);
            File file = new File(fileName);
            if (!file.exists()) {
                if (!file.createNewFile()) {
                    System.err.println("Could not create output file.");
                    return;
                }
                System.out.println("Output file created.");
            }
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
                for (Map.Entry<String, String> entry : output.entrySet()) {
                    bw.write(entry.getValue() + ": " + entry.getKey() + "\n");
                }
                bw.flush();
                System.out.println("Output written successfully.");
            }
            System.out.println("Successfully wrote output to file.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String cleanArg(String arg) {
        StringBuilder s = new StringBuilder();
        for (var c : arg.toCharArray()) {
            if (c == '\"' || c == '-') continue;
            s.append(c);
        }
        return s.toString();
    }

    private static List<NER.PSentence> process(NER.MODE mode,  Map<Integer, Analyser.Pair> text, Analyser analyser) {
        long before = System.nanoTime();
        var executed = NER.execute(mode, text);
        long after = System.nanoTime();
        if (executed != null) {
//            System.out.println(executed);
            System.out.println("Success (%) : " + (analyser.analyse(executed) * 100));
            System.out.println("Time (ms) : " + (after - before) / 1000000.0);

            Analyser.Metrics m = analyser.analyseF1(executed);
            double precision = m.precision();
            double recall = m.recall();
            double f1 = m.f1();
            System.out.println("Precision: " + precision + ", Recall: " + recall + ", F1: " + f1);
        }
        return executed;
    }
}