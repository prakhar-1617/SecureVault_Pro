package com.securevault.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A lightweight, dependency-free utility to parse flat JSON strings into maps
 * and serialize maps/objects into JSON.
 */
public final class JsonUtil {

    private JsonUtil() {}

    /**
     * Parses a flat JSON string into a Map of key-value pairs.
     * Handles string, numeric, boolean, and null values.
     *
     * @param json the JSON string
     * @return map of key-values
     */
    public static Map<String, String> parse(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }

        // Regex matches key-value pairs: "key" : "value" or "key" : 123/true/false/null
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d\\.\\-\\w]+|true|false|null))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            String valString = matcher.group(2);
            String valLiteral = matcher.group(3);
            String value = (valString != null) ? valString : valLiteral;
            map.put(key, value);
        }
        return map;
    }

    /**
     * Converts a Map into a flat JSON string.
     *
     * @param map the map to serialize
     * @return JSON string representation
     */
    public static String toJson(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(escape(val.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Helper to escape special JSON characters.
     */
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
