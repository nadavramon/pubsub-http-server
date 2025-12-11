package test;

import java.io.IOException;
import java.io.OutputStream;

import test.RequestParser.RequestInfo;

/**
 * Functional interface for handling HTTP requests.
 * <p>
 * This interface follows the Command pattern, allowing individual request
 * handlers
 * (Servlets) to be defined dynamically and registered with the content.
 * It is a {@link FunctionalInterface}, meaning it can be implemented with
 * lambda expressions.
 * </p>
 */
@FunctionalInterface
public interface Servlet {

    /**
     * Handles an incoming HTTP request.
     *
     * @param ri       The parsed request information, including method, URI,
     *                 parameters, and content.
     * @param toClient The output stream connected to the client. The servlet acts
     *                 as a "command"
     *                 that writes its response directly to this stream.
     * @throws IOException If an I/O error occurs while writing to the client.
     */
    void handle(RequestInfo ri, OutputStream toClient) throws IOException;

    /**
     * Lifecycle method to close any resources used by the Servlet.
     * <p>
     * This default implementation does nothing, but specific implementations can
     * override
     * it to close database connections, file streams, etc., when the server is shut
     * down.
     * </p>
     *
     * @throws IOException If an I/O error occurs during closure.
     */
    default void close() throws IOException {
    }
}
