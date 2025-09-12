package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    void ensureUser_returnsExisting() {
        User existing = new User();
        existing.setUserId("u1");
        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));

        User result = userService.ensureUser("u1");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void ensureUser_createsWhenMissing() {
        when(userRepository.findById("u2")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.ensureUser("u2");

        assertThat(result.getUserId()).isEqualTo("u2");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("u2");
    }

    @Test
    void listUsers_delegatesToRepo() {
        when(userRepository.findAll()).thenReturn(List.of(new User()));
        assertThat(userService.listUsers()).hasSize(1);
        verify(userRepository).findAll();
    }
}
