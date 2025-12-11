package test;

/**
 * Interface representing a simple HTTP Server.
 * <p>
 * This interface defines the contract for a lightweight HTTP server capability,
 * decoupling the specific implementation details (like threading model or
 * socket handling)
 * from the contract of starting, stopping, and managing servlet registrations.
 * </p>
 */
public interface HTTPServer extends Runnable {

    /**
     * Registers a new Servlet to handle specific HTTP requests.
     *
     * @param httpCommand The HTTP method (e.g., "GET", "POST", "DELETE") to listen
     *                    for.
     * @param uri         The URI path (e.g., "/api/data") that this servlet should
     *                    handle.
     * @param s           The {@link Servlet} instance containing the logic to
     *                    execute when a matching request is received.
     */
    void addServlet(String httpCommand, String uri, Servlet s);

    /**
     * Removes a registered Servlet.
     *
     * @param httpCommand The HTTP method of the servlet to remove.
     * @param uri         The URI path of the servlet to remove.
     */
    void removeServlet(String httpCommand, String uri);

    /**
     * Starts the server.
     * <p>
     * This method is responsible for initializing the server socket and starting
     * the
     * request listening loop, typically in a dedicated thread.
     * </p>
     */
    void start();

    /**
     * Stops the server and releases all resources.
     * <p>
     * This includes closing the server socket, shutting down any thread pools, and
     * ensuring all registered servlets are properly closed.
     * </p>
     */
    void close();
}
