package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for parsing raw HTTP requests.
 * <p>
 * This class reads from a {@link BufferedReader} and extracts the HTTP method,
 * URI, headers,
 * and body content into a structured {@link RequestInfo} object.
 * </p>
 */
public class RequestParser {

    /**
     * Parses an HTTP request from an input stream.
     * <p>
     * The parsing logic follows a specific sequence:
     * <ol>
     * <li><b>Request Line:</b> Reads the first line to get Method, URL, and
     * Protocol.</li>
     * <li><b>URI & Parameters:</b> Separates the path from the query string (e.g.,
     * "?key=val").</li>
     * <li><b>Segments:</b> Splits the URI path by '/' for easier routing.</li>
     * <li><b>Headers:</b> Scans headers for "Content-Length".</li>
     * <li><b>Meta-Data/Body:</b> Handles specific body parsing or meta-data blocks
     * until a double newline.</li>
     * </ol>
     * </p>
     *
     * @param reader The buffered reader wrapping the client's input stream.
     * @return A {@link RequestInfo} object containing the parsed data.
     * @throws IOException If the request is malformed or I/O errors occur.
     */
    public static RequestInfo parseRequest(BufferedReader reader) throws IOException {
        // ------------------------------------------------------------------
        // 1. Read the request line (e.g., "GET /index.html HTTP/1.1")
        // ------------------------------------------------------------------
        String requestLine = reader.readLine();
        if (requestLine == null)
            throw new IOException("Empty request"); // Client closed connection or sent nothing
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length < 2)
            throw new IOException("Malformed request line: " + requestLine);

        String method = requestParts[0]; // e.g., GET, POST
        String url = requestParts[1]; // e.g., /api/data?id=5

        // ------------------------------------------------------------------
        // 2. Parse URI and parameters
        // ------------------------------------------------------------------
        String uri;
        Map<String, String> params = new HashMap<>();
        int qIndex = url.indexOf('?');
        if (qIndex >= 0) {
            // Split url into base URI and query string
            uri = url.substring(0, qIndex);
            String query = url.substring(qIndex + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    params.put(kv[0], kv[1]);
                }
            }
        } else {
            uri = url;
        }

        // ------------------------------------------------------------------
        // 3. Split URI into segments (e.g., "/api/v1" -> ["api", "v1"])
        // ------------------------------------------------------------------
        // Removing leading slash to avoid an empty first element
        String[] uriSegments = uri.startsWith("/") ? uri.substring(1).split("/") : uri.split("/");

        // ------------------------------------------------------------------
        // 4. Read headers (looking for Content-Length)
        // ------------------------------------------------------------------
        int contentLength = 0;
        String line;
        // Read until we hit an empty line, which signifies the end of the standard HTTP
        // headers
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException e) {
                    contentLength = 0;
                }
            }
        }

        // ------------------------------------------------------------------
        // 5. Read custom "Meta-Parameters" (if any)
        // ------------------------------------------------------------------
        // The protocol here supports key=value pairs in the body *before* the actual
        // content
        // separated by an empty line from the real content.
        String firstBodyLine = null;
        while (true) {
            // If there's no data ready and we haven't hit our condition, break.
            // This is a simplified check; in robust servers, we'd rely solely on
            // Content-Length.
            if (!reader.ready()) {
                break;
            }
            // Mark position to peek at the line
            reader.mark(8192);
            line = reader.readLine();
            if (line == null || line.isEmpty()) {
                // Determine if this blank line is a separator or end of stream
                break;
            }
            if (line.contains("=")) {
                // It looks like a meta-param (key=value)
                String[] kv = line.split("=", 2);
                if (kv.length == 2)
                    params.put(kv[0], kv[1]);
            } else {
                // Not a key=value pair? It must be the start of the actual body content.
                // Rewind user reader to processing "firstBodyLine" can happen in step 6.
                firstBodyLine = line;
                reader.reset();
                break;
            }
        }

        // ------------------------------------------------------------------
        // 6. Capture the body content
        // ------------------------------------------------------------------
        String bodyLine;
        if (firstBodyLine != null) {
            bodyLine = firstBodyLine;
        } else {
            // Only try to read if ready, to prevent blocking on streams with no body
            bodyLine = reader.ready() ? reader.readLine() : null;
        }
        if (bodyLine == null)
            bodyLine = "";

        // We assume UTF-8 and ensure there's a trailing newline for consistency
        byte[] content = (bodyLine + "\n").getBytes("UTF-8");

        // ------------------------------------------------------------------
        // 7. Return the structured RequestInfo
        // ------------------------------------------------------------------
        return new RequestInfo(method, url, uriSegments, params, content);
    }

    /**
     * Immutable Data Transfer Object (DTO) holding parsed request details.
     */
    public static class RequestInfo {
        private final String httpCommand;
        private final String uri;
        private final String[] uriSegments;
        private final Map<String, String> parameters;
        private final byte[] content;

        public RequestInfo(String httpCommand, String uri, String[] uriSegments,
                Map<String, String> parameters, byte[] content) {
            this.httpCommand = httpCommand;
            this.uri = uri;
            this.uriSegments = uriSegments;
            this.parameters = parameters;
            // Defensive copy of mutable arrays/maps is recommended in production code,
            // but we store references here for simplicity.
            this.content = content;
        }

        public String getHttpCommand() {
            return httpCommand;
        }

        public String getUri() {
            return uri;
        }

        public String[] getUriSegments() {
            return uriSegments;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public byte[] getContent() {
            return content;
        }

    }
}