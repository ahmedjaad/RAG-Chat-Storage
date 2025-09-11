package com.rag.chatstorage.web;

import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@Validated
public class AiController {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public AiController(ChatClient chatClient, EmbeddingModel embeddingModel) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
    }

    public record InferRequest(@NotBlank String prompt, String system) {}
    public record InferResponse(String content, Map<String, Object> metadata) {}

    @PostMapping("/infer")
    @ResponseStatus(HttpStatus.OK)
    public InferResponse infer(@RequestBody InferRequest request) {
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
    public EmbeddingsResponse embeddings(@RequestBody EmbeddingsRequest request) {
        if (request.inputs() == null || request.inputs().isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        EmbeddingResponse resp = embeddingModel.embedForResponse(request.inputs());
        List<EmbeddingVector> vectors = resp.getResults().stream()
                .map(r -> new EmbeddingVector(r.getOutput()))
                .toList();
        Integer dims = vectors.isEmpty() ? 0 : vectors.get(0).vector().length;
        return new EmbeddingsResponse(vectors, dims);
    }
}
