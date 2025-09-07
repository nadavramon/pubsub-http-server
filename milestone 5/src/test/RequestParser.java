package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RequestParser {

    public static RequestInfo parseRequest(BufferedReader reader) throws IOException {
        // 1. Read the request line
        String requestLine = reader.readLine();
        if (requestLine == null)
            throw new IOException("Empty request");
        String[] requestParts = requestLine.split(" ");
        if (requestParts.length < 2)
            throw new IOException("Malformed request line");

        String method = requestParts[0];
        String url = requestParts[1];

        // 2. Parse URI and parameters
        String uri;
        Map<String, String> params = new HashMap<>();
        int qIndex = url.indexOf('?');
        if (qIndex >= 0) {
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

        //3. split URI into segments
        String[] uriSegments = uri.startsWith("/") ? uri.substring(1).split("/") : uri.split("/");

        // 4. Read headers to find content-length
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException e) {
                    contentLength = 0;
                }
            }
        }

        // 5. Read meta-parameters (key=value) until the blank line terminator
        String firstBodyLine = null;
        while (true) {
            if (!reader.ready()) {
                // no more buffered data => headers are done
                break;
            }
            reader.mark(8192);
            line = reader.readLine();
            if (line == null || line.isEmpty()) {
                // blank line: end of headers/meta
                break;
            }
            if (line.contains("=")) {
                String[] kv = line.split("=", 2);
                if (kv.length == 2)
                    params.put(kv[0], kv[1]);
            } else {
                // first non-meta line is the body start
                firstBodyLine = line;
                reader.reset();  // push body line back for the next step
                break;
            }
        }

        // 6. Capture the body – at most one line – no blocking
        String bodyLine;
        if (firstBodyLine != null) {
            bodyLine = firstBodyLine;                      // body already read
        } else {
            bodyLine = reader.ready() ? reader.readLine() : null;
        }
        if (bodyLine == null) bodyLine = "";
        byte[] content = (bodyLine + "\n").getBytes("UTF-8");   // keep single LF

        // 7. Return the fully populated RequestInfo
        return new RequestInfo(method, url, uriSegments, params, content);
    }

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