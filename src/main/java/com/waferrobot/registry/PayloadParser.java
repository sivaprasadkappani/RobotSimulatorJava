package com.waferrobot.registry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses a raw command payload string into a ParsedCommand.
 *
 * Parsing rules:
 *   - First space-delimited token becomes the action.
 *   - Remaining tokens in KEY=VALUE format become parameters.
 *   - Tokens without '=' are ignored.
 *
 * Example:
 *   Input:  "PICK FROM=LPA1 ARM=LOWER SLOTS=2"
 *   Output: action="PICK", params={ FROM->LPA1, ARM->LOWER, SLOTS->2 }
 */
public class PayloadParser {

    /**
     * Parses a payload string into a ParsedCommand.
     *
     * @param payload raw payload from the CMD frame
     * @return ParsedCommand with action and parameters
     */
    public ParsedCommand parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return new ParsedCommand("", Map.of());
        }

        String[] tokens = payload.trim().split("\\s+");
        String action = tokens[0].toUpperCase();

        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            int eqIndex = token.indexOf('=');
            if (eqIndex > 0) {
                String key   = token.substring(0, eqIndex).toUpperCase();
                String value = token.substring(eqIndex + 1).toUpperCase();
                parameters.put(key, value);
            }
            // tokens without '=' are ignored as per spec
        }

        return new ParsedCommand(action, parameters);
    }
}
