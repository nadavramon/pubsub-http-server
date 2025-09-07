package test;

import java.io.IOException;
import java.io.OutputStream;

import test.RequestParser.RequestInfo;

@FunctionalInterface
public interface Servlet {
    void handle(RequestInfo ri, OutputStream toClient) throws IOException;

    default void close() throws IOException{}
}
