package com.rag.chatstorage.web;

import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.service.UserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record CreateUserRequest(@NotBlank String userId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public User create(@RequestBody CreateUserRequest req) {
        // ensure behavior: returns existing or creates new
        return userService.ensureUser(req.userId());
    }

    @GetMapping
    public List<User> list() {
        return userService.listUsers();
    }
}
