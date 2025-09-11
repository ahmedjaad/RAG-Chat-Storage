package com.rag.chatstorage.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsRedirectController {

    // Redirect /docs to the Swagger UI index
    @GetMapping("/docs")
    public String docs() {
        return "redirect:/swagger-ui/index.html";
    }
}
