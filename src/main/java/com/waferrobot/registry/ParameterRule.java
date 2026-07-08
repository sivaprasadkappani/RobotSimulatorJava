package com.waferrobot.registry;

/**
 * Represents a single parameter rule loaded from the [COMMANDS] section of robot.cfg.
 *
 * Example cfg entry:  FROM:ValidStations
 *   name         = "FROM"
 *   hardwareLink = "ValidStations"
 *
 * Example cfg entry:  SLOTS  (no hardware link)
 *   name         = "SLOTS"
 *   hardwareLink = ""
 */
public class ParameterRule {

    private final String name;
    private final String hardwareLink;

    public ParameterRule(String name, String hardwareLink) {
        this.name         = name;
        this.hardwareLink = hardwareLink;
    }

    /** Parameter name, e.g. "FROM", "ARM", "SLOTS". */
    public String getName() { return name; }

    /**
     * Linked constraint list name from [HARDWARE] or [NUMERIC_LIMITS].
     * Empty string if this parameter has no constraint.
     */
    public String getHardwareLink() { return hardwareLink; }

    public boolean hasHardwareLink() { return !hardwareLink.isEmpty(); }

    @Override
    public String toString() {
        return name + (hardwareLink.isEmpty() ? "" : ":" + hardwareLink);
    }
}
