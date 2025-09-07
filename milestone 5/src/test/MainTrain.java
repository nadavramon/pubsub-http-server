package test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import test.RequestParser.RequestInfo;

public class MainTrain { // RequestParser

    private static void testParseRequest() {
        // Test data
        String request = "GET /api/resource?id=123&name=test HTTP/1.1\n" +
                "Host: example.com\n" +
                "Content-Length: 5\n"+
                "\n" +
                "filename=\"hello_world.txt\"\n"+
                "\n" +
                "hello world!\n"+
                "\n" ;

        BufferedReader input=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.getBytes())));
        try {
            RequestParser.RequestInfo requestInfo = RequestParser.parseRequest(input);

            // Test HTTP command
            if (!requestInfo.getHttpCommand().equals("GET")) {
                System.out.println("HTTP command test failed (-5)");
            }

            // Test URI
            if (!requestInfo.getUri().equals("/api/resource?id=123&name=test")) {
                System.out.println("URI test failed (-5)");
            }

            // Test URI segments
            String[] expectedUriSegments = {"api", "resource"};
            if (!Arrays.equals(requestInfo.getUriSegments(), expectedUriSegments)) {
                System.out.println("URI segments test failed (-5)");
                for(String s : requestInfo.getUriSegments()){
                    System.out.println(s);
                }
            }
            // Test parameters
            Map<String, String> expectedParams = new HashMap<>();
            expectedParams.put("id", "123");
            expectedParams.put("name", "test");
            expectedParams.put("filename","\"hello_world.txt\"");
            if (!requestInfo.getParameters().equals(expectedParams)) {
                System.out.println("Parameters test failed (-5)");
            }

            // Test content
            byte[] expectedContent = "hello world!\n".getBytes();
            if (!Arrays.equals(requestInfo.getContent(), expectedContent)) {
                System.out.println("Content test failed (-5)");
            }
            input.close();
        } catch (IOException e) {
            System.out.println("Exception occurred during parsing: " + e.getMessage() + " (-5)");
        }
    }


    public static void testServer() throws Exception {
        // Baseline thread count
        int baseThreads = Thread.activeCount();

        // ------------------------------------------------------------------
        // 1. Spin‑up HTTP server on an ephemeral port with two simple servlets
        // ------------------------------------------------------------------
        int PORT = 8080;                        // change if needed
        HTTPServer server = new MyHTTPServer(PORT, 4);


        // 1‑A Add servlet that adds two integers sent as query params (?a=5&b=7)
        server.addServlet("GET", "/add", (ri, out) -> {
            int a = Integer.parseInt(ri.getParameters().getOrDefault("a", "0"));
            int b = Integer.parseInt(ri.getParameters().getOrDefault("b", "0"));
            String sum = String.valueOf(a + b) + "\n";            // ensure LF
            byte[] body = sum.getBytes();
            // write raw HTTP response using OutputStream
            String headers = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\n\r\n";
            out.write(headers.getBytes());
            out.write(body);
            out.flush();
        });

        // 1‑B Echo servlet – returns the body exactly as received
        server.addServlet("POST", "/echo", (ri, out) -> {
            byte[] body = ri.getContent();
            PrintWriter pw = new PrintWriter(out);
            pw.print("HTTP/1.1 200 OK\r\nContent-Length: " + body.length + "\r\n\r\n");
            pw.flush();
            out.write(body);
            out.flush();
        });

        server.start();              // listener thread
        Thread.sleep(200);           // give the server time to bind

        // Assert only one extra thread so far
        if (Thread.activeCount() != baseThreads + 1) {
            System.out.println("PTM2: the configuration did not create the right number of threads.");
        }

        // ------------------------------------------------------------------
        // 2. Client #1 – test the /add servlet (happy path)
        // ------------------------------------------------------------------
        try (Socket s = new Socket("localhost", PORT);
             OutputStream out = s.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            String req = "GET /add?a=5&b=7 HTTP/1.1\r\nHost: test\r\n\r\n";
            out.write(req.getBytes());
            out.flush();

            String status = in.readLine();                  // HTTP/1.1 200 OK
            in.readLine();                                  // Content-Length
            in.readLine();                                  // blank
            String sum = in.readLine();                     // body

            if (!"12".equals(sum)) {
                System.out.println("Servlet add test failed (-10)");
            }
        }

        // ------------------------------------------------------------------
        // 3. Client #2 – POST with meta block + body to /echo
        // ------------------------------------------------------------------
        String metaReq =
                "POST /echo HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Content-Length: 5\r\n" +           // intentionally wrong
                        "\r\n" +
                        "filename=\"file.txt\"\r\n" +
                        "\r\n" +
                        "hello world!\n";

        try (Socket s = new Socket("localhost", PORT);
             OutputStream out = s.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            out.write(metaReq.getBytes());
            out.flush();

            String status = in.readLine();          // 200 OK
            String cl    = in.readLine();           // Content-Length
            in.readLine();                          // blank
            String body  = in.readLine();           // echoed body

            if (!"hello world!".equals(body)) {
                System.out.println("Echo servlet meta test failed (-10)");
            }
        }

        // ------------------------------------------------------------------
        // 4. Client #3 – POST correct Content‑Length, no meta
        // ------------------------------------------------------------------
        String payload = "DATA_LINE\n";
        String plainReq =
                "POST /echo HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Content-Length: " + payload.length() + "\r\n\r\n" +
                        payload;

        try (Socket s = new Socket("localhost", PORT);
             OutputStream out = s.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

            out.write(plainReq.getBytes());
            out.flush();

            in.readLine();  // status
            in.readLine();  // Content-Length
            in.readLine();  // blank
            String body = in.readLine();

            if (!"DATA_LINE".equals(body)) {
                System.out.println("Echo servlet plain test failed (-10)");
            }
        }

        // ------------------------------------------------------------------
        // 5. Shutdown and verify threads
        // ------------------------------------------------------------------
        server.close();
        Thread.sleep(500);              // allow server to terminate
        if (Thread.activeCount() > baseThreads) {
            System.out.println("Thread leak detected (-10)");
        }
    }

    public static void main(String[] args) {
        testParseRequest(); // 40 points
        try{
            testServer(); // 60
        }catch(Exception e){
            System.out.println("your server throwed an exception (-60)");
        }
        System.out.println("done");
    }

}
