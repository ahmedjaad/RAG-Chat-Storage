package com.rag.chatstorage.web;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@Validated
public class AiController {

    private final ChatClient chatClient;
    private final EmbeddingClient embeddingClient;

    public AiController(ChatClient chatClient, EmbeddingClient embeddingClient) {
        this.chatClient = chatClient;
        this.embeddingClient = embeddingClient;
    }

    public record InferRequest(@NotBlank String prompt, String system) {}
    public record InferResponse(String content, Map<String, Object> metadata) {}

    @PostMapping("/infer")
    @ResponseStatus(HttpStatus.OK)
    public InferResponse infer(@RequestBody InferRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(new SystemMessage(request.system()));
        }
        messages.add(new UserMessage(request.prompt()));
        ChatResponse response = chatClient.call(new Prompt(messages));
        String content = response.getResult().getOutput().getContent();
        return new InferResponse(content, response.getMetadata());
    }

    public record EmbeddingsRequest(List<@NotBlank String> inputs) {}
    public record EmbeddingVector(List<Double> vector) {}
    public record EmbeddingsResponse(List<EmbeddingVector> data, Integer dimensions) {}

    @PostMapping("/embeddings")
    @ResponseStatus(HttpStatus.OK)
    public EmbeddingsResponse embeddings(@RequestBody EmbeddingsRequest request) {
        if (request.inputs() == null || request.inputs().isEmpty()) {
            throw new IllegalArgumentException("inputs must not be empty");
        }
        EmbeddingResponse resp = embeddingClient.embedForResponse(new EmbeddingRequest(request.inputs()));
        List<EmbeddingVector> vectors = resp.getResults().stream()
                .map(r -> new EmbeddingVector(r.getOutput().getEmbedding()))
                .toList();
        Integer dims = vectors.isEmpty() ? 0 : vectors.get(0).vector().size();
        return new EmbeddingsResponse(vectors, dims);
    }
}
