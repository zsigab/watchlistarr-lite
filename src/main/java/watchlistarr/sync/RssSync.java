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

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class RssSync {

    private static final Logger log = LoggerFactory.getLogger(RssSync.class);

    @Inject ConfigurationService configService;
    @Inject PlexService plexService;
    @Inject SyncHelper syncHelper;

    @Scheduled(every = "${refresh.interval-seconds:60}s", delayed = "0s")
    void run() {
        try {
            sync(configService.get());
        }
        catch (Exception e) {
            log.warn("RSS sync error: {}", e.getMessage());
        }
    }

    void sync(AppConfig config) {
        Set<Item> watchlist = new HashSet<>();
        for (String url : config.plex().watchlistUrls()) {
            watchlist.addAll(plexService.fetchWatchlistFromRss(url));
        }

        if (watchlist.isEmpty()) {
            return;
        }

        syncHelper.processWatchlist(config, watchlist);
    }
}
