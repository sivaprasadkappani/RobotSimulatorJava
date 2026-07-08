package com.waferrobot.controller;

import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameParser;
import com.waferrobot.protocol.RobotFrame;

import java.io.IOException;

/**
 * Daemon thread that continuously polls the event connection for incoming
 * EVT and STAT frames from the Simulator.
 *
 * Started once at Controller startup. Runs independently of the main
 * input loop so EVT and STAT frames are displayed as soon as they arrive.
 */
public class StatListener implements Runnable {

    private final SocketConnection      eventConnection;
    private final FrameParser           frameParser;
    private final ClientFrameDispatcher dispatcher;

    /**
     * @param eventConnection event socket connection (port 5001)
     * @param frameParser     for parsing received frames
     * @param dispatcher      for routing EVT and STAT frames to output
     */
    public StatListener(SocketConnection eventConnection,
                        FrameParser frameParser,
                        ClientFrameDispatcher dispatcher) {
        this.eventConnection = eventConnection;
        this.frameParser     = frameParser;
        this.dispatcher      = dispatcher;
    }

    @Override
    public void run() {
        System.out.println("[StatListener] Event listener started.");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String rawFrame = eventConnection.receiveFrame();
                RobotFrame frame = frameParser.parse(rawFrame);
                dispatcher.dispatch(frame);
            } catch (IOException e) {
                System.out.println("[StatListener] Event connection closed or error: " + e.getMessage());
                break;
            }
        }
        System.out.println("[StatListener] Event listener stopped.");
    }
}
