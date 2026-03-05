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
public class DeleteSync {

    private static final Logger log = LoggerFactory.getLogger(DeleteSync.class);

    @Inject ConfigurationService configService;
    @Inject PlexService plexService;
    @Inject SonarrService sonarrService;
    @Inject RadarrService radarrService;

    @Scheduled(every = "${delete.interval-days:7}d", delayed = "10s")
    void run() {
        try {
            sync(configService.get());
        }
        catch (Exception e) {
            log.warn("Delete sync error: {}", e.getMessage());
        }
    }

    void sync(AppConfig config) {
        Set<Item> plexWatchlist = new HashSet<>(plexService.getSelfWatchlist(config.plex()));
        if (!config.plex().skipFriendSync()) {
            plexWatchlist.addAll(plexService.getOthersWatchlist(config.plex()));
        }
        for (String url : config.plex().watchlistUrls()) {
            plexWatchlist.addAll(plexService.fetchWatchlistFromRss(url));
        }

        Set<Item> movies = radarrService.fetchMovies(config.radarr(), true);
        Set<Item> series = sonarrService.fetchSeries(config.sonarr(), true);

        for (Item item : movies) {
            if (plexWatchlist.stream().anyMatch(p -> p.matches(item))) {
                log.debug("movie \"{}\" already exists in Plex", item.title);
            }
            else {
                deleteMovie(config, item);
            }
        }
        for (Item item : series) {
            if (plexWatchlist.stream().anyMatch(p -> p.matches(item))) {
                log.debug("show \"{}\" already exists in Plex", item.title);
            }
            else {
                deleteSeries(config, item);
            }
        }
    }

    private void deleteMovie(AppConfig config, Item movie) {
        if (config.delete().movieDeleting()) {
            radarrService.deleteFromRadarr(config.radarr(), movie, config.delete().deleteFiles());
        }
        else {
            log.info("Found movie \"{}\" which is not watchlisted on Plex", movie.title);
        }
    }

    private void deleteSeries(AppConfig config, Item show) {
        boolean isEnded = Boolean.TRUE.equals(show.ended);
        boolean isContinuing = Boolean.FALSE.equals(show.ended);
        if (isEnded && config.delete().endedShowDeleting()) {
            sonarrService.deleteFromSonarr(config.sonarr(), show, config.delete().deleteFiles());
        }
        else if (isContinuing && config.delete().continuingShowDeleting()) {
            sonarrService.deleteFromSonarr(config.sonarr(), show, config.delete().deleteFiles());
        }
        else {
            log.info("Found show \"{}\" which is not watchlisted on Plex", show.title);
        }
    }
}
