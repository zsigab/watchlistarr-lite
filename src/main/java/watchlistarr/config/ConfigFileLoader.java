package watchlistarr.config;

import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Reads config/watchlistarr.yaml (original watchlistarr format) and maps keys
 * to our MicroProfile Config key names. Creates a template on first boot.
 * Priority: env vars > this file > application defaults.
 */
public class ConfigFileLoader {

    private static final Logger log = Logger.getLogger(ConfigFileLoader.class.getName());
    private static final String CONFIG_FILE = "/config/watchlistarr.yaml";

    private final Map<String, String> values = new HashMap<>();

    public static ConfigFileLoader load() {
        var loader = new ConfigFileLoader();
        var path = Paths.get(CONFIG_FILE);
        if (!Files.exists(path)) {
            loader.createTemplate(path);
        } else {
            loader.parse(path);
        }
        return loader;
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key)).filter(s -> !s.isBlank());
    }

    // ── Template creation ─────────────────────────────────────────────────────

    private void createTemplate(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (var in = ConfigFileLoader.class.getResourceAsStream("/config-template.yaml");
                 var out = Files.newOutputStream(path)) {
                if (in == null) { log.warning("Config template resource not found"); return; }
                in.transferTo(out);
            }
            log.info("Created config template at " + path.toAbsolutePath());
        } catch (Exception e) {
            log.warning("Could not create config template: " + e.getMessage());
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void parse(Path path) {
        try (var reader = new FileReader(path.toFile())) {
            Map<String, Object> root = new Yaml().load(reader);
            if (root == null) { log.info("Config file is empty: " + path); return; }

            map(root, "interval.seconds",          "refresh.interval-seconds");

            map(root, "sonarr.baseUrl",            "sonarr.base-url");
            map(root, "sonarr.apikey",             "sonarr.api-key");
            map(root, "sonarr.qualityProfile",     "sonarr.quality-profile");
            map(root, "sonarr.rootFolder",         "sonarr.root-folder");
            map(root, "sonarr.bypassIgnored",      "sonarr.bypass-ignored");
            map(root, "sonarr.seasonMonitoring",   "sonarr.season-monitoring");
            mapList(root, "sonarr.tags",           "sonarr.tags");

            map(root, "radarr.baseUrl",            "radarr.base-url");
            map(root, "radarr.apikey",             "radarr.api-key");
            map(root, "radarr.qualityProfile",     "radarr.quality-profile");
            map(root, "radarr.rootFolder",         "radarr.root-folder");
            map(root, "radarr.bypassIgnored",      "radarr.bypass-ignored");
            mapList(root, "radarr.tags",           "radarr.tags");

            map(root, "plex.token",                "plex.token");
            map(root, "plex.watchlist1",           "plex.watchlist1");
            map(root, "plex.watchlist2",           "plex.watchlist2");
            map(root, "plex.skipfriendsync",       "plex.skip-friend-sync");

            map(root, "delete.movie",              "delete.movie");
            map(root, "delete.endedShow",          "delete.ended-show");
            map(root, "delete.continuingShow",     "delete.continuing-show");
            mapLiteralKey(root, "delete", "interval.days", "delete.interval-days");
            map(root, "delete.deleteFiles",        "delete.delete-files");

            log.info("Loaded " + values.size() + " values from " + path.toAbsolutePath());
        } catch (Exception e) {
            log.warning("Could not read config file " + path + ": " + e.getMessage());
        }
    }

    /** Navigate a dot-separated path through nested maps, treating each segment as a literal key. */
    @SuppressWarnings("unchecked")
    private void map(Map<String, Object> root, String dotPath, String targetKey) {
        Object node = root;
        for (var part : dotPath.split("\\.")) {
            if (!(node instanceof Map<?, ?> m)) return;
            node = m.get(part);
        }
        if (node != null && !(node instanceof Map)) {
            values.put(targetKey, node.toString());
        }
    }

    /** Like map(), but joins a YAML list into a comma-separated string. */
    @SuppressWarnings("unchecked")
    private void mapList(Map<String, Object> root, String dotPath, String targetKey) {
        Object node = root;
        for (var part : dotPath.split("\\.")) {
            if (!(node instanceof Map<?, ?> m)) return;
            node = m.get(part);
        }
        if (node instanceof List<?> list) {
            values.put(targetKey, list.stream().map(Object::toString).collect(Collectors.joining(",")));
        } else if (node != null) {
            values.put(targetKey, node.toString());
        }
    }

    /** For YAML keys that literally contain a dot, e.g. "interval.days" nested under "delete". */
    @SuppressWarnings("unchecked")
    private void mapLiteralKey(Map<String, Object> root, String parent, String literalKey, String targetKey) {
        var parentNode = root.get(parent);
        if (!(parentNode instanceof Map<?, ?> m)) return;
        var val = m.get(literalKey);
        if (val != null) values.put(targetKey, val.toString());
    }
}
