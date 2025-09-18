package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.User;
import java.util.List;

public interface UserService {
    User ensureUser(String userId);
    List<User> listUsers();
}
