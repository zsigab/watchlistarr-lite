package watchlistarr.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import watchlistarr.config.*;
import watchlistarr.model.Item;
import watchlistarr.plex.PlexService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FullSyncTest {

    @Mock ConfigurationService configService;
    @Mock PlexService plexService;
    @Mock SyncHelper syncHelper;
    @InjectMocks FullSync fullSync;

    private AppConfig buildConfig() {
        var sonarr = new SonarrConfig("http://localhost:8989", "key", 1, "/shows", false, "all", 1, Set.of());
        var radarr = new RadarrConfig("http://localhost:7878", "key", 1, "/movies", false, Set.of());
        var plex   = new PlexConfig(Set.of(), Set.of("token"), false, true);
        var delete = new DeleteConfig(false, false, false, 7, true);
        return new AppConfig(60, sonarr, radarr, plex, delete);
    }

    @Test void sync_fullSync_passesTokenWatchlistToHelper() {
        AppConfig config = buildConfig();
        Item show = new Item("New Show", List.of("tvdb://999"), "show", null, null);
        when(plexService.getSelfWatchlist(any())).thenReturn(Set.of(show));
        when(plexService.getOthersWatchlist(any())).thenReturn(Set.of());

        fullSync.sync(config, true);

        ArgumentCaptor<Set<Item>> captor = ArgumentCaptor.forClass(Set.class);
        verify(syncHelper).processWatchlist(eq(config), captor.capture());
        assertTrue(captor.getValue().contains(show));
    }

    @Test void sync_rssOnly_doesNotCallTokenWatchlist() {
        AppConfig config = buildConfig();

        fullSync.sync(config, false);

        verify(plexService, never()).getSelfWatchlist(any());
        verify(plexService, never()).getOthersWatchlist(any());
        verify(syncHelper).processWatchlist(eq(config), any());
    }
}
