package com.rag.chatstorage.service;

import com.rag.chatstorage.domain.Document;
import com.rag.chatstorage.repository.DocumentRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DocumentService {

    private final DocumentRepository repo;
    private final EmbeddingModel embeddingModel;

    public DocumentService(DocumentRepository repo, org.springframework.beans.factory.ObjectProvider<EmbeddingModel> embeddingModelProvider,
                           org.springframework.core.env.Environment env) {
        this.repo = repo;
        this.embeddingModel = chooseEmbeddingModel(embeddingModelProvider, env);
    }

    private EmbeddingModel chooseEmbeddingModel(org.springframework.beans.factory.ObjectProvider<EmbeddingModel> provider,
                                                org.springframework.core.env.Environment env) {
        // If only one bean, return it
        List<EmbeddingModel> all = provider.stream().toList();
        if (all.isEmpty()) return null;
        if (all.size() == 1) return all.getFirst();
        // Prefer by active profile
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        // Priority order depending on common profiles
        if (profiles.contains("openai") || profiles.contains("openai-compatible")) {
            for (EmbeddingModel m : all) if (m.getClass().getName().toLowerCase().contains("openai")) return m;
        }
        if (profiles.contains("ollama")) {
            for (EmbeddingModel m : all) if (m.getClass().getName().toLowerCase().contains("ollama")) return m;
        }
        // Fallback priority: OpenAI > Ollama
        for (EmbeddingModel m : all) if (m.getClass().getName().toLowerCase().contains("openai")) return m;
        for (EmbeddingModel m : all) if (m.getClass().getName().toLowerCase().contains("ollama")) return m;
        return all.getFirst();
    }

    public record UpsertRequest(String userId, String text, String metadata) {}
    public record UpsertResponse(Long id, Integer dimensions) {}

    public record SearchMatch(Long id, String userId, String text, String metadata, double score) {}

    @Transactional
    public UpsertResponse upsert(UpsertRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            throw new IllegalArgumentException("text must not be empty");
        }
        if (embeddingModel == null) {
            throw new IllegalStateException("No AI embedding provider is configured. Enable a provider profile (e.g. openai, ollama).");
        }
        String userId = (req.userId() == null || req.userId().isBlank()) ? "public" : req.userId();
        EmbeddingResponse er = embeddingModel.embedForResponse(List.of(req.text()));
        float[] vec = er.getResults().getFirst().getOutput();
        String encoded = encode(vec);
        int dims = vec.length;
        Document d = new Document(userId, req.text(), req.metadata(), encoded, dims);
        repo.save(d);
        return new UpsertResponse(d.getId(), dims);
    }

    public List<SearchMatch> search(String query, String userId, int topK) {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be empty");
        if (embeddingModel == null) {
            throw new IllegalStateException("No AI embedding provider is configured. Enable a provider profile (e.g. openai, ollama).");
        }
        if (topK <= 0) topK = 5;
        EmbeddingResponse er = embeddingModel.embedForResponse(List.of(query));
        float[] q = er.getResults().getFirst().getOutput();
        List<Document> corpus = (userId == null || userId.isBlank()) ? repo.findAll() : repo.findByUserId(userId);
        // compute cosine similarity
        List<SearchMatch> matches = new ArrayList<>();
        for (Document d : corpus) {
            float[] v = decode(d.getVector());
            double score = cosine(q, v);
            matches.add(new SearchMatch(d.getId(), d.getUserId(), d.getText(), d.getMetadata(), score));
        }
        matches.sort(Comparator.comparingDouble(SearchMatch::score).reversed());
        if (matches.size() > topK) return matches.subList(0, topK);
        return matches;
    }

    private static String encode(float[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        return sb.toString();
    }
    private static float[] decode(String s) {
        if (s == null || s.isBlank()) return new float[0];
        String[] parts = s.split(",");
        float[] arr = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Float.parseFloat(parts[i]);
        }
        return arr;
    }
    private static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
