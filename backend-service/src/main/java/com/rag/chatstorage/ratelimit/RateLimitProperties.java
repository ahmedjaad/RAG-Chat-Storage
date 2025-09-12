package com.rag.chatstorage.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean trustProxies = false;
    private String keyPrefix = "ratelimit:";
    private String defaultTier = "default";

    // Redis
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private boolean ssl = false;
    private int database = 0;
    private int keyTtlSeconds = 86400; // default 1 day

    private List<String> whitelistPaths = Arrays.asList("/", "/docs", "/swagger-ui/**", "/actuator/health/**", "/ui/**", "/static/**");

    private Map<String, String> apiKeys = new HashMap<>();

    private List<Policy> policies = new ArrayList<>();

    // Fallback local limiter tightness multiplier (smaller is stricter)
    private double fallbackFactor = 0.5;

    public static class Policy {
        private String id;
        private String tier; // optional
        private Match match = new Match();
        private List<BandwidthDef> bandwidths = new ArrayList<>();
        private List<Cost> costs = new ArrayList<>();
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public Match getMatch() { return match; }
        public void setMatch(Match match) { this.match = match; }
        public List<BandwidthDef> getBandwidths() { return bandwidths; }
        public void setBandwidths(List<BandwidthDef> bandwidths) { this.bandwidths = bandwidths; }
        public List<Cost> getCosts() { return costs; }
        public void setCosts(List<Cost> costs) { this.costs = costs; }
    }
    public static class Match {
        private List<String> methods = Arrays.asList("GET","POST","PATCH","DELETE");
        private List<String> paths = Arrays.asList("/api/**");
        public List<String> getMethods() { return methods; }
        public void setMethods(List<String> methods) { this.methods = methods; }
        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) { this.paths = paths; }
    }
    public static class BandwidthDef {
        private long limit;
        private long windowSeconds;
        private String refillStrategy = "smooth"; // smooth or interval
        public long getLimit() { return limit; }
        public void setLimit(long limit) { this.limit = limit; }
        public long getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(long windowSeconds) { this.windowSeconds = windowSeconds; }
        public String getRefillStrategy() { return refillStrategy; }
        public void setRefillStrategy(String refillStrategy) { this.refillStrategy = refillStrategy; }
    }
    public static class Cost {
        private String path;
        private String method;
        private int tokens = 1;
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public int getTokens() { return tokens; }
        public void setTokens(int tokens) { this.tokens = tokens; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isTrustProxies() { return trustProxies; }
    public void setTrustProxies(boolean trustProxies) { this.trustProxies = trustProxies; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getDefaultTier() { return defaultTier; }
    public void setDefaultTier(String defaultTier) { this.defaultTier = defaultTier; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public boolean isSsl() { return ssl; }
    public void setSsl(boolean ssl) { this.ssl = ssl; }
    public int getDatabase() { return database; }
    public void setDatabase(int database) { this.database = database; }
    public int getKeyTtlSeconds() { return keyTtlSeconds; }
    public void setKeyTtlSeconds(int keyTtlSeconds) { this.keyTtlSeconds = keyTtlSeconds; }
    public List<String> getWhitelistPaths() { return whitelistPaths; }
    public void setWhitelistPaths(List<String> whitelistPaths) { this.whitelistPaths = whitelistPaths; }
    public Map<String, String> getApiKeys() { return apiKeys; }
    public void setApiKeys(Map<String, String> apiKeys) { this.apiKeys = apiKeys; }
    public List<Policy> getPolicies() { return policies; }
    public void setPolicies(List<Policy> policies) { this.policies = policies; }
    public double getFallbackFactor() { return fallbackFactor; }
    public void setFallbackFactor(double fallbackFactor) { this.fallbackFactor = fallbackFactor; }
}
