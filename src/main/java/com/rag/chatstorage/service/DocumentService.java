package com.rag.chatstorage.service;

import java.util.List;

public interface DocumentService {
    UpsertResponse upsert(UpsertRequest req);
    List<SearchMatch> search(String query, String userId, int topK);

    record UpsertRequest(String userId, String text, String metadata) {}
    record UpsertResponse(Long id, Integer dimensions) {}
    record SearchMatch(Long id, String userId, String text, String metadata, double score) {}
}
