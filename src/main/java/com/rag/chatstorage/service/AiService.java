package com.rag.chatstorage.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiService {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    public AiService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String infer(String system, String user) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("OpenAI API key is not configured. Set OPENAI_API_KEY in your .env.");
        }
        var prompt = chatClient.prompt();
        if (StringUtils.hasText(system)) {
            prompt = prompt.system(system);
        }
        return prompt.user(user).call().content();
    }
}
