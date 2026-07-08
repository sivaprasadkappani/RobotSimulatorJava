package com.waferrobot.simulator;

import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameBuilder;
import com.waferrobot.registry.ParsedCommand;
import com.waferrobot.registry.StatusCodeRegistry;

import java.io.IOException;
import java.util.Map;

/**
 * Worker thread that simulates command execution on the Simulator side.
 *
 * Spawned by SimulatorFrameDispatcher after sending ACK for a valid CMD frame.
 * Sleeps for the processing delay, then sends an EVT frame with the
 * appropriate completion code on the event connection.
 *
 * Processing delay is hardcoded at 4 seconds.
 * TODO: Replace with distance/speed calculation from robot.cfg station positions.
 */
public class CommandWorker implements Runnable {

    /** Hardcoded processing delay in milliseconds. */
    private static final int PROCESSING_DELAY_MS = 4000;

    /** Maps action names to their completion code descriptions. */
    private static final Map<String, String> COMPLETION_MAP = Map.of(
            "PICK",   "PICK_COMPLETE",
            "PLACE",  "PLACE_COMPLETE",
            "MOVE",   "MOVE_COMPLETE",
            "HOME",   "HOME_COMPLETE",
            "CONFIG", "CONFIG_COMPLETE"
    );

    private final ParsedCommand     parsedCommand;
    private final int               sequenceId;
    private final SocketConnection  eventConnection;
    private final StatusCodeRegistry statusRegistry;
    private final FrameBuilder      frameBuilder;

    /**
     * @param parsedCommand   the validated command being executed
     * @param sequenceId      sequence ID to correlate the EVT response
     * @param eventConnection event socket connection (port 5001)
     * @param statusRegistry  for resolving completion codes
     */
    public CommandWorker(ParsedCommand parsedCommand, int sequenceId,
                         SocketConnection eventConnection,
                         StatusCodeRegistry statusRegistry) {
        this.parsedCommand   = parsedCommand;
        this.sequenceId      = sequenceId;
        this.eventConnection = eventConnection;
        this.statusRegistry  = statusRegistry;
        this.frameBuilder    = new FrameBuilder();
    }

    @Override
    public void run() {
        String action = parsedCommand.getAction();
        System.out.println("[CommandWorker] Processing command #" + sequenceId
                + " action=" + action + " — started (delay=" + PROCESSING_DELAY_MS + "ms)");
        try {
            Thread.sleep(PROCESSING_DELAY_MS);

            // Determine completion code
            String completionDesc = COMPLETION_MAP.getOrDefault(action, "PROCESSING_COMPLETE");
            int    completionCode = statusRegistry.getCode(completionDesc);
            if (completionCode < 0) completionCode = 205; // fallback

            System.out.println("[CommandWorker] Command #" + sequenceId
                    + " complete — sending EVT " + completionCode
                    + " (" + completionDesc + ")");

            String evtFrame = frameBuilder.build("EVT", sequenceId, String.valueOf(completionCode));
            eventConnection.sendFrame(evtFrame);

        } catch (InterruptedException e) {
            System.out.println("[CommandWorker] Command #" + sequenceId + " interrupted.");
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.out.println("[CommandWorker] ERROR sending EVT: " + e.getMessage());
        }
    }
}
