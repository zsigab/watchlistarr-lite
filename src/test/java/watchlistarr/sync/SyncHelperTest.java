package watchlistarr.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import watchlistarr.config.*;
import watchlistarr.model.Item;
import watchlistarr.radarr.RadarrService;
import watchlistarr.sonarr.SonarrService;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncHelperTest {

    @Mock SonarrService sonarrService;
    @Mock RadarrService radarrService;
    @InjectMocks SyncHelper syncHelper;

    private AppConfig buildConfig() {
        var sonarr = new SonarrConfig("http://localhost:8989", "key", 1, "/shows", false, "all", 1, Set.of());
        var radarr = new RadarrConfig("http://localhost:7878", "key", 1, "/movies", false, Set.of());
        var plex   = new PlexConfig(Set.of(), Set.of("token"), false, true);
        var delete = new DeleteConfig(false, false, false, 7, true);
        return new AppConfig(60, sonarr, radarr, plex, delete);
    }

    @Test void processWatchlist_addsNewShow() {
        AppConfig config = buildConfig();
        Item watchedShow = new Item("New Show", List.of("tvdb://999"), "show", null, null);
        when(sonarrService.fetchSeries(any(), eq(false))).thenReturn(Set.of());
        when(radarrService.fetchMovies(any(), eq(false))).thenReturn(Set.of());

        syncHelper.processWatchlist(config, Set.of(watchedShow));

        verify(sonarrService).addToSonarr(any(), eq(watchedShow));
        verify(radarrService, never()).addToRadarr(any(), any());
    }

    @Test void processWatchlist_addsNewMovie() {
        AppConfig config = buildConfig();
        Item watchedMovie = new Item("New Movie", List.of("tmdb://888"), "movie", null, null);
        when(sonarrService.fetchSeries(any(), eq(false))).thenReturn(Set.of());
        when(radarrService.fetchMovies(any(), eq(false))).thenReturn(Set.of());

        syncHelper.processWatchlist(config, Set.of(watchedMovie));

        verify(radarrService).addToRadarr(any(), eq(watchedMovie));
        verify(sonarrService, never()).addToSonarr(any(), any());
    }

    @Test void processWatchlist_skipsExistingItem() {
        AppConfig config = buildConfig();
        Item existing = new Item("Existing", List.of("tvdb://1"), "show", false, null);
        Item watched  = new Item("Existing", List.of("tvdb://1"), "show", null, null);
        when(sonarrService.fetchSeries(any(), eq(false))).thenReturn(Set.of(existing));
        when(radarrService.fetchMovies(any(), eq(false))).thenReturn(Set.of());

        syncHelper.processWatchlist(config, Set.of(watched));

        verify(sonarrService, never()).addToSonarr(any(), any());
    }
}
