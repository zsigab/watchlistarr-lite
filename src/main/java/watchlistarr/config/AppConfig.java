package watchlistarr.config;

public record AppConfig(
        long refreshIntervalSeconds,
        SonarrConfig sonarr,
        RadarrConfig radarr,
        PlexConfig plex,
        DeleteConfig delete
) {
}
