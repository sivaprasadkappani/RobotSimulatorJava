package com.waferrobot.controller;

import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameBuilder;
import com.waferrobot.protocol.FrameParser;
import com.waferrobot.protocol.RobotFrame;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends a single CMD frame to the Simulator and waits for the ACK or NAK response.
 * Runs in its own thread so the Controller main thread stays responsive.
 *
 * The Controller creates a new CommandSender instance per user command.
 * The shared 'ready' flag signals the main thread when this sender has finished.
 */
public class CommandSender implements Runnable {

    private final String              payload;
    private final int                 sequenceId;
    private final SocketConnection    commandConnection;
    private final FrameBuilder        frameBuilder;
    private final FrameParser         frameParser;
    private final ClientFrameDispatcher dispatcher;
    private final AtomicBoolean       ready;

    /**
     * @param payload           command text entered by the user
     * @param sequenceId        sequence number for this command
     * @param commandConnection command socket connection (port 5000)
     * @param frameBuilder      for building the CMD frame
     * @param frameParser       for parsing the ACK/NAK response
     * @param dispatcher        for routing the response to output
     * @param ready             shared flag set to true when this sender completes
     */
    public CommandSender(String payload, int sequenceId,
                         SocketConnection commandConnection,
                         FrameBuilder frameBuilder, FrameParser frameParser,
                         ClientFrameDispatcher dispatcher, AtomicBoolean ready) {
        this.payload           = payload;
        this.sequenceId        = sequenceId;
        this.commandConnection = commandConnection;
        this.frameBuilder      = frameBuilder;
        this.frameParser       = frameParser;
        this.dispatcher        = dispatcher;
        this.ready             = ready;
    }

    @Override
    public void run() {
        try {
            String frame = frameBuilder.build("CMD", sequenceId, payload);
            System.out.println("[Controller] Sending CMD #" + sequenceId + ": " + payload);
            commandConnection.sendFrame(frame);

            // Block waiting for ACK or NAK
            String rawResponse = commandConnection.receiveFrame();
            RobotFrame response = frameParser.parse(rawResponse);
            dispatcher.dispatch(response);

        } catch (IOException e) {
            System.out.println("[Controller] ERROR in CommandSender: " + e.getMessage());
        } finally {
            ready.set(true);
        }
    }
}
