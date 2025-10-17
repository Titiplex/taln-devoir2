package udem.taln.api.utils;

import udem.taln.ner.NER;

import java.util.List;

public abstract class ResponseHelper {
    public static String extractBetweenDoubleBrackets(String s) {
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

    public static List<NER.TYPE> getTypes(String response) {
        String typeToken = ResponseHelper.extractBetweenDoubleBrackets(response);
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
    }
}
