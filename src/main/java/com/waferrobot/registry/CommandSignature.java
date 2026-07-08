package com.waferrobot.registry;

import java.util.List;

/**
 * Holds the required and optional parameter rules for one command action.
 * Loaded from the [COMMANDS] section of robot.cfg.
 *
 * Format in robot.cfg:
 *   ACTION=RequiredParam1:HardwareList;RequiredParam2|OptionalParam1;OptionalParam2
 */
public class CommandSignature {

    private final List<ParameterRule> required;
    private final List<ParameterRule> optional;

    public CommandSignature(List<ParameterRule> required, List<ParameterRule> optional) {
        this.required = List.copyOf(required);
        this.optional = List.copyOf(optional);
    }

    /** Parameters that must be present in a valid command. */
    public List<ParameterRule> getRequired() { return required; }

    /** Parameters that may optionally be present. */
    public List<ParameterRule> getOptional() { return optional; }
}
