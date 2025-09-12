package com.rag.chatstorage.ui.controller;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ui")
public class UiController {

    private final WebClient backend;

    public UiController(WebClient backend) {
        this.backend = backend;
    }

    @GetMapping
    public String index() {
        return "redirect:/ui/sessions?userId=demo";
    }

    @GetMapping("/sessions")
    public String listSessions(@RequestParam(required = false) String userId,
                               @RequestParam(required = false) Boolean favorite,
                               Model model) {
        String effectiveUserId = (userId == null || userId.isBlank()) ? "demo" : userId;
        // Ensure user
        backend.post().uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userId", effectiveUserId))
                .retrieve().toBodilessEntity().block();
        var sessions = backend.get().uri(uriBuilder -> uriBuilder.path("/api/v1/sessions")
                        .queryParam("userId", effectiveUserId)
                        .queryParamIfPresent("favorite", java.util.Optional.ofNullable(favorite))
                        .build())
                .retrieve().bodyToMono(List.class).block();
        model.addAttribute("userId", effectiveUserId);
        model.addAttribute("favorite", favorite);
        model.addAttribute("sessions", sessions);
        return "sessions";
    }

    @PostMapping("/sessions")
    public String create(@RequestParam String userId, @RequestParam(required = false) String title) {
        var session = backend.post().uri("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userId", userId, "title", title))
                .retrieve().bodyToMono(Map.class).block();
        var id = ((Number) session.get("id")).longValue();
        return "redirect:/ui/sessions/" + id + "?userId=" + userId;
    }

    @GetMapping("/sessions/{id}")
    public String view(@PathVariable Long id,
                       @RequestParam(required = false) String userId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        String effectiveUserId2 = userId;
        if (effectiveUserId2 == null || effectiveUserId2.isBlank()) {
            var s = backend.get().uri("/api/v1/sessions/{id}", id)
                    .retrieve().bodyToMono(Map.class).block();
            effectiveUserId2 = (String) ((Map<?,?>) s.get("user")).get("userId");
        }
        var current = backend.get().uri("/api/v1/sessions/{id}", id)
                .retrieve().bodyToMono(Map.class).block();
        final String finalUserId = effectiveUserId2;
        var sessions = backend.get().uri(uriBuilder -> uriBuilder.path("/api/v1/sessions")
                        .queryParam("userId", finalUserId)
                        .build())
                .retrieve().bodyToMono(List.class).block();
        var messagesPage = backend.get().uri(uriBuilder -> uriBuilder.path("/api/v1/sessions/{id}/messages")
                        .queryParam("page", page).queryParam("size", size).build(id))
                .retrieve().bodyToMono(Map.class).block();
        model.addAttribute("sessions", sessions);
        model.addAttribute("current", current);
        model.addAttribute("userId", finalUserId);
        model.addAttribute("sessionId", id);
        model.addAttribute("messages", messagesPage);
        return "session";
    }

    @PostMapping("/sessions/{id}/messages")
    public String postMessage(@PathVariable Long id, @RequestParam String userId,
                              @RequestParam("sender") String sender,
                              @RequestParam("content") String content,
                              @RequestParam(value = "context", required = false) String context) {
        backend.post().uri("/api/v1/sessions/{id}/messages", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("sender", sender, "content", content, "context", context))
                .retrieve().toBodilessEntity().block();
        // Also call AI for assistant reply
        try {
            var aiResp = backend.post().uri("/api/v1/ai/infer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("prompt", content, "system", "You are a helpful AI assistant."))
                    .retrieve().bodyToMono(Map.class).block();
            String reply = (String) aiResp.get("content");
            backend.post().uri("/api/v1/sessions/{id}/messages", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("sender", "ASSISTANT", "content", reply))
                    .retrieve().toBodilessEntity().block();
        } catch (Exception ignored) {}
        return "redirect:/ui/sessions/" + id + "?userId=" + userId;
    }

    @PostMapping("/sessions/{id}/title")
    public String rename(@PathVariable Long id, @RequestParam String userId, @RequestParam String title) {
        backend.patch().uri("/api/v1/sessions/{id}/title", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", title))
                .retrieve().toBodilessEntity().block();
        return "redirect:/ui/sessions?userId=" + userId;
    }

    @PostMapping("/sessions/{id}/favorite")
    public String favorite(@PathVariable Long id, @RequestParam String userId, @RequestParam boolean favorite) {
        backend.patch().uri("/api/v1/sessions/{id}/favorite", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("favorite", favorite))
                .retrieve().toBodilessEntity().block();
        return "redirect:/ui/sessions?userId=" + userId;
    }

    @PostMapping("/sessions/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam String userId) {
        backend.delete().uri("/api/v1/sessions/{id}", id)
                .retrieve().toBodilessEntity().block();
        return "redirect:/ui/sessions?userId=" + userId;
    }
}
