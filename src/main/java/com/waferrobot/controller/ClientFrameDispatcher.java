package com.waferrobot.controller;

import com.waferrobot.protocol.RobotFrame;
import com.waferrobot.registry.StatusCodeRegistry;

/**
 * Routes incoming frames on the Controller side by message type.
 * Prints a human-readable message to the console for each frame received.
 */
public class ClientFrameDispatcher {

    private final StatusCodeRegistry statusRegistry;

    public ClientFrameDispatcher(StatusCodeRegistry statusRegistry) {
        this.statusRegistry = statusRegistry;
    }

    /**
     * Dispatches an incoming frame from the Simulator.
     *
     * @param frame the parsed frame to route
     */
    public void dispatch(RobotFrame frame) {
        if (!frame.isValid()) {
            System.out.println("[Controller] WARNING: Received invalid frame — dropped.");
            return;
        }

        switch (frame.getMessageType()) {
            case "ACK"  -> handleAck(frame);
            case "NAK"  -> handleNak(frame);
            case "EVT"  -> handleEvt(frame);
            case "STAT" -> handleStat(frame);
            default     -> System.out.println("[Controller] WARNING: Unknown message type '"
                    + frame.getMessageType() + "' — dropped.");
        }
    }

    private void handleAck(RobotFrame frame) {
        System.out.println("[Controller] ACK received — Command #" + frame.getSequenceId()
                + " accepted by Simulator. Processing started.");
    }

    private void handleNak(RobotFrame frame) {
        String description = resolveCode(frame.getPayload());
        System.out.println("[Controller] NAK received — Command #" + frame.getSequenceId()
                + " REJECTED. Reason: " + frame.getPayload() + " (" + description + ")");
    }

    private void handleEvt(RobotFrame frame) {
        String description = resolveCode(frame.getPayload());
        System.out.println("[Controller] EVT received — Command #" + frame.getSequenceId()
                + " COMPLETE. Status: " + frame.getPayload() + " (" + description + ")");
    }

    private void handleStat(RobotFrame frame) {
        String description = resolveCode(frame.getPayload());
        System.out.println("[Controller] STAT received — Simulator status: "
                + frame.getPayload() + " (" + description + ")");
    }

    private String resolveCode(String payload) {
        if (payload == null || payload.isBlank()) return "N/A";
        try {
            int code = Integer.parseInt(payload.trim());
            return statusRegistry.getDescription(code);
        } catch (NumberFormatException e) {
            return payload;
        }
    }
}
