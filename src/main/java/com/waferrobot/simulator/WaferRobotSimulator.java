package com.waferrobot.simulator;

import com.waferrobot.config.AppConfig;
import com.waferrobot.connection.SocketConnection;
import com.waferrobot.protocol.FrameParser;
import com.waferrobot.protocol.RobotFrame;
import com.waferrobot.registry.CommandRegistry;
import com.waferrobot.registry.StatusCodeRegistry;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WaferRobotSimulator — Main class for the Simulator application.
 *
 * Roles:
 *   - TCP Client: connects to the Controller on command port (5000) and event port (5001)
 *   - Receives and validates CMD frames from the Controller
 *   - Sends ACK or NAK responses on the command connection
 *   - Spawns CommandWorker threads that send EVT on the event connection
 *   - Starts a StatusBroadcaster daemon thread for periodic STAT messages
 *
 * Run:
 *   java -jar WaferRobotSimulator.jar [path/to/app.properties]
 */
public class WaferRobotSimulator {

    private static final String DEFAULT_CONFIG  = "config/app.properties";
    private static final String DEFAULT_CFG     = "config/robot.cfg";
    private static final String DEFAULT_CODES   = "config/error_codes.csv";

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : DEFAULT_CONFIG;

        // Load application configuration
        AppConfig config = new AppConfig();
        config.load(configPath);

        String host        = config.getProperty("controller.host",    "localhost");
        int    commandPort = config.getInt("controller.command.port");
        int    eventPort   = config.getInt("controller.event.port");
        long   statInterval = Long.parseLong(
                config.getProperty("stat.interval.ms", "5000"));

        String robotCfgPath = config.getProperty("robot.cfg.file", DEFAULT_CFG);
        String errorCodesPath = config.getProperty("error.codes.file", DEFAULT_CODES);

        // Load command registry
        CommandRegistry commandRegistry = new CommandRegistry();
        if (!commandRegistry.loadFromCfg(robotCfgPath)) {
            System.out.println("[Simulator] FATAL: Cannot load robot.cfg — exiting.");
            System.exit(-1);
        }

        // Load status code registry
        StatusCodeRegistry statusRegistry = new StatusCodeRegistry();
        statusRegistry.loadFromCsv(errorCodesPath);

        // Connect to Controller
        SocketConnection commandConnection = new SocketConnection();
        SocketConnection eventConnection   = new SocketConnection();

        System.out.println("=== Wafer Robot Simulator ===");

        try {
            commandConnection.connect(host, commandPort);
            eventConnection.connect(host, eventPort);
        } catch (IOException e) {
            System.out.println("[Simulator] FATAL: Cannot connect to Controller — " + e.getMessage());
            System.exit(1);
        }

        // Sequence counter shared between main loop and StatusBroadcaster
        AtomicInteger sequenceCounter = new AtomicInteger(1000);

        // Start StatusBroadcaster as daemon thread
        StatusBroadcaster broadcaster = new StatusBroadcaster(
                eventConnection, statusRegistry, statInterval, sequenceCounter);
        Thread broadcasterThread = new Thread(broadcaster, "StatusBroadcaster");
        broadcasterThread.setDaemon(true);
        broadcasterThread.start();

        // Frame processing
        FrameParser             frameParser  = new FrameParser();
        SimulatorFrameDispatcher dispatcher  = new SimulatorFrameDispatcher(
                commandRegistry, statusRegistry, commandConnection, eventConnection);

        System.out.println("[Simulator] Ready. Waiting for commands from Controller...\n");

        // Receive loop
        while (true) {
            try {
                String rawFrame = commandConnection.receiveFrame();
                RobotFrame frame = frameParser.parse(rawFrame);
                dispatcher.dispatch(frame);
            } catch (IOException e) {
                System.out.println("[Simulator] Command connection closed or error: " + e.getMessage());
                break;
            }
        }

        System.out.println("[Simulator] Shutting down.");
        commandConnection.close();
        eventConnection.close();
    }
}
