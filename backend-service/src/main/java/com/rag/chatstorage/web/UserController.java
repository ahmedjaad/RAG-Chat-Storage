package com.rag.chatstorage.web;

import com.rag.chatstorage.domain.User;
import com.rag.chatstorage.service.UserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Manage users (ensure/list)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public record CreateUserRequest(@NotBlank String userId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Ensure user",
            description = "Create the user if it does not exist, otherwise return the existing user.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateUserRequest.class),
                            examples = @ExampleObject(value = "{\n  \"userId\": \"u1\"\n}"))
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = User.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    public User create(@RequestBody CreateUserRequest req) {
        // ensure behavior: returns existing or creates new
        return userService.ensureUser(req.userId());
    }

    @GetMapping
    @Operation(
            summary = "List users",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json"))
            }
    )
    public List<User> list() {
        return userService.listUsers();
    }
}
