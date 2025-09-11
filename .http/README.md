# HTTP Requests Collection (.http)

This directory contains IntelliJ HTTP Client request files for exercising the API.

Environment placeholders used in files:
- {{HOST}}: base URL (default you can use http://localhost:8080)
- {{API_KEY}}: your API key value for X-API-KEY header
- {{SESSION_ID}}: a session ID to target message-related endpoints

Usage (IntelliJ IDEA):
- Open any .http file and click the gutter "Run" icons next to each request.
- Define environment variables by adding a http-client.env.json at project root or by replacing placeholders inline.

Quick inline usage example:
- Set at top of file or in environment: 
  @HOST = http://localhost:8080
  @API_KEY = replace-with-a-secure-random-string

Files:
- health.http – root, actuator health/liveness/readiness, docs
- sessions.http – create/list/rename/favorite/delete sessions
- messages.http – add and list messages for a session
- ai.http – inference and embeddings endpoints (requires OpenAI env vars configured)

Note: The .http directory contents are git-ignored except for this README, the .keep file, and the sample env file http-client.env.json.sample.

401 tips when using these requests:
- Set @API_KEY at the top of the file or via http-client.env.json. The header name is X-API-KEY by default.
- The server will reply 401 with a hint that includes the expected header name if the key is missing/incorrect.
