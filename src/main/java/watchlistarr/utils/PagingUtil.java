package watchlistarr.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.http.HttpService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PagingUtil {

    public static final int PAGE_SIZE = 1000;
    private static final Logger log = LoggerFactory.getLogger(PagingUtil.class);

    public static <T> List<T> getAllPaged(HttpService http, String baseUrl, String apiKey, String endpoint, TypeReference<List<T>> type) {
        List<T> all = new ArrayList<>();
        int page = 1;
        while (true) {
            String url = baseUrl + "/api/v3/" + endpoint + "/paged?page=" + page + "&pageSize=" + PAGE_SIZE;
            Optional<JsonNode> result = http.get(url, apiKey);
            if (result.isEmpty()) {
                log.warn("No response from paged endpoint: {}", endpoint);
                break;
            }
            try {
                int totalRecords = result.get().path("totalRecords").asInt(0);
                JsonNode recordsNode = result.get().path("records");
                List<T> items = http.getMapper().convertValue(recordsNode, type);
                all.addAll(items);
                if (all.size() >= totalRecords || items.isEmpty()) {
                    break;
                }
                page++;
            }
            catch (Exception e) {
                log.warn("Failed to decode paged response for {}: {}", endpoint, e.getMessage());
                break;
            }
        }
        return all;
    }
}
