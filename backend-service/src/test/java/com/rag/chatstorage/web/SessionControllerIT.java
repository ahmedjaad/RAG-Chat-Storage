package com.rag.chatstorage.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.chatstorage.domain.ChatMessage;
import com.rag.chatstorage.web.dto.SessionDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class SessionControllerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    private String apiKey = "test";

    @BeforeEach
    void setup() {}

    @Test
    void unauthorizedWithoutApiKey() throws Exception {
        mvc.perform(get("/api/v1/sessions").param("userId","u1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createListRenameFavoriteDeleteFlow() throws Exception {
        // ensure user
        mvc.perform(post("/api/v1/users").header("X-API-KEY", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u1\"}"))
                .andExpect(status().isCreated());

        // create session
        var create = new SessionDtos.CreateSessionRequest("u1", "My chat");
        String sessionJson = mvc.perform(post("/api/v1/sessions").header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("My chat"))
                .andReturn().getResponse().getContentAsString();
        Long id = om.readTree(sessionJson).get("id").asLong();

        // add message
        var add = new SessionDtos.AddMessageRequest(ChatMessage.Sender.USER, "hello", null);
        mvc.perform(post("/api/v1/sessions/"+id+"/messages").header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(add)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(id));

        // list messages
        mvc.perform(get("/api/v1/sessions/"+id+"/messages").header("X-API-KEY", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("hello"));

        // list sessions paged
        mvc.perform(get("/api/v1/sessions").header("X-API-KEY", apiKey)
                        .param("userId","u1").param("page","0").param("size","10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));

        // rename
        mvc.perform(patch("/api/v1/sessions/"+id+"/title").header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New title"));

        // favorite
        mvc.perform(patch("/api/v1/sessions/"+id+"/favorite").header("X-API-KEY", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favorite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        // delete
        mvc.perform(delete("/api/v1/sessions/"+id).header("X-API-KEY", apiKey))
                .andExpect(status().isNoContent());

        // 404 after delete
        mvc.perform(get("/api/v1/sessions/"+id+"/messages").header("X-API-KEY", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }
}
