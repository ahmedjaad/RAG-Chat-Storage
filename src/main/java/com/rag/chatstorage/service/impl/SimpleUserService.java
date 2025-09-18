package com.rag.chatstorage.service.impl;

import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.repository.UserRepository;
import com.rag.chatstorage.service.UserService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SimpleUserService implements UserService {

    private final UserRepository userRepository;

    public SimpleUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User ensureUser(String userId) {
        return userRepository.findByUserId(userId)
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
