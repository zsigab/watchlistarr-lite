package watchlistarr.sync;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.config.ConfigurationService;
import watchlistarr.plex.PlexService;

@ApplicationScoped
public class PingSync {

    private static final Logger log = LoggerFactory.getLogger(PingSync.class);

    @Inject ConfigurationService configService;
    @Inject PlexService plexService;

    @Scheduled(every = "24H", delayed = "0s")
    void run() {
        try {
            plexService.ping(configService.get().plex());
        }
        catch (Exception e) {
            log.warn("Ping sync error: {}", e.getMessage());
        }
    }
}
