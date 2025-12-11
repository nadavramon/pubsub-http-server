package test;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import test.RequestParser.RequestInfo;

import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Implementation of a multi-threaded HTTP Server.
 * <p>
 * This class listens on a specific port and dispatches incoming requests to
 * registered servlets.
 * It uses a Thread Pool used to handle multiple clients concurrently without
 * blocking the main listener loop.
 * </p>
 * <p>
 * <b>Key Architecture Features:</b>
 * <ul>
 * <li><b>Thread Pool:</b> Uses a fixed thread pool to manage client
 * connections, preventing resource exhaustion.</li>
 * <li><b>Non-blocking Accept:</b> Uses {@code setSoTimeout} to allow the server
 * loop to check for a shutdown flag periodically.</li>
 * <li><b>Servlet Registry:</b> Maintains a concurrent map of servlets (Method
 * -> URI -> Servlet) for thread-safe access.</li>
 * <li><b>Request Dispatching:</b> Supports exact matching and longest-prefix
 * matching for URIs.</li>
 * </ul>
 * </p>
 */
public class MyHTTPServer extends Thread implements HTTPServer {
    private final int port;
    private final int maxThreads;
    private volatile boolean running = false;

    private ServerSocket serverSocket;
    private final ExecutorService threadPool;

    // Map of HTTP method -> URI prefix -> Servlet
    // We use ConcurrentHashMap because this map might be read by multiple worker
    // threads
    // while the main thread (or another thread) adds/removes servlets.
    private final Map<String, Map<String, Servlet>> servlets;

    /**
     * Constructs a new MyHTTPServer.
     *
     * @param port     The port number to bind the server to (e.g., 8080).
     * @param nThreads The maximum number of threads in the thread pool.
     */
    public MyHTTPServer(int port, int nThreads) {
        this.port = port;
        this.maxThreads = nThreads;
        this.servlets = new ConcurrentHashMap<>();
        // FixedThreadPool is chosen to limit the maximum system resources used by the
        // server.
        this.threadPool = Executors.newFixedThreadPool(maxThreads);
    }

    /**
     * Registers a servlet for a specific HTTP method and URI.
     * <p>
     * The `httpCommand` is case-insensitive (stored as generic UPPERCASE).
     * </p>
     *
     * @param httpCommand The HTTP command (GET, POST, etc.)
     * @param uri         The URI path to handle.
     * @param s           The servlet instance.
     */
    @Override
    public void addServlet(String httpCommand, String uri, Servlet s) {
        servlets.computeIfAbsent(httpCommand.toUpperCase(), k -> new ConcurrentHashMap<>())
                .put(uri, s);
    }

    /**
     * Removes a registered servlet.
     */
    @Override
    public void removeServlet(String httpCommand, String uri) {
        Map<String, Servlet> uriMap = servlets.get(httpCommand.toUpperCase());
        if (uriMap != null) {
            uriMap.remove(uri);
        }
    }

