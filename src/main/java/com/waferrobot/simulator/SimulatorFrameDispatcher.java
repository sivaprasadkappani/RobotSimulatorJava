package com.waferrobot.simulator;

import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameBuilder;
import com.waferrobot.protocol.RobotFrame;
import com.waferrobot.registry.CommandRegistry;
import com.waferrobot.registry.ParsedCommand;
import com.waferrobot.registry.PayloadParser;
import com.waferrobot.registry.StatusCodeRegistry;

import java.io.IOException;

/**
 * Routes incoming frames on the Simulator side.
 *
 * For CMD frames: runs the 4-step validation pipeline, sends ACK or NAK,
 * and spawns a CommandWorker thread for valid commands.
 *
 * For STAT frames: acknowledges receipt (outgoing STAT handled by StatusBroadcaster).
 * Other types: prints an illegal message type warning.
 */
public class SimulatorFrameDispatcher {

    private final CommandRegistry    commandRegistry;
    private final StatusCodeRegistry statusRegistry;
    private final SocketConnection   commandConnection;
    private final SocketConnection   eventConnection;
    private final FrameBuilder       frameBuilder;
    private final PayloadParser      payloadParser;

    /**
     * @param commandRegistry   for validating CMD payloads
     * @param statusRegistry    for resolving error and completion codes
     * @param commandConnection command socket (port 5000) — ACK/NAK sent here
     * @param eventConnection   event socket (port 5001) — EVT sent here by workers
     */
    public SimulatorFrameDispatcher(CommandRegistry commandRegistry,
                                    StatusCodeRegistry statusRegistry,
                                    SocketConnection commandConnection,
                                    SocketConnection eventConnection) {
        this.commandRegistry   = commandRegistry;
        this.statusRegistry    = statusRegistry;
        this.commandConnection = commandConnection;
        this.eventConnection   = eventConnection;
        this.frameBuilder      = new FrameBuilder();
        this.payloadParser     = new PayloadParser();
    }

    /**
     * Dispatches a received frame.
     *
     * @param frame the parsed frame from the Controller
     */
    public void dispatch(RobotFrame frame) {
        if (!frame.isValid()) {
            System.out.println("[Simulator] WARNING: Received invalid frame — dropped.");
            return;
        }

        System.out.println("[Simulator] Received " + frame.getMessageType()
                + " #" + frame.getSequenceId()
                + (frame.getPayload().isEmpty() ? "" : " | " + frame.getPayload()));

        switch (frame.getMessageType()) {
            case "CMD"  -> handleCmd(frame);
            case "STAT" -> System.out.println("[Simulator] STAT request received — StatusBroadcaster handles outgoing STAT.");
            default     -> System.out.println("[Simulator] WARNING: Illegal message type '"
                    + frame.getMessageType() + "' received on command connection.");
        }
    }

    private void handleCmd(RobotFrame frame) {
        ParsedCommand cmd = payloadParser.parse(frame.getPayload());
        System.out.println("[Simulator] Parsed command: action=" + cmd.getAction()
                + " params=" + cmd.getParameters());

        if (commandRegistry.validate(cmd)) {
            sendAck(frame.getSequenceId());
            spawnWorker(cmd, frame.getSequenceId());
        } else {
            int    errorCode   = commandRegistry.getLastFailureCode();
            String errorReason = commandRegistry.getLastFailureReason();
            System.out.println("[Simulator] Validation FAILED — code=" + errorCode
                    + " reason: " + errorReason);
            sendNak(frame.getSequenceId(), errorCode);
        }
    }

    private void sendAck(int sequenceId) {
        try {
            String ackFrame = frameBuilder.build("ACK", sequenceId);
            commandConnection.sendFrame(ackFrame);
            System.out.println("[Simulator] ACK #" + sequenceId + " sent.");
        } catch (IOException e) {
            System.out.println("[Simulator] ERROR sending ACK: " + e.getMessage());
        }
    }

    private void sendNak(int sequenceId, int errorCode) {
        try {
            String nakFrame = frameBuilder.build("NAK", sequenceId, String.valueOf(errorCode));
            commandConnection.sendFrame(nakFrame);
            System.out.println("[Simulator] NAK #" + sequenceId
                    + " sent — code=" + errorCode
                    + " (" + statusRegistry.getDescription(errorCode) + ")");
        } catch (IOException e) {
            System.out.println("[Simulator] ERROR sending NAK: " + e.getMessage());
        }
    }

    private void spawnWorker(ParsedCommand cmd, int sequenceId) {
        CommandWorker worker = new CommandWorker(cmd, sequenceId, eventConnection, statusRegistry);
        Thread workerThread  = new Thread(worker, "CommandWorker-" + sequenceId);
        workerThread.start();
        System.out.println("[Simulator] CommandWorker spawned for command #" + sequenceId);
    }
}
