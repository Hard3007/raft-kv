package io.github.hard3007.raftkv.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tiny TCP proxy in front of one node, so tests can cut real connections in the middle of a
 * call. {@link #resetAll()} closes every live connection with a TCP RST (SO_LINGER 0), the way a
 * pulled cable or a killed process looks to the other side: requests that were sent but not yet
 * answered may or may not have reached the node. New connections are accepted again right away.
 */
final class TcpProxy implements AutoCloseable {

    private final ServerSocket listener;
    private final int targetPort;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Thread acceptor;
    private volatile boolean closed;

    TcpProxy(int listenPort, int targetPort) throws IOException {
        this.listener = new ServerSocket(listenPort, 50, InetAddress.getLoopbackAddress());
        this.targetPort = targetPort;
        this.acceptor = new Thread(this::acceptLoop, "tcp-proxy-" + listenPort);
        acceptor.setDaemon(true);
        acceptor.start();
    }

    int port() {
        return listener.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket client = listener.accept();
                Socket server;
                try {
                    server = new Socket(InetAddress.getLoopbackAddress(), targetPort);
                } catch (IOException targetDown) {
                    reset(client); // the node is down: refuse, like a closed port
                    continue;
                }
                client.setTcpNoDelay(true);
                server.setTcpNoDelay(true);
                sockets.add(client);
                sockets.add(server);
                pump(client, server);
                pump(server, client);
            } catch (IOException e) {
                if (!closed) {
                    continue; // a single failed accept is not fatal
                }
            }
        }
    }

    /** Copies bytes from one socket to the other until either side fails; then resets both. */
    private void pump(Socket from, Socket to) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[16 * 1024];
            try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    out.flush();
                }
            } catch (IOException e) {
                // either side went away
            } finally {
                reset(from);
                reset(to);
            }
        }, "tcp-proxy-pump");
        thread.setDaemon(true);
        thread.start();
    }

    /** Cuts every live connection with an RST. In-flight calls fail on both sides. */
    void resetAll() {
        for (Socket socket : sockets) {
            reset(socket);
        }
    }

    private void reset(Socket socket) {
        sockets.remove(socket);
        try {
            socket.setSoLinger(true, 0); // close() now sends RST instead of FIN
        } catch (SocketException e) {
            // already closed
        }
        try {
            socket.close();
        } catch (IOException e) {
            // already closed
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            listener.close();
        } catch (IOException e) {
            // ignore
        }
        resetAll();
    }
}
