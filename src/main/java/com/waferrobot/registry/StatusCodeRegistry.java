package com.waferrobot.registry;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads status and error codes from error_codes.csv and provides
 * forward and reverse lookups.
 *
 * CSV format (no header):
 *   100,INVALID_COMMAND
 *   200,PICK_COMPLETE
 *
 * Codes 100-120 are error codes (used in NAK payloads).
 * Codes 200-205 are completion codes (used in EVT payloads).
 */
public class StatusCodeRegistry {

    private final Map<Integer, String> codeToDescription = new HashMap<>();
    private final Map<String, Integer> descriptionToCode = new HashMap<>();

    /**
     * Loads status codes from a CSV file.
     *
     * @param filename path to error_codes.csv
     * @return true if loaded successfully, false if the file cannot be opened
     */
    public boolean loadFromCsv(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",", 2);
                if (parts.length != 2) {
                    System.out.println("[StatusCodeRegistry] Skipping malformed line: " + line);
                    continue;
                }

                try {
                    int    code        = Integer.parseInt(parts[0].trim());
                    String description = parts[1].trim();
                    codeToDescription.put(code, description);
                    descriptionToCode.put(description, code);
                } catch (NumberFormatException e) {
                    System.out.println("[StatusCodeRegistry] Skipping non-integer code: " + line);
                }
            }
            System.out.println("[StatusCodeRegistry] Loaded " + codeToDescription.size() + " codes from " + filename);
            return true;
        } catch (IOException e) {
            System.out.println("[StatusCodeRegistry] ERROR: Cannot open file: " + filename + " — " + e.getMessage());
            return false;
        }
    }

    /**
     * Forward lookup: returns the description for a given code.
     *
     * @param code status or error code integer
     * @return description string, or "UNKNOWN_CODE_<code>" if not found
     */
    public String getDescription(int code) {
        return codeToDescription.getOrDefault(code, "UNKNOWN_CODE_" + code);
    }

    /**
     * Reverse lookup: returns the integer code for a given description.
     *
     * @param description status or error description string
     * @return code integer, or -1 if not found
     */
    public int getCode(String description) {
        return descriptionToCode.getOrDefault(description, -1);
    }

    /** Returns true if the code is a completion code (200-299). */
    public boolean isCompletionCode(int code) {
        return code >= 200 && code < 300;
    }

    /** Returns true if the code is an error code (100-199). */
    public boolean isErrorCode(int code) {
        return code >= 100 && code < 200;
    }
}
