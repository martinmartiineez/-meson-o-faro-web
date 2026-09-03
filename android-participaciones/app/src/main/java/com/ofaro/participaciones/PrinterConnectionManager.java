package com.ofaro.participaciones;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Conexión TCP persistente y compartida con la impresora ESC/POS.
 * La impresora puede cerrar conexiones inactivas: este gestor reconecta
 * automáticamente antes del siguiente trabajo sin depender de Apps Script.
 */
final class PrinterConnectionManager {
    interface OutputJob { void run(OutputStream out) throws Exception; }

    enum State { UNCONFIGURED, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

    private static final PrinterConnectionManager INSTANCE = new PrinterConnectionManager();
    static PrinterConnectionManager get() { return INSTANCE; }

    private final Object lock = new Object();
    private Socket socket;
    private OutputStream output;
    private String host = "";
    private int port = 9100;
    private volatile State state = State.DISCONNECTED;
    private volatile String lastError = "";
    private volatile long connectedAt = 0L;

    private PrinterConnectionManager() {}

    State state() { return state; }
    String lastError() { return lastError; }
    long connectedAt() { return connectedAt; }

    String statusText() {
        switch (state) {
            case CONNECTED: return "Conectada · " + host + ":" + port;
            case CONNECTING: return "Conectando · " + host + ":" + port;
            case UNCONFIGURED: return "Impresora sin configurar";
            case ERROR: return "Sin conexión · " + (lastError.isEmpty() ? host + ":" + port : lastError);
            default: return "Desconectada · " + host + ":" + port;
        }
    }

    boolean isConnected() {
        synchronized (lock) {
            return socketHealthyLocked();
        }
    }

    boolean ensureConnected(String ip, int printerPort) {
        String safeHost = ip == null ? "" : ip.trim();
        int safePort = printerPort > 0 && printerPort < 65536 ? printerPort : 9100;
        if (safeHost.isEmpty()) {
            state = State.UNCONFIGURED;
            lastError = "IP no configurada";
            close();
            return false;
        }
        synchronized (lock) {
            try {
                if (!safeHost.equals(host) || safePort != port) closeLocked();
                host = safeHost;
                port = safePort;
                if (socketHealthyLocked()) {
                    state = State.CONNECTED;
                    return true;
                }
                state = State.CONNECTING;
                closeLocked();
                Socket s = new Socket();
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                s.setReuseAddress(true);
                s.connect(new InetSocketAddress(host, port), 3000);
                s.setSoTimeout(5000);
                socket = s;
                output = s.getOutputStream();
                state = State.CONNECTED;
                lastError = "";
                connectedAt = System.currentTimeMillis();
                return true;
            } catch (Exception e) {
                lastError = message(e);
                state = State.ERROR;
                closeLocked();
                return false;
            }
        }
    }

    void execute(String ip, int printerPort, OutputJob job) throws Exception {
        synchronized (lock) {
            if (!ensureConnected(ip, printerPort)) {
                throw new Exception(lastError.isEmpty() ? "No se pudo conectar con la impresora" : lastError);
            }
            try {
                job.run(output);
                output.flush();
                state = State.CONNECTED;
                lastError = "";
            } catch (Exception e) {
                lastError = message(e);
                state = State.ERROR;
                closeLocked();
                throw e;
            }
        }
    }

    void reconnect(String ip, int printerPort) {
        synchronized (lock) { closeLocked(); }
        ensureConnected(ip, printerPort);
    }

    void close() {
        synchronized (lock) { closeLocked(); }
    }

    private boolean socketHealthyLocked() {
        return socket != null && output != null && socket.isConnected() && !socket.isClosed() && !socket.isOutputShutdown();
    }

    private void closeLocked() {
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        output = null;
        socket = null;
        if (state == State.CONNECTED || state == State.CONNECTING) state = State.DISCONNECTED;
    }

    private static String message(Throwable t) {
        if (t == null) return "Error desconocido";
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m.trim();
    }
}