    /**
     * The main server loop logic.
     * <p>
     * This method runs in the dedicated thread started by {@code start()}.
     * It continuously accepts new client connections until {@code running} becomes
     * false.
     * </p>
     */
    @Override
    public void run() {
        running = true; // Use a volatile flag to safely control the loop across threads
        try {
            serverSocket = new ServerSocket(port);
            // define a timeout for 'accept()' so we don't block forever.
            // This allows us to check the 'running' flag every 1 second.
            serverSocket.setSoTimeout(1000);
            while (running) {
                try {
                    // This call blocks for at most 1000ms
                    Socket clientSocket = serverSocket.accept();
                    // Offload the heavy lifting of handling the request to a worker thread
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (SocketTimeoutException e) {
                    // Time out occurred; the loop continues and checks 'running' again.
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Server error: " + e.getMessage());
            }
        }
    }

    /**
     * Handles a single client connection.
     * <p>
     * 1. Parses the request using {@link RequestParser}.
     * 2. Finds the appropriate servlet.
     * 3. Delegates execution to the servlet or returns 404.
     * </p>
     *
     * @param clientSocket The connected client socket.
     */
    private void handleClient(Socket clientSocket) {
        BufferedReader fromClient = null;
        OutputStream toClient = null;
        try {
            // Setup streams
            fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            toClient = clientSocket.getOutputStream();

            // Parse the raw HTTP request
            RequestInfo ri = RequestParser.parseRequest(fromClient);

            // Basic validation check
            if (ri == null || ri.getHttpCommand() == null || ri.getUri() == null) {
                respondNotFound(toClient);
                return;
            }

            // Find matching servlet based on Method and URI using longest-prefix matching
            Servlet servlet = findServlet(ri.getHttpCommand(), ri.getUri());
            if (servlet != null) {
                servlet.handle(ri, toClient);
            } else {
                respondNotFound(toClient);
            }
        } catch (SocketTimeoutException ste) {
            // Client read timed out — simply close the connection
        } catch (Exception e) {
            // In a real server, we would log this safely.
        } finally {
            // Ensure resources are always closed to avoid file descriptor leaks
            try {
                if (fromClient != null)
                    fromClient.close();
            } catch (IOException ignored) {
            }
            try {
                if (toClient != null)
                    toClient.flush();
            } catch (IOException ignored) {
            }
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Finds the most specific servlet for the given request.
     * <p>
     * Implements "Longest Prefix Matching". For example, if we have servlets at:
     * <ul>
     * <li>{@code /api/users}</li>
     * <li>{@code /api}</li>
     * </ul>
     * A request to {@code /api/users/123} will match {@code /api/users} because it
     * is the longer match.
     * </p>
     *
     * @param method The HTTP method.
     * @param uri    The full request URI.
     * @return The matching Servlet, or null if none found.
     */
    private Servlet findServlet(String method, String uri) {
        // Normalize HTTP method
        String cmd = method.toUpperCase(Locale.ROOT);

        // Strip off the query string (anything after '?') because mapping is done on
        // the path only
        String path = uri.split("\\?", 2)[0];

        // Retrieve servlet map for this HTTP command
        Map<String, Servlet> map = servlets.get(cmd);
        if (map == null)
            return null;

        // 1. Exact-match check (Optimization)
        if (map.containsKey(path)) {
            return map.get(path);
        }

        // 2. Fallback to longest-prefix matching
        String bestMatch = null;
        for (String registeredPath : map.keySet()) {
            if (path.startsWith(registeredPath)) {
                // If this path is a prefix of our request and is longer than any previous
                // match, take it.
                if (bestMatch == null || registeredPath.length() > bestMatch.length()) {
                    bestMatch = registeredPath;
                }
            }
        }
        return (bestMatch != null) ? map.get(bestMatch) : null;
    }

    /**
     * Helper to send a standardized 404 response.
     */
    private void respondNotFound(OutputStream out) throws IOException {
        String body = "404 Not Found";
        String response = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + body.getBytes().length + "\r\n" +
                "\r\n" +
                body;
        out.write(response.getBytes());
        out.flush();
    }

    /**
     * Gracefully stops the server.
     * <p>
     * 1. Sets the running flag to false.
     * 2. Closes the socket to unblock {@code accept()}.
     * 3. Shuts down the thread pool.
     * 4. Closes all registered servlets.
     * </p>
     */
    @Override
    public void close() {
        running = false;

        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException ignored) {
        }

        threadPool.shutdown();

        for (Map<String, Servlet> uriMap : servlets.values()) {
            for (Servlet servlet : uriMap.values()) {
                try {
                    servlet.close();
                } catch (IOException ignored) {
                }
            }
        }
        servlets.clear();
        try {
            // Wait a bit for existing tasks to finish
            if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                threadPool.shutdownNow(); // Force shutdown if tasks take too long
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}