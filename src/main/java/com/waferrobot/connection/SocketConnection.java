package com.waferrobot.connection;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Reusable TCP socket connection that supports both server and client roles.
 *
 * Controller creates two instances in server mode (command port + event port).
 * Simulator creates two instances in client mode (connecting to both ports).
 *
 * sendFrame() is synchronized — multiple threads (e.g. CommandWorker and
 * StatusBroadcaster) may share the same event connection safely.
 */
public class SocketConnection {

    private ServerSocket serverSocket;
    private Socket       socket;
    private PrintWriter  writer;
    private BufferedReader reader;

    /**
     * TCP Server mode: binds to the given port and blocks until a client connects.
     *
     * @param port TCP port to listen on
     * @throws IOException if the port cannot be bound or the accept fails
     */
    public void listenAndAccept(int port) throws IOException {
        System.out.println("[SocketConnection] Listening on port " + port + " ...");
        serverSocket = new ServerSocket(port);
        socket       = serverSocket.accept();
        initStreams();
        System.out.println("[SocketConnection] Client connected on port " + port
                + " from " + socket.getInetAddress().getHostAddress());
    }

    /**
     * TCP Client mode: connects to the given host and port.
     *
     * @param host remote host name or IP
     * @param port remote TCP port
     * @throws IOException if the connection fails
     */
    public void connect(String host, int port) throws IOException {
        System.out.println("[SocketConnection] Connecting to " + host + ":" + port + " ...");
        socket = new Socket(host, port);
        initStreams();
        System.out.println("[SocketConnection] Connected to " + host + ":" + port);
    }

    private void initStreams() throws IOException {
        writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), false);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Sends a complete frame string over the socket.
     * Synchronized so multiple threads can share the same connection safely.
     *
     * @param frame complete frame string (SOH ... CRLF)
     * @throws IOException if the write fails
     */
    public synchronized void sendFrame(String frame) throws IOException {
        if (writer == null) {
            throw new IOException("SocketConnection is not open.");
        }
        writer.print(frame);
        writer.flush();
    }

    /**
     * Reads bytes from the socket until a complete frame ending in LF is received.
     * Blocks until a full frame arrives.
     *
     * @return the complete raw frame string including SOH and CRLF
     * @throws IOException if the read fails or the connection is closed
     */
    public String receiveFrame() throws IOException {
        if (reader == null) {
            throw new IOException("SocketConnection is not open.");
        }
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            sb.append((char) ch);
            // Frame ends at LF (\n); CR\LF terminator — stop after LF
            if (ch == '\n') {
                break;
            }
        }
        if (sb.isEmpty()) {
            throw new IOException("Connection closed by remote peer.");
        }
        return sb.toString();
    }

    /**
     * Closes the socket and all associated streams.
     */
    public void close() {
        try { if (writer      != null) writer.close();      } catch (Exception ignored) {}
        try { if (reader      != null) reader.close();      } catch (Exception ignored) {}
        try { if (socket      != null) socket.close();      } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
    }
}
