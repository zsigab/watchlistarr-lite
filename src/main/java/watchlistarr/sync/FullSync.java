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
public class FullSync {

    private static final Logger log = LoggerFactory.getLogger(FullSync.class);

    @Inject ConfigurationService configService;
    @Inject PlexService plexService;
    @Inject SyncHelper syncHelper;

    @Scheduled(every = "19m", delayed = "5s")
    void run() {
        try {
            sync(configService.get(), true);
        }
        catch (Exception e) {
            log.warn("Full sync error: {}", e.getMessage());
        }
    }

    void sync(AppConfig config, boolean runFullSync) {
        Set<Item> watchlist = new HashSet<>();

        if (runFullSync) {
            Set<Item> self = plexService.getSelfWatchlist(config.plex());
            log.info("Found {} items on user's watchlist using the plex token", self.size());
            watchlist.addAll(self);

            if (!config.plex().skipFriendSync()) {
                Set<Item> others = plexService.getOthersWatchlist(config.plex());
                log.info("Found {} items on other available watchlists using the plex token", others.size());
                watchlist.addAll(others);
            }
        }

        syncHelper.processWatchlist(config, watchlist);
    }
}
