package udem.taln.wrapper;

public interface WrapperInterface {
    /**
     * Returns a list in form of a JSON string.
     *
     * @param sentence the sentence to process.
     * @return the response json containing labels.
     */
    String processLG(String sentence);
    String processLG(String sentence, String target);
    String processMD(String sentence);
    String processMD(String sentence, String target);
    String processSM(String sentence);
    String processSM(String sentence, String target);
//    String processTRF(String sentence);
}
