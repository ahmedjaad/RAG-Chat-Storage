package com.rag.chatstorage.web;

import com.rag.chatstorage.service.DocumentService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/docs")
@Validated
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    public record UpsertRequest(String userId, @NotBlank String text, String metadata) {}
    public record UpsertResponse(Long id, Integer dimensions) {}

    public record SearchRequest(@NotBlank String query, String userId, Integer topK) {}
    public record SearchResponse(List<DocumentService.SearchMatch> matches, Integer total) {}

    @PostMapping("/upsert")
    @ResponseStatus(HttpStatus.OK)
    public UpsertResponse upsert(@RequestBody UpsertRequest req) {
        var res = service.upsert(new DocumentService.UpsertRequest(req.userId(), req.text(), req.metadata()));
        return new UpsertResponse(res.id(), res.dimensions());
    }

    @PostMapping("/search")
    @ResponseStatus(HttpStatus.OK)
    public SearchResponse search(@RequestBody SearchRequest req) {
        int k = req.topK() == null ? 5 : req.topK();
        var list = service.search(req.query(), req.userId(), k);
        return new SearchResponse(list, list.size());
    }
}
