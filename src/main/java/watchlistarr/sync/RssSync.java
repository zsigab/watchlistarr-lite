package watchlistarr.sync;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.config.AppConfig;
import watchlistarr.config.ConfigurationService;
import watchlistarr.model.Item;
import watchlistarr.plex.PlexService;
import watchlistarr.radarr.RadarrService;
import watchlistarr.sonarr.SonarrService;

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class RssSync {

    private static final Logger log = LoggerFactory.getLogger(RssSync.class);

    @Inject ConfigurationService configService;
    @Inject PlexService plexService;
    @Inject SonarrService sonarrService;
    @Inject RadarrService radarrService;

    @Scheduled(every = "${refresh.interval-seconds:60}s", delayed = "0s")
    void run() {
        try {
            sync(configService.get());
        } catch (Exception e) {
            log.warn("RSS sync error: {}", e.getMessage());
        }
    }

    void sync(AppConfig config) {
        // RSS-only sync: no token-based self/others fetch (runFullSync=false equivalent)
        Set<Item> watchlist = new HashSet<>();
        for (var url : config.plex().watchlistUrls()) {
            watchlist.addAll(plexService.fetchWatchlistFromRss(url));
        }

        if (watchlist.isEmpty()) return;

        var existingMovies = radarrService.fetchMovies(config.radarr(), config.radarr().bypassIgnored());
        var existingSeries = sonarrService.fetchSeries(config.sonarr(), config.sonarr().bypassIgnored());
        var existingAll = new HashSet<Item>();
        existingAll.addAll(existingMovies);
        existingAll.addAll(existingSeries);

        for (var watched : watchlist) {
            boolean alreadyExists = existingAll.stream().anyMatch(e -> e.matches(watched));
            if (alreadyExists) {
                log.debug("{} \"{}\" already exists in Sonarr/Radarr", watched.category, watched.title);
                continue;
            }
            switch (watched.category) {
                case "show" -> {
                    if (watched.getTvdbId().isPresent()) {
                        log.debug("Found show \"{}\" which does not exist yet in Sonarr", watched.title);
                        sonarrService.addToSonarr(config.sonarr(), watched);
                    } else {
                        log.debug("Found show \"{}\" with no tvdb ID, skipping", watched.title);
                    }
                }
                case "movie" -> {
                    if (watched.getTmdbId().isPresent()) {
                        log.debug("Found movie \"{}\" which does not exist yet in Radarr", watched.title);
                        radarrService.addToRadarr(config.radarr(), watched);
                    } else {
                        log.debug("Found movie \"{}\" with no tmdb ID, skipping", watched.title);
                    }
                }
                default -> log.warn("Found \"{}\" with unrecognised category: {}", watched.title, watched.category);
            }
        }
    }
}
