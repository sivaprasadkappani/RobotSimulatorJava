package com.waferrobot.simulator;

import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameBuilder;
import com.waferrobot.registry.StatusCodeRegistry;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon thread that periodically sends STAT frames to the Controller
 * on the event connection.
 *
 * Shares the event SocketConnection with CommandWorker threads.
 * sendFrame() is synchronized so concurrent writes are safe.
 */
public class StatusBroadcaster implements Runnable {

    private final SocketConnection   eventConnection;
    private final StatusCodeRegistry statusRegistry;
    private final long               intervalMs;
    private final FrameBuilder       frameBuilder;
    private final AtomicInteger      sequenceCounter;

    /**
     * @param eventConnection event socket connection (port 5001)
     * @param statusRegistry  for resolving status codes
     * @param intervalMs      broadcast interval in milliseconds
     * @param sequenceCounter shared sequence counter with the Simulator main thread
     */
    public StatusBroadcaster(SocketConnection eventConnection,
                              StatusCodeRegistry statusRegistry,
                              long intervalMs,
                              AtomicInteger sequenceCounter) {
        this.eventConnection  = eventConnection;
        this.statusRegistry   = statusRegistry;
        this.intervalMs       = intervalMs;
        this.frameBuilder     = new FrameBuilder();
        this.sequenceCounter  = sequenceCounter;
    }

    @Override
    public void run() {
        System.out.println("[StatusBroadcaster] Started. Broadcasting STAT every "
                + intervalMs + "ms.");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalMs);

                int statusCode = statusRegistry.getCode("PROCESSING_COMPLETE");
                if (statusCode < 0) statusCode = 205;

                int seq = sequenceCounter.getAndIncrement();
                String statFrame = frameBuilder.build("STAT", seq, String.valueOf(statusCode));
                eventConnection.sendFrame(statFrame);

                System.out.println("[StatusBroadcaster] STAT #" + seq
                        + " sent — code=" + statusCode
                        + " (" + statusRegistry.getDescription(statusCode) + ")");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException e) {
                System.out.println("[StatusBroadcaster] ERROR sending STAT: " + e.getMessage());
                break;
            }
        }
        System.out.println("[StatusBroadcaster] Stopped.");
    }
}
