package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.repository.ChatMessageRepository;
import com.rag.chatstorage.repository.ChatSessionRepository;
import com.rag.chatstorage.service.impl.SimpleChatSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ChatSessionServiceTest {

    private ChatSessionRepository sessionRepository;
    private ChatMessageRepository messageRepository;
    private UserService userService;
    private ChatSessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(ChatSessionRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        userService = mock(UserService.class);
        service = new SimpleChatSessionService(sessionRepository, messageRepository, userService);
    }

    @Test
    void createSession_ensuresUser_andSaves() {
        when(userService.ensureUser("u1")).thenReturn(user("u1"));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatSession s = service.createSession("u1", "Title");

        assertThat(s.getUser().getUserId()).isEqualTo("u1");
        assertThat(s.getTitle()).isEqualTo("Title");
        verify(sessionRepository).save(any(ChatSession.class));
    }

    @Test
    void getSessionOrThrow_notFound() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getSessionOrThrow(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void rename_updatesTitle() {
        ChatSession s = new ChatSession(); s.setId(1L); s.setTitle("Old");
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(s));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatSession out = service.rename(1L, "New");
        assertThat(out.getTitle()).isEqualTo("New");
    }

    @Test
    void favorite_updatesFlag() {
        ChatSession s = new ChatSession(); s.setId(1L); s.setFavorite(false);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(s));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatSession out = service.favorite(1L, true);
        assertThat(out.isFavorite()).isTrue();
    }

    @Test
    void addMessage_capsContextLength() {
        ChatSession s = new ChatSession(); s.setId(5L);
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(s));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        String longCtx = "x".repeat(25000);
        ChatMessage m = service.addMessage(5L, ChatMessage.Sender.USER, "hi", longCtx);
        assertThat(m.getContext().length()).isEqualTo(20000);
    }

    @Test
    void pageSessions_routesByFilters() {
        when(sessionRepository.findByUser_UserIdOrderByUpdatedAtDesc(eq("u"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(sessionRepository.findByUser_UserIdAndFavoriteOrderByUpdatedAtDesc(eq("u"), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(sessionRepository.findByUser_UserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(eq("u"), eq("q"), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(sessionRepository.findByUser_UserIdAndFavoriteAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(eq("u"), eq(true), eq("q"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        Page<?> p1 = service.pageSessions("u", null, null, 0, 10);
        Page<?> p2 = service.pageSessions("u", true, null, 0, 10);
        Page<?> p3 = service.pageSessions("u", null, "q", 0, 10);
        Page<?> p4 = service.pageSessions("u", true, "q", 0, 10);

        assertThat(p1.getContent()).isEmpty();
        assertThat(p2.getContent()).isEmpty();
        assertThat(p3.getContent()).isEmpty();
        assertThat(p4.getContent()).isEmpty();
        verify(sessionRepository).findByUser_UserIdOrderByUpdatedAtDesc(eq("u"), any(PageRequest.class));
        verify(sessionRepository).findByUser_UserIdAndFavoriteOrderByUpdatedAtDesc(eq("u"), eq(true), any(PageRequest.class));
        verify(sessionRepository).findByUser_UserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(eq("u"), eq("q"), any(PageRequest.class));
        verify(sessionRepository).findByUser_UserIdAndFavoriteAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc(eq("u"), eq(true), eq("q"), any(PageRequest.class));
    }

    private User user(String id) {
        User u = new User();
        u.setUserId(id);
        return u;
    }
}
