package udem.taln;

import udem.taln.api.MistralService;
import udem.taln.api.OllamaService;
import udem.taln.ner.Analyser;
import udem.taln.ner.NER;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    // contains the args and their values
    private static final HashMap<String, String> args_map = new HashMap<>();

    public static void main(String[] args) {
        for (String arg : args) {
            var cleanArg = cleanArg(arg);
            System.out.println("Arg: " + cleanArg);
            args_map.put(cleanArg.split("=")[0], cleanArg.split("=").length == 1 ? "true" : cleanArg.split("=")[1]);
        }

        List<String> text = getText();

        // Managing Spacy method
        if ((args_map.get("compare") == null || !args_map.get("compare").equals("true")) && args_map.get("method").equals("spacy")) {
            System.out.println("Executing Spacy...");
            Analyser analyser = new Analyser();
            var processedText = analyser.format(text, false);
            List<NER.PSentence> executed = getExecutedSpacy(processedText, analyser, true);
            var toFile = formatForFile(processedText, executed);
            writeOutput("en-" + args_map.get("file").split("\\.")[1] + "-spacy-2methode-" + args_map.get("model").split("_")[3] + ".out", toFile);
        }

        // Managing Ollama method
        else if ((args_map.get("compare") == null || !args_map.get("compare").equals("true")) && args_map.get("method").equals("ollama")) {
            runOllama(text, true, true);
        }

        // Managing Mistral AI method
        // https://docs.mistral.ai/getting-started/models/models_overview
        else if (args_map.get("method").equals("mistral")) {
            Analyser analyser = new Analyser();
            List<String> toProcess = text.subList(0, 99);
            var processedText = analyser.format(toProcess, true);

            var mistral = new MistralService(args_map.get("model"));
            long before = System.nanoTime();
            var executed = mistral.executeBatch(processedText);
            long after = System.nanoTime();
            System.out.println(executed);
            System.out.println("Time (ms) : " + (after - before) / 1000000.0);

            analyse(executed, analyser);

            Map<String, String> toFile = formatForFile(processedText, executed);
            writeOutput("en-" + args_map.get("file").split("\\.")[1] + "mistral-" + args_map.get("model") + ".out", toFile);
        }

        // Comparing between one model and LG Spacy on 1 method
        else if (args_map.get("compare") != null) {
            System.out.println("Executing Comparing between NRB and WTS on Spacy...");
            Analyser analyser = new Analyser();
            var subText = text.subList(0, 100);
            var fText = analyser.format(subText, false);
            List<NER.PSentence> executedOther = List.of();

            if (args_map.get("method").equals("spacy")) executedOther = getExecutedSpacy(fText, analyser, false);
            else if (args_map.get("method").equals("ollama")) executedOther = runOllama(subText, false, false);

            args_map.replace("model", "en_core_web_lg");
            List<NER.PSentence> executedLG = getExecutedSpacy(fText, analyser, false);

            System.out.println(analyser.mcnemar(executedLG, executedOther).toString());
        }
    }

    private static List<NER.PSentence> runOllama(List<String> text, boolean analyse, boolean printToFile) {
        Analyser analyser = new Analyser();
        var processedText = analyser.format(text, true);
        System.out.println("Ollama size to process : "+processedText.size());

        var ollama = new OllamaService(args_map.get("model"));
        long before = System.nanoTime();
        var executed = ollama.execute(processedText);
        long after = System.nanoTime();
//            System.out.println(executed);
        System.out.println("Time (ms) : " + (after - before) / 1000000.0);

        if (analyse) analyse(executed, analyser);

        if (printToFile){
            Map<String, String> toFile = formatForFile(processedText, executed);
            writeOutput("en-" + args_map.get("file").split("\\.")[1] + "ollama" + args_map.get("model") + ".out", toFile);
        }
        return executed;
    }

    private static Map<String, String> formatForFile(Map<Integer, Analyser.Pair> processedText, List<NER.PSentence> executed) {
        Map<String, String> toFile = new HashMap<>();
        if (executed != null) {
            for (var result : executed) {
                toFile.put(processedText.get(result.id()).sentence, result.types().stream().map(Enum::toString).collect(Collectors.joining(",")));
            }
        }
        return toFile;
    }

    /**
     * Utility to write the output of execution to a file.
     *
     * @param fileName name of the file to write to
     * @param output   what we want to write to the file
     */
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

    /**
     * Utility to clean the arguments (especially double dash).
     */
    private static String cleanArg(String arg) {
        return arg.substring(arg.indexOf("--") + 2);
    }

    private static List<NER.PSentence> process(NER.MODE mode, Map<Integer, Analyser.Pair> text, Analyser analyser, boolean analyse) {
        long before = System.nanoTime();
        var executed = NER.execute(mode, text);
        long after = System.nanoTime();
        if (executed != null) {
//            System.out.println(executed);
            System.out.println("Time (ms) : " + (after - before) / 1000000.0);

            if (analyse) analyse(executed, analyser);
        }
        return executed;
    }

    /**
     * Gets the input test according to how it was provided to the main func.
     */
    private static List<String> getText() {
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
                throw new RuntimeException("No text provided");
            }
        }
        return text;
    }

    /**
     * Executes Spacy on the given text, for all spacy models (provided in args).
     *
     * @param text     text to process
     * @param analyser analyser to use for analyzing the results
     * @return processed text with NER annotations
     */
    public static List<NER.PSentence> getExecutedSpacy(Map<Integer, Analyser.Pair> text, Analyser analyser, boolean analyse) {
        List<NER.PSentence> executed = null;
        switch (args_map.get("model")) {
            case "en_core_web_lg": {
                executed = process(NER.MODE.LG, text, analyser, analyse);
                break;
            }
            case "en_core_web_md": {
                executed = process(NER.MODE.MD, text, analyser, analyse);
                break;
            }
            case "en_core_web_sm": {
                executed = process(NER.MODE.SM, text, analyser, analyse);
                break;
            }
            case "en_core_web_trf": {
                executed = process(NER.MODE.TRF, text, analyser, analyse);
                break;
            }
        }
        return executed;
    }

    /**
     * Small utility to print some of the stats of the execution.
     */
    private static void analyse(List<NER.PSentence> executed, Analyser analyser) {
        System.out.println("Success (%) : " + (analyser.analyse(executed) * 100));
        Analyser.Metrics m = analyser.analyseF1(executed);
        double precision = m.precision();
        double recall = m.recall();
        double f1 = m.f1();
        System.out.println("Precision: " + precision + ", Recall: " + recall + ", F1: " + f1);
        System.out.println(analyser.analyseAdvanced(executed));
    }
}