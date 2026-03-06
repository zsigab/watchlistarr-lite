package watchlistarr.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class HttpService {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);

    private static final long CACHE_TTL_MS = 5_000;
    private static final int  CACHE_MAX    = 1_000;

    private HttpClient client;
    private ObjectMapper mapper;
    private Map<CacheKey, CachedValue> cache;

    @PostConstruct
    void init() {
        client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        mapper = new ObjectMapper()
            .registerModule(new Jdk8Module());
        cache = new ConcurrentHashMap<>();
    }

    public Optional<JsonNode> get(String url, String apiKey) {
        return cachedRequest("GET", url, apiKey, null);
    }

    public Optional<JsonNode> get(String url) {
        return cachedRequest("GET", url, null, null);
    }

    public Optional<JsonNode> post(String url, String apiKey, Object body) {
        String bodyStr = serializeBody(body);
        return cachedRequest("POST", url, apiKey, bodyStr);
    }

    public Optional<JsonNode> post(String url, Object body) {
        return post(url, null, body);
    }

    public Optional<JsonNode> delete(String url, String apiKey) {
        return makeRequest("DELETE", url, apiKey, null);
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    private Optional<JsonNode> cachedRequest(String method, String url, String apiKey, String body) {
        CacheKey key = new CacheKey(method, url, apiKey, body);
        CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }
        Optional<JsonNode> result = makeRequest(method, url, apiKey, body);
        cache.put(key, new CachedValue(result));
        if (cache.size() > CACHE_MAX) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
        }
        return result;
    }

    private Optional<JsonNode> makeRequest(String method, String url, String apiKey, String body) {
        try {
            URI uri = URI.create(url);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "watchlistarr/1.0");

            if (apiKey != null) {
                requestBuilder.header("X-Api-Key", apiKey);
                requestBuilder.header("X-Plex-Token", apiKey);
            }

            HttpRequest.BodyPublisher bodyPublisher = body != null
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

            HttpRequest request = requestBuilder.method(method, bodyPublisher).build();

            log.debug("HTTP {} {}", method, url);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("HTTP Response: {} {}", response.statusCode(), url);

            String responseBody = response.body();
            if (responseBody == null || responseBody.isBlank()) {
                return Optional.of(mapper.nullNode());
            }
            return Optional.of(mapper.readTree(responseBody));
        }
        catch (Exception e) {
            log.warn("HTTP request failed [{} {}]: {}", method, url, e.getMessage());
            return Optional.empty();
        }
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(body);
        }
        catch (Exception e) {
            log.warn("Failed to serialize request body: {}", e.getMessage());
            return null;
        }
    }

    private static class CachedValue {
        final Optional<JsonNode> value;
        private final long expiresAt;

        CachedValue(Optional<JsonNode> value) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    record CacheKey(String method, String url, String apiKey, String body) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) o;
            return Objects.equals(url, cacheKey.url) &&
                    Objects.equals(body, cacheKey.body) &&
                    Objects.equals(method, cacheKey.method) &&
                    Objects.equals(apiKey, cacheKey.apiKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, url, apiKey, body);
        }
    }
}
