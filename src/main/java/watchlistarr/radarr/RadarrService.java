package watchlistarr.radarr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.config.RadarrConfig;
import watchlistarr.http.HttpService;
import watchlistarr.model.Item;
import watchlistarr.radarr.model.RadarrMovie;
import watchlistarr.radarr.model.RadarrMovieExclusion;
import watchlistarr.radarr.model.RadarrPost;

import java.util.*;

@ApplicationScoped
public class RadarrService {

    private static final Logger log = LoggerFactory.getLogger(RadarrService.class);

    @Inject HttpService http;

    public Set<Item> fetchMovies(RadarrConfig config, boolean bypass) {
        List<RadarrMovie> movies = getArr(config.baseUrl(), config.apiKey(), "movie", new TypeReference<List<RadarrMovie>>() {});
        List<RadarrMovieExclusion> exclusions = bypass
            ? List.<RadarrMovieExclusion>of()
            : getArr(config.baseUrl(), config.apiKey(), "exclusions", new TypeReference<List<RadarrMovieExclusion>>() {});

        Set<Item> result = new HashSet<>();
        Objects.requireNonNull(movies).stream().map(this::toItem).forEach(result::add);
        Objects.requireNonNull(exclusions).stream().map(this::toMovieItem).forEach(result::add);
        return result;
    }

    public void addToRadarr(RadarrConfig config, Item item) {
        RadarrPost post = new RadarrPost(
            item.title,
            item.getTmdbId().orElse(0L),
                config.qualityProfileId(),
                config.rootFolder(),
            new ArrayList<>(config.tagIds())
        );
        Optional<JsonNode> result = http.post(config.baseUrl() + "/api/v3/movie", config.apiKey(), post);
        if (result.isPresent()) {
            log.info("Sent {} to Radarr on behalf of {}", item.title, item.username);
        }
        else {
            log.warn("Received warning for sending {} to Radarr", item.title);
        }
    }

    public void deleteFromRadarr(RadarrConfig config, Item item, boolean deleteFiles) {
        long id = item.getRadarrId().orElseGet(() -> {
            log.warn("Unable to extract Radarr ID from movie to delete: {}", item);
            return 0L;
        });
        String url = config.baseUrl() + "/api/v3/movie/" + id
            + "?deleteFiles=" + deleteFiles + "&addImportExclusion=false";
        http.delete(url, config.apiKey());
        log.info("Deleted {} from Radarr", item.title);
    }

    private Item toItem(RadarrMovie m) {
        List<String> guids = new ArrayList<>();
        if (m.imdbId != null) {
            guids.add(m.imdbId);
        }
        if (m.tmdbId != null) {
            guids.add("tmdb://" + m.tmdbId);
        }
        guids.add("radarr://" + m.id);
        return new Item(m.title, guids, "movie", null, null);
    }

    private Item toMovieItem(RadarrMovieExclusion e) {
        return toItem(new RadarrMovie() {{ title = e.movieTitle; imdbId = e.imdbId; tmdbId = e.tmdbId; id = e.id; }});
    }

    private <T> T getArr(String baseUrl, String apiKey, String endpoint, TypeReference<T> type) {
        Optional<JsonNode> result = http.get(baseUrl + "/api/v3/" + endpoint, apiKey);
        if (result.isEmpty()) {
            log.warn("No response from Radarr endpoint: {}", endpoint);
            try {
                return http.getMapper().convertValue(List.of(), type);
            }
            catch (Exception e) {
                return null;
            }
        }
        try {
            return http.getMapper().convertValue(result.get(), type);
        }
        catch (Exception e) {
            log.warn("Failed to decode Radarr response for {}: {}", endpoint, e.getMessage());
            try {
                return http.getMapper().convertValue(List.of(), type);
            }
            catch (Exception ex) {
                return null;
            }
        }
    }
}
