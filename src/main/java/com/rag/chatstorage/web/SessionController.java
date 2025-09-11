package com.rag.chatstorage.web;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import com.rag.chatstorage.service.ChatSessionService;
import com.rag.chatstorage.web.dto.SessionDtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final ChatSessionService service;

    public SessionController(ChatSessionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest req) {
        ChatSession s = service.createSession(req.userId(), req.title());
        return SessionResponse.from(s);
    }

    @GetMapping
    public List<SessionResponse> list(@RequestParam String userId,
                                      @RequestParam(required = false) Boolean favorite) {
        return service.listSessions(userId, favorite).stream().map(SessionResponse::from).collect(Collectors.toList());
    }

    @PatchMapping("/{id}/title")
    public SessionResponse rename(@PathVariable Long id, @Valid @RequestBody RenameSessionRequest req) {
        return SessionResponse.from(service.rename(id, req.title()));
    }

    @PatchMapping("/{id}/favorite")
    public SessionResponse favorite(@PathVariable Long id, @Valid @RequestBody FavoriteRequest req) {
        return SessionResponse.from(service.favorite(id, req.favorite()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse addMessage(@PathVariable Long id, @Valid @RequestBody AddMessageRequest req) {
        ChatMessage m = service.addMessage(id, req.sender(), req.content(), req.context());
        return MessageResponse.from(m);
    }

    @GetMapping("/{id}/messages")
    public PagedMessages getMessages(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        Page<ChatMessage> p = service.getMessages(id, page, size);
        return new PagedMessages(
                p.getContent().stream().map(MessageResponse::from).collect(Collectors.toList()),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()
        );
    }
}
