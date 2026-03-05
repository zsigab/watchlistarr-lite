package watchlistarr.config;

import com.fasterxml.jackson.core.type.TypeReference;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.http.HttpService;
import watchlistarr.plex.model.RssFeedGenerated;

import java.util.*;
import java.util.stream.Collectors;

@Startup
@ApplicationScoped
public class ConfigurationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationService.class);

    private static final List<String> FALLBACK_HOSTS = List.of(
        "http://localhost", "http://127.0.0.1", "http://host.docker.internal"
    );

    @Inject HttpService http;

    // Sonarr raw config
    @ConfigProperty(name = "sonarr.api-key")          Optional<String> sonarrApiKey;
    @ConfigProperty(name = "sonarr.base-url")          Optional<String> sonarrBaseUrl;
    @ConfigProperty(name = "sonarr.quality-profile")   Optional<String> sonarrQualityProfile;
    @ConfigProperty(name = "sonarr.root-folder")       Optional<String> sonarrRootFolder;
    @ConfigProperty(name = "sonarr.bypass-ignored",    defaultValue = "false") boolean sonarrBypassIgnored;
    @ConfigProperty(name = "sonarr.season-monitoring", defaultValue = "all") String sonarrSeasonMonitoring;
    @ConfigProperty(name = "sonarr.tags")              Optional<String> sonarrTags;

    // Radarr raw config
    @ConfigProperty(name = "radarr.api-key")         Optional<String> radarrApiKey;
    @ConfigProperty(name = "radarr.base-url")         Optional<String> radarrBaseUrl;
    @ConfigProperty(name = "radarr.quality-profile")  Optional<String> radarrQualityProfile;
    @ConfigProperty(name = "radarr.root-folder")      Optional<String> radarrRootFolder;
    @ConfigProperty(name = "radarr.bypass-ignored",   defaultValue = "false") boolean radarrBypassIgnored;
    @ConfigProperty(name = "radarr.tags")             Optional<String> radarrTags;

    // Plex raw config
    @ConfigProperty(name = "plex.token")            Optional<String> plexToken;
    @ConfigProperty(name = "plex.watchlist1")        Optional<String> plexWatchlist1;
    @ConfigProperty(name = "plex.watchlist2")        Optional<String> plexWatchlist2;
    @ConfigProperty(name = "plex.skip-friend-sync",  defaultValue = "false") boolean skipFriendSync;

    // Delete config
    @ConfigProperty(name = "delete.movie",            defaultValue = "false") boolean deleteMovie;
    @ConfigProperty(name = "delete.ended-show",        defaultValue = "false") boolean deleteEndedShow;
    @ConfigProperty(name = "delete.continuing-show",   defaultValue = "false") boolean deleteContinuingShow;
    @ConfigProperty(name = "delete.interval-days",     defaultValue = "7") long deleteIntervalDays;
    @ConfigProperty(name = "delete.delete-files",      defaultValue = "true") boolean deleteFiles;

    // Refresh
    @ConfigProperty(name = "refresh.interval-seconds", defaultValue = "60") long refreshIntervalSeconds;

    private AppConfig appConfig;

    @PostConstruct
    void init() {
        log.info("Initialising configuration...");
        try {
            appConfig = resolve();
            log.info(redact(appConfig));
        } catch (Exception e) {
            log.error("Failed to initialise configuration: {}", e.getMessage());
            throw new RuntimeException("Configuration initialisation failed", e);
        }
    }

    public AppConfig get() { return appConfig; }

    // ── Resolution ────────────────────────────────────────────────────────────

    private AppConfig resolve() {
        var sonarr = resolveSonarr();
        var radarr = resolveRadarr();
        var tokens = parsePlexTokens();
        var watchlistUrls = resolvePlexWatchlistUrls(tokens);
        boolean hasPlexPass = !watchlistUrls.isEmpty();
        long effectiveInterval = hasPlexPass ? refreshIntervalSeconds : 19 * 60;

        return new AppConfig(
            effectiveInterval,
            sonarr,
            radarr,
            new PlexConfig(watchlistUrls, tokens, skipFriendSync, hasPlexPass),
            new DeleteConfig(deleteMovie, deleteEndedShow, deleteContinuingShow, deleteIntervalDays, deleteFiles)
        );
    }

    private SonarrConfig resolveSonarr() {
        var apiKey = sonarrApiKey.orElse("");
        if (apiKey.isBlank()) error("Unable to find Sonarr API key");
        var url = findCorrectUrl(buildCandidateUrls(sonarrBaseUrl.orElse(""), 8989), apiKey, 8989);

        var rootFolder = fetchRootFolder(url, apiKey, sonarrRootFolder.orElse(""), "Sonarr");
        var qualityProfileId = fetchQualityProfile(url, apiKey, sonarrQualityProfile.orElse(""), "Sonarr");
        var languageProfileId = fetchLanguageProfile(url, apiKey);
        var tagIds = resolveTags(url, apiKey, sonarrTags.orElse(""));
        log.info("Successfully connected to Sonarr at {}", url);
        return new SonarrConfig(url, apiKey, qualityProfileId, rootFolder,
            sonarrBypassIgnored, sonarrSeasonMonitoring, languageProfileId, tagIds);
    }

    private RadarrConfig resolveRadarr() {
        var apiKey = radarrApiKey.orElse("");
        if (apiKey.isBlank()) error("Unable to find Radarr API key");
        var url = findCorrectUrl(buildCandidateUrls(radarrBaseUrl.orElse(""), 7878), apiKey, 7878);

        var rootFolder = fetchRootFolder(url, apiKey, radarrRootFolder.orElse(""), "Radarr");
        var qualityProfileId = fetchQualityProfile(url, apiKey, radarrQualityProfile.orElse(""), "Radarr");
        var tagIds = resolveTags(url, apiKey, radarrTags.orElse(""));
        log.info("Successfully connected to Radarr at {}", url);
        return new RadarrConfig(url, apiKey, qualityProfileId, rootFolder, radarrBypassIgnored, tagIds);
    }

    // ── URL probing ───────────────────────────────────────────────────────────

    private List<String> buildCandidateUrls(String configured, int defaultPort) {
        List<String> candidates = new ArrayList<>();
        if (!configured.isBlank()) {
            candidates.add(normaliseUrl(configured));
        }
        FALLBACK_HOSTS.forEach(h -> candidates.add(h + ":" + defaultPort));
        return candidates;
    }

    private String findCorrectUrl(List<String> candidates, String apiKey, int defaultPort) {
        for (String url : candidates) {
            var result = http.get(url + "/api/v3/health", apiKey);
            if (result.isPresent()) return url;
        }
        var fallback = "http://localhost:" + defaultPort;
        log.warn("Could not reach any candidate URL, defaulting to {}", fallback);
        return fallback;
    }

    private String normaliseUrl(String url) {
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ── Profile / folder helpers ──────────────────────────────────────────────

    private String fetchRootFolder(String baseUrl, String apiKey, String preferred, String app) {
        var result = http.get(baseUrl + "/api/v3/rootFolder", apiKey);
        if (result.isEmpty()) error("Unable to connect to " + app + " at " + baseUrl);
        try {
            var folders = http.getMapper().convertValue(result.get(), new TypeReference<List<RootFolder>>() {});
            return selectRootFolder(folders, preferred.isBlank() ? null : preferred, app);
        } catch (Exception e) {
            error("Unable to parse root folders from " + app + ": " + e.getMessage());
            return null;
        }
    }

    private String selectRootFolder(List<RootFolder> folders, String preferred, String app) {
        if (folders.isEmpty()) {
            log.warn("No root folders found in {} - adding items will fail until a root folder is configured", app);
            return "";
        }
        if (preferred != null) {
            return folders.stream()
                .filter(f -> f.accessible)
                .filter(f -> normalisePath(f.path).equals(normalisePath(preferred)))
                .map(f -> f.path)
                .findFirst()
                .orElseGet(() -> error("Root folder '" + preferred + "' not found in " + app));
        }
        return folders.stream().filter(f -> f.accessible).map(f -> f.path).findFirst()
            .orElseGet(() -> {
                log.warn("No accessible root folder in {} - adding items will fail until permissions are fixed", app);
                return folders.get(0).path;
            });
    }

    private int fetchQualityProfile(String baseUrl, String apiKey, String preferred, String app) {
        var result = http.get(baseUrl + "/api/v3/qualityprofile", apiKey);
        if (result.isEmpty()) error("Unable to fetch quality profiles from " + app);
        try {
            var profiles = http.getMapper().convertValue(result.get(), new TypeReference<List<QualityProfile>>() {});
            return selectQualityProfile(profiles, preferred.isBlank() ? null : preferred, app);
        } catch (Exception e) {
            error("Unable to parse quality profiles from " + app + ": " + e.getMessage());
            return -1;
        }
    }

    private int selectQualityProfile(List<QualityProfile> profiles, String preferred, String app) {
        if (profiles.isEmpty()) error("No quality profiles in " + app);
        if (profiles.size() == 1) { log.debug("Only one quality profile: {}", profiles.get(0).name); return profiles.get(0).id; }
        if (preferred == null) { log.debug("Multiple profiles in {}, using first", app); return profiles.get(0).id; }
        return profiles.stream()
            .filter(p -> p.name.equalsIgnoreCase(preferred))
            .map(p -> p.id)
            .findFirst()
            .orElseGet(() -> error("Quality profile '" + preferred + "' not found in " + app));
    }

    private int fetchLanguageProfile(String baseUrl, String apiKey) {
        var result = http.get(baseUrl + "/api/v3/languageprofile", apiKey);
        if (result.isEmpty()) { log.warn("Unable to find language profile, using 1 as default"); return 1; }
        try {
            var profiles = http.getMapper().convertValue(result.get(), new TypeReference<List<LanguageProfile>>() {});
            return profiles.isEmpty() ? 1 : profiles.get(0).id;
        } catch (Exception e) {
            log.warn("Unable to parse language profiles, using 1 as default");
            return 1;
        }
    }

    private Set<Integer> resolveTags(String baseUrl, String apiKey, String tagsConfig) {
        if (tagsConfig.isBlank()) return Set.of();
        return Arrays.stream(tagsConfig.split(","))
            .map(String::trim)
            .filter(t -> !t.isBlank())
            .map(tag -> resolveTag(baseUrl, apiKey, tag))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<Integer> resolveTag(String baseUrl, String apiKey, String tagName) {
        log.info("Fetching information for tag: {}", tagName);
        var body = Map.of("label", tagName.toLowerCase());
        var result = http.post(baseUrl + "/api/v3/tag", apiKey, body);
        if (result.isEmpty()) { log.warn("Failed to create/fetch tag '{}'", tagName); return Optional.empty(); }
        var idNode = result.get().get("id");
        return idNode != null ? Optional.of(idNode.asInt()) : Optional.empty();
    }

    // ── Plex helpers ──────────────────────────────────────────────────────────

    private Set<String> parsePlexTokens() {
        var token = plexToken.orElse("");
        if (token.isBlank()) { log.warn("Missing plex token"); return Set.of(); }
        return Arrays.stream(token.split(",")).map(String::trim).filter(t -> !t.isBlank())
            .collect(Collectors.toSet());
    }

    private Set<String> resolvePlexWatchlistUrls(Set<String> tokens) {
        Set<String> urls = new LinkedHashSet<>();
        // Legacy URLs from direct config
        plexWatchlist1.filter(u -> !u.isBlank() && isValidPlexRssUrl(u)).ifPresent(urls::add);
        plexWatchlist2.filter(u -> !u.isBlank() && isValidPlexRssUrl(u)).ifPresent(urls::add);

        for (var token : tokens) {
            generateRssUrl(token, "watchlist").ifPresent(u -> { log.info("Generated watchlist RSS feed for self: {}", u); urls.add(u); });
            if (!skipFriendSync)
                generateRssUrl(token, "friendsWatchlist").ifPresent(u -> { log.info("Generated watchlist RSS feed for friends: {}", u); urls.add(u); });
        }

        if (urls.isEmpty()) {
            log.warn("Missing RSS URL. Are you an active Plex Pass user?");
            log.warn("Real-time RSS sync disabled");
        }
        return urls;
    }

    private Optional<String> generateRssUrl(String token, String feedType) {
        var url = "https://discover.provider.plex.tv/rss?X-Plex-Token=" + token + "&X-Plex-Client-Identifier=watchlistarr";
        var body = Map.of("feedType", feedType);
        var result = http.post(url, body);
        if (result.isEmpty()) { log.warn("Unable to generate an RSS feed for {}", feedType); return Optional.empty(); }
        try {
            var feed = http.getMapper().treeToValue(result.get(), RssFeedGenerated.class);
            if (feed.RSSInfo.isEmpty()) return Optional.empty();
            return Optional.ofNullable(feed.RSSInfo.get(0).url);
        } catch (Exception e) {
            log.warn("Unable to decode RSS generation response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isValidPlexRssUrl(String url) {
        try {
            var host = new java.net.URI(url).getHost();
            return "rss.plex.tv".equals(host);
        } catch (Exception e) { return false; }
    }

    private String normalisePath(String path) {
        if (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        return path.replace("//", "/");
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    private String redact(AppConfig c) {
        return """

Configuration:
  refreshInterval: %d seconds

  Sonarr:
    baseUrl: %s  apiKey: REDACTED
    qualityProfileId: %d  rootFolder: %s
    bypassIgnored: %s  languageProfileId: %d  tagIds: %s

  Radarr:
    baseUrl: %s  apiKey: REDACTED
    qualityProfileId: %d  rootFolder: %s
    bypassIgnored: %s  tagIds: %s

  Plex:
    watchlistUrls: %s  tokens: %d token(s)
    skipFriendSync: %s  hasPlexPass: %s

  Delete:
    movie: %s  endedShow: %s  continuingShow: %s
    intervalDays: %d  deleteFiles: %s
""".formatted(
                c.refreshIntervalSeconds(),
                c.sonarr().baseUrl(), c.sonarr().qualityProfileId(), c.sonarr().rootFolder(),
                c.sonarr().bypassIgnored(), c.sonarr().languageProfileId(), c.sonarr().tagIds(),
                c.radarr().baseUrl(), c.radarr().qualityProfileId(), c.radarr().rootFolder(),
                c.radarr().bypassIgnored(), c.radarr().tagIds(),
                c.plex().watchlistUrls(), c.plex().tokens().size(), c.plex().skipFriendSync(), c.plex().hasPlexPass(),
                c.delete().movieDeleting(), c.delete().endedShowDeleting(), c.delete().continuingShowDeleting(),
                c.delete().deleteIntervalDays(), c.delete().deleteFiles()
        );
    }

    private <T> T error(String msg) {
        log.error(msg);
        throw new IllegalArgumentException(msg);
    }
}
