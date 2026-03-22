package watchlistarr.radarr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import watchlistarr.config.RadarrConfig;
import watchlistarr.http.HttpService;
import watchlistarr.model.Item;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RadarrServiceTest {

    @Mock HttpService http;
    @InjectMocks RadarrService radarrService;

    private ObjectMapper mapper = new ObjectMapper();
    private RadarrConfig config = new RadarrConfig(
        "http://localhost:7878", "test-api-key", 1, "/movies", false, Set.of()
    );

    @BeforeEach void setup() {
        when(http.getMapper()).thenReturn(mapper);
    }

    @Test void fetchMovies_returnsMappedItems() throws Exception {
        var json = loadJson("radarr.json");
        when(http.get("http://localhost:7878/api/v3/movie", "test-api-key")).thenReturn(Optional.of(json));
        when(http.get("http://localhost:7878/api/v3/exclusions/paged?page=1&pageSize=1000", "test-api-key"))
            .thenReturn(Optional.of(mapper.readTree("{\"totalRecords\":0,\"records\":[]}")));

        Set<Item> result = radarrService.fetchMovies(config, false);

        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(i -> "movie".equals(i.category)));
    }

    @Test void fetchMovies_bypassIgnored_skipsExclusions() throws Exception {
        var json = loadJson("radarr.json");
        when(http.get("http://localhost:7878/api/v3/movie", "test-api-key")).thenReturn(Optional.of(json));

        radarrService.fetchMovies(config, true);

        verify(http, never()).get(contains("exclusions"), anyString());
    }

    @Test void fetchMovies_withExclusions() throws Exception {
        var movies = loadJson("radarr.json");
        var exclusions = loadJson("exclusions.json");
        when(http.get("http://localhost:7878/api/v3/movie", "test-api-key")).thenReturn(Optional.of(movies));
        when(http.get("http://localhost:7878/api/v3/exclusions/paged?page=1&pageSize=1000", "test-api-key")).thenReturn(Optional.of(exclusions));

        Set<Item> result = radarrService.fetchMovies(config, false);
        assertFalse(result.isEmpty());
    }

    private com.fasterxml.jackson.databind.JsonNode loadJson(String name) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(name)) {
            return mapper.readTree(is);
        }
    }
}
