package com.rag.chatstorage.repository;

import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.domain.ChatSession;
import com.rag.chatstorage.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class ChatSessionRepositoryTest {

    @Autowired
    private ChatSessionRepository sessions;
    @Autowired
    private ChatMessageRepository messages;
    @Autowired
    private UserRepository users;

    @Test
    void orderingAndFiltersWork() {
        // user
        User u = new User(); u.setUserId("u"); users.save(u);
        // session 1 (older, favorite=false)
        ChatSession s1 = new ChatSession(); s1.setUser(u); s1.setTitle("Alpha"); s1.setFavorite(false);
        s1.setCreatedAt(OffsetDateTime.now().minusHours(2));
        s1.setUpdatedAt(OffsetDateTime.now().minusHours(2));
        sessions.save(s1);
        // session 2 (newer, favorite=true)
        ChatSession s2 = new ChatSession(); s2.setUser(u); s2.setTitle("Beta"); s2.setFavorite(true);
        s2.setCreatedAt(OffsetDateTime.now().minusHours(1));
        s2.setUpdatedAt(OffsetDateTime.now().minusHours(1));
        sessions.save(s2);

        var all = sessions.findByUser_UserIdOrderByUpdatedAtDesc("u");
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getTitle()).isEqualTo("Beta");

        var fav = sessions.findByUser_UserIdAndFavoriteOrderByUpdatedAtDesc("u", true);
        assertThat(fav).hasSize(1);
        assertThat(fav.get(0).getTitle()).isEqualTo("Beta");

        var page = sessions.findByUser_UserIdOrderByUpdatedAtDesc("u", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);

        var search = sessions.findByUser_UserIdAndTitleContainingIgnoreCaseOrderByUpdatedAtDesc("u", "alp", PageRequest.of(0, 10));
        assertThat(search.getTotalElements()).isEqualTo(1);
        assertThat(search.getContent().get(0).getTitle()).isEqualTo("Alpha");
    }

    @Test
    void messagesOrderedByCreatedAt() {
        User u = new User(); u.setUserId("u2"); users.save(u);
        ChatSession s = new ChatSession(); s.setUser(u); s.setTitle("S");
        sessions.save(s);
        ChatMessage m1 = new ChatMessage(); m1.setSession(s); m1.setSender(ChatMessage.Sender.USER); m1.setContent("a"); messages.save(m1);
        ChatMessage m2 = new ChatMessage(); m2.setSession(s); m2.setSender(ChatMessage.Sender.USER); m2.setContent("b"); messages.save(m2);

        var page = messages.findBySession_Id(s.getId(), PageRequest.of(0, 10));
        assertThat(page.getContent().get(0).getCreatedAt()).isBeforeOrEqualTo(page.getContent().get(1).getCreatedAt());
    }
}
