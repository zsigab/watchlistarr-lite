package watchlistarr.sonarr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import watchlistarr.config.SonarrConfig;
import watchlistarr.http.HttpService;
import watchlistarr.model.Item;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SonarrServiceTest {

    @Mock HttpService http;
    @InjectMocks SonarrService sonarrService;

    private ObjectMapper mapper = new ObjectMapper();
    private SonarrConfig config = new SonarrConfig(
        "http://localhost:8989", "test-api-key", 1, "/shows", false, "all", Set.of()
    );

    @BeforeEach void setup() {
        when(http.getMapper()).thenReturn(mapper);
    }

    @Test void fetchSeries_returnsMappedItems() throws Exception {
        var json = loadJson("sonarr.json");
        when(http.get("http://localhost:8989/api/v3/series", "test-api-key")).thenReturn(Optional.of(json));
        when(http.get("http://localhost:8989/api/v3/importlistexclusion", "test-api-key")).thenReturn(Optional.of(mapper.createArrayNode()));

        Set<Item> result = sonarrService.fetchSeries(config, false);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(i -> "show".equals(i.category)));
    }

    @Test void fetchSeries_bypassIgnored_skipsExclusions() throws Exception {
        var json = loadJson("sonarr.json");
        when(http.get("http://localhost:8989/api/v3/series", "test-api-key")).thenReturn(Optional.of(json));

        sonarrService.fetchSeries(config, true);

        verify(http, never()).get(contains("importlistexclusion"), anyString());
    }

    @Test void fetchSeries_withExclusions() throws Exception {
        var series = loadJson("sonarr.json");
        var exclusions = loadJson("importlistexclusion.json");
        when(http.get("http://localhost:8989/api/v3/series", "test-api-key")).thenReturn(Optional.of(series));
        when(http.get("http://localhost:8989/api/v3/importlistexclusion", "test-api-key")).thenReturn(Optional.of(exclusions));

        Set<Item> result = sonarrService.fetchSeries(config, false);
        assertFalse(result.isEmpty());
    }

    private com.fasterxml.jackson.databind.JsonNode loadJson(String name) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            return mapper.readTree(is);
        }
    }
}
