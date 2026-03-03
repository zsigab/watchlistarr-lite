package watchlistarr.sonarr;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.config.SonarrConfig;
import watchlistarr.http.HttpService;
import watchlistarr.model.Item;
import watchlistarr.sonarr.model.SonarrAddOptions;
import watchlistarr.sonarr.model.SonarrPost;
import watchlistarr.sonarr.model.SonarrSeries;

import java.util.*;

@ApplicationScoped
public class SonarrService {

    private static final Logger log = LoggerFactory.getLogger(SonarrService.class);

    @Inject HttpService http;

    public Set<Item> fetchSeries(SonarrConfig config, boolean bypass) {
        var shows = getArr(config.baseUrl(), config.apiKey(), "series", new TypeReference<List<SonarrSeries>>() {});
        var exclusions = bypass
            ? List.<SonarrSeries>of()
            : getArr(config.baseUrl(), config.apiKey(), "importlistexclusion", new TypeReference<List<SonarrSeries>>() {});

        var result = new HashSet<Item>();
        Objects.requireNonNull(shows).stream().map(this::toItem).forEach(result::add);
        Objects.requireNonNull(exclusions).stream().map(this::toItem).forEach(result::add);
        return result;
    }

    public void addToSonarr(SonarrConfig config, Item item) {
        var addOptions = new SonarrAddOptions(config.seasonMonitoring());
        var post = new SonarrPost(
            item.title,
            item.getTvdbId().orElse(0L),
                config.qualityProfileId(),
                config.rootFolder(),
            addOptions,
                config.languageProfileId(),
            new ArrayList<>(config.tagIds())
        );
        var result = http.post(config.baseUrl() + "/api/v3/series", config.apiKey(), post);
        if (result.isPresent()) {
            log.info("Sent {} to Sonarr", item.title);
        } else {
            log.debug("Received warning for sending {} to Sonarr", item.title);
        }
    }

    public void deleteFromSonarr(SonarrConfig config, Item item, boolean deleteFiles) {
        var id = item.getSonarrId().orElseGet(() -> {
            log.warn("Unable to extract Sonarr ID from show to delete: {}", item);
            return 0L;
        });
        var url = config.baseUrl() + "/api/v3/series/" + id
            + "?deleteFiles=" + deleteFiles + "&addImportListExclusion=false";
        http.delete(url, config.apiKey());
        log.info("Deleted {} from Sonarr", item.title);
    }

    private Item toItem(SonarrSeries s) {
        List<String> guids = new ArrayList<>();
        if (s.imdbId != null) guids.add(s.imdbId);
        if (s.tvdbId != null) guids.add("tvdb://" + s.tvdbId);
        guids.add("sonarr://" + s.id);
        return new Item(s.title, guids, "show", s.ended);
    }

    private <T> T getArr(String baseUrl, String apiKey, String endpoint, TypeReference<T> type) {
        var result = http.get(baseUrl + "/api/v3/" + endpoint, apiKey);
        if (result.isEmpty()) {
            log.warn("No response from Sonarr endpoint: {}", endpoint);
            try { return http.getMapper().convertValue(List.of(), type); } catch (Exception e) { return null; }
        }
        try {
            return http.getMapper().convertValue(result.get(), type);
        } catch (Exception e) {
            log.warn("Failed to decode Sonarr response for {}: {}", endpoint, e.getMessage());
            try { return http.getMapper().convertValue(List.of(), type); } catch (Exception ex) { return null; }
        }
    }
}
