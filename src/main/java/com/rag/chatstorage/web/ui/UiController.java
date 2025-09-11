package com.rag.chatstorage.web.ui;

import com.rag.chatstorage.service.ChatSessionService;
import com.rag.chatstorage.web.dto.SessionDtos;
import com.rag.chatstorage.web.dto.SessionDtos.MessageResponse;
import com.rag.chatstorage.web.dto.SessionDtos.PagedMessages;
import com.rag.chatstorage.web.dto.SessionDtos.SessionResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui")
public class UiController {

    private final ChatSessionService service;
    private final com.rag.chatstorage.service.AiService aiService;

    public UiController(ChatSessionService service, com.rag.chatstorage.service.AiService aiService) {
        this.service = service;
        this.aiService = aiService;
    }

    @GetMapping
    public String index() {
        return "redirect:/ui/sessions?userId=demo";
    }

    @GetMapping("/sessions")
    public String listSessions(@RequestParam(required = false) String userId, Model model) {
        if (userId == null || userId.isBlank()) {
            userId = "demo"; // default for convenience
        }
        List<SessionResponse> sessions = service.listSessions(userId).stream()
                .map(SessionResponse::from)
                .collect(Collectors.toList());
        model.addAttribute("userId", userId);
        model.addAttribute("sessions", sessions);
        return "sessions";
    }

    @PostMapping("/sessions")
    public String create(@RequestParam String userId, @RequestParam(required = false) String title) {
        service.createSession(userId, title);
        return "redirect:/ui/sessions?userId=" + userId;
        
    }

    @GetMapping("/sessions/{id}")
    public String view(@PathVariable Long id,
                       @RequestParam(required = false) String userId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        // If userId is missing (e.g., malformed URL), derive it from the session
        if (userId == null || userId.isBlank()) {
            var s = service.getSessionOrThrow(id);
            userId = s.getUserId();
        }
        // Load sidebar sessions for ChatGPT-like layout
        List<SessionResponse> sessions = service.listSessions(userId).stream()
                .map(SessionResponse::from)
                .collect(Collectors.toList());
        model.addAttribute("sessions", sessions);

        Page<com.rag.chatstorage.domain.ChatMessage> p = service.getMessages(id, page, size);
        PagedMessages pm = new PagedMessages(
                p.getContent().stream().map(MessageResponse::from).collect(Collectors.toList()),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()
        );
        model.addAttribute("userId", userId);
        model.addAttribute("sessionId", id);
        model.addAttribute("messages", pm);
        return "session";
    }

    @PostMapping("/sessions/{id}/messages")
    public String postMessage(@PathVariable Long id, @RequestParam String userId,
                              @RequestParam("sender") com.rag.chatstorage.domain.ChatMessage.Sender sender,
                              @RequestParam("content") String content,
                              @RequestParam(value = "context", required = false) String context,
                              org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        // Save user message first
        service.addMessage(id, sender, content, context);
        // If the sender is USER, try to get an assistant reply via Spring AI
        if (sender == com.rag.chatstorage.domain.ChatMessage.Sender.USER) {
            try {
                String system = "You are a helpful AI assistant."; // simple default system prompt
                String reply = aiService.infer(system, content);
                service.addMessage(id, com.rag.chatstorage.domain.ChatMessage.Sender.ASSISTANT, reply, null);
            } catch (Exception e) {
                ra.addFlashAttribute("uiError", "AI reply failed: " + e.getMessage());
            }
        }
        return "redirect:/ui/sessions/" + id + "?userId=" + userId;
    }

    @PostMapping("/sessions/{id}/title")
    public String rename(@PathVariable Long id, @RequestParam String userId, @RequestParam String title) {
        service.rename(id, title);
        return "redirect:/ui/sessions?userId=" + userId;
    }

    @PostMapping("/sessions/{id}/favorite")
    public String favorite(@PathVariable Long id, @RequestParam String userId, @RequestParam boolean favorite) {
        service.favorite(id, favorite);
        return "redirect:/ui/sessions?userId=" + userId;
    }

    @PostMapping("/sessions/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam String userId) {
        service.delete(id);
        return "redirect:/ui/sessions?userId=" + userId;
    }
}
