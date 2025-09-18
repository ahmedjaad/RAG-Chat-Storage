package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.ChatMessage;
import java.util.List;

public interface AiService {

    String infer(String system, String user);
    String inferWithHistory(String user, String system, List<ChatMessage> history);

    class AiFriendlyException extends RuntimeException {
        private final String code;
        private final String hint;
        public AiFriendlyException(String code, String message, String hint) {
            super(message);
            this.code = code;
            this.hint = hint;
        }
        public String getCode() { return code; }
        public String getHint() { return hint; }
    }
}
