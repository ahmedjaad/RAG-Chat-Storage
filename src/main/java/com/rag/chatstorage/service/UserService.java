package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User ensureUser(String userId) {
        return userRepository.findById(userId)
                .orElseGet(() -> {
                    User u = new User();
                    u.setUserId(userId);
                    Instant now = Instant.now();
                    u.setCreatedAt(now);
                    u.setUpdatedAt(now);
                    return userRepository.save(u);
                });
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }
}
