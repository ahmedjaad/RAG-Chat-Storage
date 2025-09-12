package com.rag.chatstorage.web;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import com.rag.chatstorage.service.ChatSessionService;
import com.rag.chatstorage.web.dto.SessionDtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Manage chat sessions and messages")
public class SessionController {

    private final ChatSessionService service;

    public SessionController(ChatSessionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create session",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateSessionRequest.class),
                            examples = @ExampleObject(value = "{\n  \"userId\": \"u1\",\n  \"title\": \"My chat\"\n}"))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SessionResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public SessionResponse create(@Valid @RequestBody CreateSessionRequest req) {
        ChatSession s = service.createSession(req.userId(), req.title());
        return SessionResponse.from(s);
    }

    @GetMapping
    @Operation(
            summary = "List sessions (paged)",
            description = "List user's sessions ordered by updatedAt desc. Supports favorites filter and title search.",
            parameters = {
                    @Parameter(name = "userId", description = "User ID", required = true),
                    @Parameter(name = "favorite", description = "Filter by favorite"),
                    @Parameter(name = "q", description = "Title contains (case-insensitive)"),
                    @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
                    @Parameter(name = "size", description = "Page size", example = "20")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = PagedSessions.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public PagedSessions list(@RequestParam String userId,
                              @RequestParam(required = false) Boolean favorite,
                              @RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        var p = service.pageSessions(userId, favorite, q, page, size);
        return new PagedSessions(
                p.getContent().stream().map(SessionResponse::from).collect(Collectors.toList()),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages()
        );
    }

    @PatchMapping("/{id}/title")
    @Operation(
            summary = "Rename session",
            parameters = { @Parameter(name = "id", description = "Session ID", required = true) },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RenameSessionRequest.class),
                            examples = @ExampleObject(value = "{\n  \"title\": \"New title\"\n}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SessionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public SessionResponse rename(@PathVariable Long id, @Valid @RequestBody RenameSessionRequest req) {
        return SessionResponse.from(service.rename(id, req.title()));
    }

    @PatchMapping("/{id}/favorite")
    @Operation(
            summary = "Mark/unmark favorite",
            parameters = { @Parameter(name = "id", description = "Session ID", required = true) },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FavoriteRequest.class),
                            examples = @ExampleObject(value = "{\n  \"favorite\": true\n}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = SessionResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public SessionResponse favorite(@PathVariable Long id, @Valid @RequestBody FavoriteRequest req) {
        return SessionResponse.from(service.favorite(id, req.favorite()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete session",
            parameters = { @Parameter(name = "id", description = "Session ID", required = true) },
            responses = {
                    @ApiResponse(responseCode = "204", description = "No Content"),
                    @ApiResponse(responseCode = "404", description = "Not Found",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Add message",
            parameters = { @Parameter(name = "id", description = "Session ID", required = true) },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AddMessageRequest.class),
                            examples = @ExampleObject(value = "{\n  \"sender\": \"USER\",\n  \"content\": \"Hello\",\n  \"context\": \"optional\"\n}"))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = MessageResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "404", description = "Not Found",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public MessageResponse addMessage(@PathVariable Long id, @Valid @RequestBody AddMessageRequest req) {
        ChatMessage m = service.addMessage(id, req.sender(), req.content(), req.context());
        return MessageResponse.from(m);
    }

    @GetMapping("/{id}/messages")
    @Operation(
            summary = "List messages (paged)",
            parameters = {
                    @Parameter(name = "id", description = "Session ID", required = true),
                    @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
                    @Parameter(name = "size", description = "Page size", example = "20")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = PagedMessages.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
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
