# RAG Chat UI (separate project)

A lightweight Spring Boot Thymeleaf UI that consumes the RAG Chat Storage backend REST APIs.

- Starts on port 8081 by default
- Talks to backend at BACKEND_BASE_URL (default http://localhost:8080)
- Sends X-API-KEY header from API_KEY env var

Run locally:

mvn -q -f ui-app/pom.xml spring-boot:run -Dspring-boot.run.arguments=--BACKEND_BASE_URL=http://localhost:8080

Build Jar:

mvn -q -f ui-app/pom.xml clean package

Run container:

docker build -t rag-chat-ui:local ui-app
Docker run:

docker run -e BACKEND_BASE_URL=http://host.docker.internal:8080 -e API_KEY=changeme -p 8081:8081 rag-chat-ui:local
