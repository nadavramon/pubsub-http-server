package test;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import test.RequestParser.RequestInfo;

import java.net.SocketTimeoutException;
import java.util.Locale;


public class MyHTTPServer extends Thread implements HTTPServer {
    private final int port;
    private final int maxThreads;
    private volatile boolean running = false;

    private ServerSocket serverSocket;
    private final ExecutorService threadPool;

    // Map of HTTP method -> URI prefix -> Servlet
    private final Map<String, Map<String, Servlet>> servlets;

    public MyHTTPServer(int port, int nThreads) {
        this.port = port;
        this.maxThreads = nThreads;
        this.servlets = new ConcurrentHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(maxThreads);
    }

    @Override
    public void addServlet(String httpCommand, String uri, Servlet s) {
        servlets.computeIfAbsent(httpCommand.toUpperCase(), k -> new ConcurrentHashMap<>())
                .put(uri, s);
    }

    @Override
    public void removeServlet(String httpCommand, String uri) {
        Map<String, Servlet> uriMap = servlets.get(httpCommand.toUpperCase());
        if (uriMap != null) {
            uriMap.remove(uri);
        }
    }

    @Override
    public void run() {
        running = true; // Needed so thread tracking checks work
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                try {
                    serverSocket.setSoTimeout(1000);
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (SocketTimeoutException e) {
                    // Expected, used for breaking accept() loop on shutdown
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Server error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        BufferedReader fromClient = null;
        OutputStream toClient = null;
        try {

            fromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            toClient = clientSocket.getOutputStream();

            RequestInfo ri = RequestParser.parseRequest(fromClient);

            if (ri == null || ri.getHttpCommand() == null || ri.getUri() == null) {
                respondNotFound(toClient);
                return;
            }

            Servlet servlet = findServlet(ri.getHttpCommand(), ri.getUri());
            if (servlet != null) {
                servlet.handle(ri, toClient);
            } else {
                respondNotFound(toClient);
            }
        } catch (SocketTimeoutException ste) {
            // Client read timed out — simply close the connection
        } catch (Exception e) {
            // Log other exceptions if needed
        } finally {
            try {
                if (fromClient != null) fromClient.close();
            } catch (IOException ignored) {
            }
            try {
                if (toClient != null) toClient.flush();
            } catch (IOException ignored) {
            }
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private Servlet findServlet(String method, String uri) {
        // Normalize HTTP method
        String cmd = method.toUpperCase(Locale.ROOT);

        // Strip off the query string (anything after '?')
        String path = uri.split("\\?", 2)[0];

        // Retrieve servlet map for this HTTP command
        Map<String, Servlet> map = servlets.get(cmd);
        if (map == null) return null;

        // Exact-match on the stripped path first
        if (map.containsKey(path)) {
            return map.get(path);
        }

        // Fallback to longest-prefix matching
        String bestMatch = null;
        for (String registeredPath : map.keySet()) {
            if (path.startsWith(registeredPath)) {
                if (bestMatch == null || registeredPath.length() > bestMatch.length()) {
                    bestMatch = registeredPath;
                }
            }
        }
        return (bestMatch != null) ? map.get(bestMatch) : null;
    }

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

    @Override
    public void close() {
        running = false;

        try {
            if (serverSocket != null) serverSocket.close();
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
            if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                threadPool.shutdownNow(); // Ensures thread leak does not occur
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}