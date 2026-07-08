package com.waferrobot.controller;

import com.waferrobot.config.AppConfig;
import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameBuilder;
import com.waferrobot.protocol.FrameParser;
import com.waferrobot.registry.StatusCodeRegistry;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WaferRobotController — Main class for the Controller application.
 *
 * Roles:
 *   - TCP Server: binds command port (5000) and event port (5001)
 *   - Reads robot command payloads from stdin
 *   - Spawns a CommandSender thread per command
 *   - Starts a StatListener daemon thread to receive EVT and STAT frames
 *
 * Run:
 *   java -jar WaferRobotController.jar [path/to/app.properties]
 */
public class WaferRobotController {

    private static final String DEFAULT_CONFIG = "config/app.properties";
    private static final int    SEQUENCE_START = 100;

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : DEFAULT_CONFIG;

        // Load configuration
        AppConfig config = new AppConfig();
        config.load(configPath);

        int commandPort = config.getInt("controller.command.port");
        int eventPort   = config.getInt("controller.event.port");

        String errorCodesPath = config.getProperty("error.codes.file", "config/error_codes.csv");

        // Load status codes
        StatusCodeRegistry statusRegistry = new StatusCodeRegistry();
        statusRegistry.loadFromCsv(errorCodesPath);

        FrameBuilder        frameBuilder = new FrameBuilder();
        FrameParser         frameParser  = new FrameParser();
        ClientFrameDispatcher dispatcher = new ClientFrameDispatcher(statusRegistry);

        SocketConnection commandConnection = new SocketConnection();
        SocketConnection eventConnection   = new SocketConnection();

        System.out.println("=== Wafer Robot Controller ===");
        System.out.println("Waiting for Simulator to connect...");

        // Accept Simulator connections on both ports (command first, then event)
        try {
            commandConnection.listenAndAccept(commandPort);
            eventConnection.listenAndAccept(eventPort);
        } catch (IOException e) {
            System.out.println("[Controller] FATAL: Cannot start server — " + e.getMessage());
            return;
        }

        // Start StatListener as daemon thread
        StatListener statListener = new StatListener(eventConnection, frameParser, dispatcher);
        Thread statThread = new Thread(statListener, "StatListener");
        statThread.setDaemon(true);
        statThread.start();

        // Input loop
        Scanner scanner = new Scanner(System.in);
        int sequenceId = SEQUENCE_START;
        AtomicBoolean ready = new AtomicBoolean(true);

        System.out.println("[Controller] Ready. Type a command payload and press Enter.");
        System.out.println("[Controller] Example: PICK FROM=LPA1 ARM=LOWER");
        System.out.println("[Controller] Type 'exit' to quit.\n");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if (input.equalsIgnoreCase("exit")) break;

            // Wait for previous command to complete before sending next
            while (!ready.get()) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }

            ready.set(false);
            int currentSeq = sequenceId++;

            CommandSender sender = new CommandSender(
                    input, currentSeq,
                    commandConnection, frameBuilder, frameParser,
                    dispatcher, ready);

            Thread senderThread = new Thread(sender, "CommandSender-" + currentSeq);
            senderThread.start();
        }

        System.out.println("[Controller] Exiting...");
        commandConnection.close();
        eventConnection.close();
    }
}
