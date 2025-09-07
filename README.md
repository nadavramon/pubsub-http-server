# Pub/Sub Computational Graph & Custom HTTP Server (Java)

Small project implementing a thread-safe publisher/subscriber computational graph (agents/topics) and a lightweight socket-based HTTP server with servlet-style handlers.

## Features
- Pub/Sub framework: topics, immutable Messages, agents that subscribe/publish and form directed computational graphs.
- Custom HTTP server: raw-socket `RequestParser`, longest-prefix URI routing, `Servlet` interface (GET/POST support).
- Concurrency: fixed thread-pool, graceful shutdown, careful resource management.
- Self-tests included: request parsing and end-to-end servlet behavior.

## Quick run
Compile and run with plain `javac`/`java` (example from project root):

```bash
# compile
javac -d out $(find . -name "*.java")

# run tests (example)
java -cp out test.MainTrain
