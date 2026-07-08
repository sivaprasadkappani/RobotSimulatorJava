package com.waferrobot.registry;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Loads command and hardware validation rules from robot.cfg and runs
 * a 4-step validation pipeline against parsed commands.
 *
 * Sections parsed from robot.cfg:
 *   [COMMANDS]       — command actions and parameter rules
 *   [HARDWARE]       — allowed string values for hardware-linked parameters
 *   [NUMERIC_LIMITS] — min/max ranges for numeric parameters
 *   [STATION_ARMS]   — allowed arms per station
 */
public class CommandRegistry {

    private final Map<String, CommandSignature> commands      = new HashMap<>();
    private final Map<String, List<String>>     hardwareLists = new HashMap<>();
    private final Map<String, int[]>            numericLimits = new HashMap<>();
    private final Map<String, List<String>>     stationArms   = new HashMap<>();

    // Last validation failure reason — available after any validate* call
    private String lastFailureReason = "";
    private int    lastFailureCode   = 100;

    /**
     * Loads all sections from robot.cfg.
     *
     * @param filename path to robot.cfg
     * @return true if loaded successfully, false if the file cannot be opened
     */
    public boolean loadFromCfg(String filename) {
        commands.clear();
        hardwareLists.clear();
        numericLimits.clear();
        stationArms.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String currentSection = "";
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1).toUpperCase();
                    continue;
                }

                switch (currentSection) {
                    case "COMMANDS"       -> parseCommandLine(line);
                    case "HARDWARE"       -> parseKeyValueList(line, hardwareLists);
                    case "NUMERIC_LIMITS" -> parseNumericLimit(line);
                    case "STATION_ARMS"   -> parseKeyValueList(line, stationArms);
                }
            }
            System.out.println("[CommandRegistry] Loaded " + commands.size()
                    + " commands from " + filename);
            return true;
        } catch (IOException e) {
            System.out.println("[CommandRegistry] ERROR: Cannot open file: " + filename
                    + " — " + e.getMessage());
            return false;
        }
    }

    // ── Config parsing helpers ────────────────────────────────────────────────

    private void parseCommandLine(String line) {
        // Format: ACTION=RequiredParams|OptionalParams
        int eqIdx = line.indexOf('=');
        if (eqIdx < 0) return;

        String action    = line.substring(0, eqIdx).trim().toUpperCase();
        String remainder = line.substring(eqIdx + 1);

        String[] halves   = remainder.split("\\|", 2);
        String   reqPart  = halves[0];
        String   optPart  = halves.length > 1 ? halves[1] : "";

        List<ParameterRule> required = parseRuleList(reqPart);
        List<ParameterRule> optional = parseRuleList(optPart);
        commands.put(action, new CommandSignature(required, optional));
    }

    private List<ParameterRule> parseRuleList(String part) {
        List<ParameterRule> rules = new ArrayList<>();
        if (part == null || part.isBlank()) return rules;
        for (String raw : part.split(";")) {
            raw = raw.trim();
            if (raw.isEmpty()) continue;
            int colonIdx = raw.indexOf(':');
            if (colonIdx > 0) {
                rules.add(new ParameterRule(
                        raw.substring(0, colonIdx).trim().toUpperCase(),
                        raw.substring(colonIdx + 1).trim()));
            } else {
                rules.add(new ParameterRule(raw.toUpperCase(), ""));
            }
        }
        return rules;
    }

    private void parseKeyValueList(String line, Map<String, List<String>> target) {
        int eqIdx = line.indexOf('=');
        if (eqIdx < 0) return;
        String key    = line.substring(0, eqIdx).trim().toUpperCase();
        String values = line.substring(eqIdx + 1).trim();
        List<String> list = new ArrayList<>();
        for (String v : values.split(",")) {
            list.add(v.trim().toUpperCase());
        }
        target.put(key, list);
    }

    private void parseNumericLimit(String line) {
        int eqIdx = line.indexOf('=');
        if (eqIdx < 0) return;
        String key    = line.substring(0, eqIdx).trim();
        String[] minMax = line.substring(eqIdx + 1).trim().split(",", 2);
        if (minMax.length == 2) {
            try {
                int min = Integer.parseInt(minMax[0].trim());
                int max = Integer.parseInt(minMax[1].trim());
                numericLimits.put(key, new int[]{min, max});
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── Validation pipeline ───────────────────────────────────────────────────

    /**
     * Step 1: Validates command grammar.
     * Checks: unknown action, missing required parameter, undeclared parameter.
     *
     * @param cmd parsed command to validate
     * @return true if syntax is valid
     */
    public boolean validateSyntax(ParsedCommand cmd) {
        CommandSignature sig = commands.get(cmd.getAction());
        if (sig == null) {
            lastFailureReason = "Unknown action: " + cmd.getAction();
            lastFailureCode   = 102;
            return false;
        }

        // Check all required parameters are present
        for (ParameterRule rule : sig.getRequired()) {
            if (!cmd.hasParameter(rule.getName())) {
                lastFailureReason = "Missing required parameter: " + rule.getName();
                lastFailureCode   = 104;
                return false;
            }
        }

        // Check no undeclared parameters are supplied
        Set<String> declared = new HashSet<>();
        sig.getRequired().forEach(r -> declared.add(r.getName()));
        sig.getOptional().forEach(r -> declared.add(r.getName()));

        for (String supplied : cmd.getParameters().keySet()) {
            if (!declared.contains(supplied)) {
                lastFailureReason = "Illegal parameter: " + supplied;
                lastFailureCode   = 103;
                return false;
            }
        }

        return true;
    }

    /**
     * Step 2: Validates hardware-linked parameter values against [HARDWARE] lists.
     *
     * @param cmd parsed command to validate
     * @return true if all hardware-linked parameters have allowed values
     */
    public boolean validateHardwareConstraints(ParsedCommand cmd) {
        CommandSignature sig = commands.get(cmd.getAction());
        if (sig == null) return false;

        List<ParameterRule> allRules = new ArrayList<>(sig.getRequired());
        allRules.addAll(sig.getOptional());

        for (ParameterRule rule : allRules) {
            if (!rule.hasHardwareLink()) continue;
            String link  = rule.getHardwareLink();
            if (!hardwareLists.containsKey(link)) continue; // numeric list — handled separately

            String value = cmd.getParameter(rule.getName());
            if (value == null) continue; // optional param not supplied

            List<String> allowed = hardwareLists.get(link);
            if (!allowed.contains(value.toUpperCase())) {
                lastFailureReason = "Invalid value '" + value + "' for " + rule.getName()
                        + ". Allowed: " + allowed;
                lastFailureCode   = 105;
                return false;
            }
        }
        return true;
    }

    /**
     * Step 3: Validates numeric parameter values against [NUMERIC_LIMITS] ranges.
     *
     * @param cmd parsed command to validate
     * @return true if all numeric parameters are within their min/max range
     */
    public boolean validateNumericLimits(ParsedCommand cmd) {
        CommandSignature sig = commands.get(cmd.getAction());
        if (sig == null) return false;

        List<ParameterRule> allRules = new ArrayList<>(sig.getRequired());
        allRules.addAll(sig.getOptional());

        for (ParameterRule rule : allRules) {
            if (!rule.hasHardwareLink()) continue;
            String link = rule.getHardwareLink();
            if (!numericLimits.containsKey(link)) continue; // not a numeric limit

            String value = cmd.getParameter(rule.getName());
            if (value == null) continue;

            try {
                double numVal = Double.parseDouble(value);
                int[] range   = numericLimits.get(link);
                if (numVal < range[0] || numVal > range[1]) {
                    lastFailureReason = "Value " + value + " for " + rule.getName()
                            + " is out of range [" + range[0] + ", " + range[1] + "]";
                    lastFailureCode   = 115;
                    return false;
                }
            } catch (NumberFormatException e) {
                lastFailureReason = "Parameter " + rule.getName()
                        + " expects a numeric value, got: " + value;
                lastFailureCode   = 115;
                return false;
            }
        }
        return true;
    }

    /**
     * Step 4: Validates ARM compatibility with the FROM or TO station per [STATION_ARMS].
     *
     * @param cmd parsed command to validate
     * @return true if the ARM is allowed at the specified station
     */
    public boolean validateStationArmCompatibility(ParsedCommand cmd) {
        String arm     = cmd.getParameter("ARM");
        String station = cmd.getParameter("FROM");
        if (station == null) station = cmd.getParameter("TO");

        if (arm == null || station == null) return true; // not applicable

        List<String> allowedArms = stationArms.get(station.toUpperCase());
        if (allowedArms == null) return true; // station not in STATION_ARMS — no constraint

        if (!allowedArms.contains(arm.toUpperCase())) {
            lastFailureReason = "ARM=" + arm + " is not allowed at station "
                    + station + ". Allowed arms: " + allowedArms;
            lastFailureCode   = 116;
            return false;
        }
        return true;
    }

    /**
     * Runs the full 4-step validation pipeline.
     *
     * @param cmd parsed command to validate
     * @return true if all steps pass
     */
    public boolean validate(ParsedCommand cmd) {
        return validateSyntax(cmd)
                && validateHardwareConstraints(cmd)
                && validateNumericLimits(cmd)
                && validateStationArmCompatibility(cmd);
    }

    /** Returns the human-readable reason for the last validation failure. */
    public String getLastFailureReason() { return lastFailureReason; }

    /** Returns the error code corresponding to the last validation failure. */
    public int getLastFailureCode() { return lastFailureCode; }
}
