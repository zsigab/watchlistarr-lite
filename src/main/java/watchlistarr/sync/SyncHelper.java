package watchlistarr.sync;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.config.AppConfig;
import watchlistarr.model.Item;
import watchlistarr.radarr.RadarrService;
import watchlistarr.sonarr.SonarrService;

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class SyncHelper {

    private static final Logger log = LoggerFactory.getLogger(SyncHelper.class);

    @Inject SonarrService sonarrService;
    @Inject RadarrService radarrService;

    void processWatchlist(AppConfig config, Set<Item> watchlist) {
        Set<Item> existingMovies = radarrService.fetchMovies(config.radarr(), config.radarr().bypassIgnored());
        Set<Item> existingSeries = sonarrService.fetchSeries(config.sonarr(), config.sonarr().bypassIgnored());
        Set<Item> existingAll = new HashSet<>();
        existingAll.addAll(existingMovies);
        existingAll.addAll(existingSeries);

        for (Item watched : watchlist) {
            boolean alreadyExists = existingAll.stream().anyMatch(e -> e.matches(watched));
            if (alreadyExists) {
                log.debug("{} \"{}\" already exists in Sonarr/Radarr", watched.category, watched.title);
                continue;
            }
            switch (watched.category) {
                case "show" -> {
                    if (watched.getTvdbId().isPresent()) {
                        log.debug("Found show \"{}\" on behalf of {} which does not exist yet in Sonarr", watched.title, watched.username);
                        sonarrService.addToSonarr(config.sonarr(), watched);
                    }
                    else {
                        log.info("Found show \"{}\" on behalf of {} with no tvdb ID, skipping", watched.title, watched.username);
                    }
                }
                case "movie" -> {
                    if (watched.getTmdbId().isPresent()) {
                        log.debug("Found movie \"{}\" on behalf of {} which does not exist yet in Radarr", watched.title, watched.username);
                        radarrService.addToRadarr(config.radarr(), watched);
                    }
                    else {
                        log.info("Found movie \"{}\" on behalf of {} with no tmdb ID, skipping", watched.title, watched.username);
                    }
                }
                default -> log.warn("Found \"{}\" on behalf of {} with unrecognised category: {}", watched.title, watched.category, watched.username);
            }
        }
    }
}
