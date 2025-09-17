package com.rag.chatstorage.web;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@Validated
@Tag(name = "AI", description = "AI inference and embeddings via Spring AI/OpenAI")
@Hidden
public class AiController {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public AiController(org.springframework.beans.factory.ObjectProvider<ChatClient> chatClientProvider,
                        org.springframework.beans.factory.ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.chatClient = chatClientProvider.getIfAvailable();
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
    }

    public record InferRequest(@NotBlank String prompt, String system) {}
    public record InferResponse(String content, Map<String, Object> metadata) {}

    @PostMapping("/infer")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Chat inference",
            description = "Send a prompt (and optional system instruction) to the configured chat model and return the assistant reply.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = InferRequest.class),
                            examples = @ExampleObject(value = "{\n  \"prompt\": \"Hello\",\n  \"system\": \"You are a helpful assistant.\"\n}"))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = InferResponse.class),
                                    examples = @ExampleObject(value = "{\n  \"content\": \"Hi! How can I help you today?\",\n  \"metadata\": {}\n}"))
                    ),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - missing/invalid API key",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "429", description = "Too Many Requests",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "503", description = "AI Service Unavailable",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public InferResponse infer(@RequestBody InferRequest request) {
        if (chatClient == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No AI chat provider is configured. Enable a provider profile (e.g. openai, ollama)."
            );
        }
        var prompt = chatClient.prompt();
        if (request.system() != null && !request.system().isBlank()) {
            prompt = prompt.system(request.system());
        }
        var result = prompt.user(request.prompt()).call();
        String content = result.content();
        return new InferResponse(content, Map.of());
    }

    public record EmbeddingsRequest(List<@NotBlank String> inputs) {}
    public record EmbeddingVector(float[] vector) {}
    public record EmbeddingsResponse(List<EmbeddingVector> data, Integer dimensions) {}

    @PostMapping("/embeddings")
    @ResponseStatus(HttpStatus.OK)
    @Operation(
            summary = "Create embeddings",
            description = "Generate vector embeddings for a list of input strings using the configured embedding model.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EmbeddingsRequest.class),
                            examples = @ExampleObject(value = "{\n  \"inputs\": [\"first text\", \"second text\"]\n}"))
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = EmbeddingsResponse.class))) ,
                    @ApiResponse(responseCode = "400", description = "Bad Request",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "429", description = "Too Many Requests",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public EmbeddingsResponse embeddings(@RequestBody EmbeddingsRequest request) {
        if (request.inputs() == null || request.inputs().isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        if (embeddingModel == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No AI embedding provider is configured. Enable a provider profile (e.g. openai, ollama)."
            );
        }
        EmbeddingResponse resp = embeddingModel.embedForResponse(request.inputs());
        List<EmbeddingVector> vectors = resp.getResults().stream()
                .map(r -> new EmbeddingVector(r.getOutput()))
                .toList();
        Integer dims = vectors.isEmpty() ? 0 : vectors.getFirst().vector().length;
        return new EmbeddingsResponse(vectors, dims);
    }
}
