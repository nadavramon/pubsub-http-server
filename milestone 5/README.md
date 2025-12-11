# Lightweight HTTP Server Implementation for Milestone 5

## 1. Overview
This project implements a lightweight, multi-threaded **HTTP Server** in Java from scratch. It was designed to demonstrate core networking concepts, concurrent programming using thread pools, and the **Command Pattern** for handling requests via Servlets.

The server allows for dynamic registration of "Servlets" (request handlers) that can process **GET**, **POST**, and other HTTP methods. It also features a custom `RequestParser` capable of handling standard URL parameters as well as a specific "meta-data" body format.

## 2. Architecture

### Server-Client Model
The core of the system is the `MyHTTPServer` class, which wraps a standard Java `ServerSocket`.
- **Listening Loop**: The server runs a continuous loop (in its own `run()` method) that accepts incoming client connections (`Socket`).
- **Non-blocking Accept**: We use `serverSocket.setSoTimeout(1000)` to ensure the `accept()` call doesn't block indefinitely. This allows the server to check a `volatile boolean running` flag periodically, enabling a graceful shutdown when `close()` is called.

### Concurrency Model
To handle multiple clients simultaneously without blocking the main server thread, we use a **Thread Pool** (`ExecutorService`).
- **Why a Thread Pool?** Creating a new thread for every request is expensive and can exhaust system resources (Context Switching overhead). A Fixed Thread Pool limits the number of active threads (e.g., 10), queuing additional requests until a thread becomes available.
- **Workflow**:
    1.  Main thread calls `accept()` and gets a `Socket`.
    2.  Main thread submits a task (`handleClient(socket)`) to the thread pool.
    3.  A worker thread picks up the task, parses the request, executes the servlet, and closes the socket.

## 3. Key Components

### `HTTPServer` (Interface)
Defines the contract for the server. It decouples the *usage* of a server (start, stop, add servlet) from its *implementation*. This allows for easy swapping of server implementations (e.g., single-threaded vs multi-threaded) without changing client code.

### `Servlet` (Functional Interface)
Represents a pluggable request handler.
- **Pattern**: This implements the **Command Pattern**. The server is the invoker, and the Servlet is the command.
- **Design**: It is a `FunctionalInterface`, allowing users to define handlers cleanly using **Lambda Expressions**:
  ```java
  server.addServlet("GET", "/hello", (req, out) -> out.write("Hello!".getBytes()));
  ```

### `RequestParser`
A static utility class responsible for reading the raw input stream and converting it into a structured `RequestInfo` object.
- **Parsing logic**:
    1.  Extracts HTTP Method and URI.
    2.  Parses Query Parameters (`?key=value`).
    3.  Reads Headers (specifically `Content-Length`).
    4.  **Custom Logic**: Handles a special body format where "meta-data" lines (key=value) can precede the actual content body, separated by a blank line.

### `MyHTTPServer`
The concrete implementation of the server.
- **Servlet Registry**: Uses a `ConcurrentHashMap<String, Map<String, Servlet>>` to store servlets. This maps `HTTP Method` -> `URI` -> `Servlet`.
- **Routing Algorithm**:
    - First, it attempts an **Exact Match** for the URI.
    - If that fails, it uses **Longest Prefix Matching**. This means if you register `/api` and `/api/v1`, a request to `/api/v1/users` will correctly route to `/api/v1`.

## 4. Usage

### Starting the Server
```java
// Create a server on port 8080 with a pool of 10 threads
HTTPServer server = new MyHTTPServer(8080, 10);
server.start();
```

### Registering Servlets
```java
// Register a simple GET handler
server.addServlet("GET", "/api/greet", (req, out) -> {
    String name = req.getParameters().getOrDefault("name", "World");
    String response = "Hello, " + name;
    
    // Write standard HTTP response
    out.write(("HTTP/1.1 200 OK\r\n\r\n" + response).getBytes());
});
```

### Stopping the Server
```java
server.close(); // Gracefully shuts down socket and thread pool
```

