package com.waferrobot.registry;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a command payload after parsing.
 *
 * Example payload: "PICK FROM=LPA1 ARM=LOWER"
 *   action     = "PICK"
 *   parameters = { "FROM" -> "LPA1", "ARM" -> "LOWER" }
 */
public class ParsedCommand {

    private final String action;
    private final Map<String, String> parameters;

    public ParsedCommand(String action, Map<String, String> parameters) {
        this.action     = action;
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    /** Command action, e.g. "PICK", "MOVE", "HOME". */
    public String getAction() { return action; }

    /** Key-value parameter map, e.g. { "FROM" -> "LPA1", "ARM" -> "LOWER" }. */
    public Map<String, String> getParameters() { return parameters; }

    /** Returns the value for a parameter key, or null if not present. */
    public String getParameter(String key) { return parameters.get(key); }

    /** Returns true if the given parameter key is present. */
    public boolean hasParameter(String key) { return parameters.containsKey(key); }

    @Override
    public String toString() {
        return "ParsedCommand[action=" + action + ", params=" + parameters + "]";
    }
}
