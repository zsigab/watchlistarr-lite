package watchlistarr.config;
import java.util.Set;

public record SonarrConfig(
        String baseUrl,
        String apiKey,
        int qualityProfileId,
        String rootFolder,
        boolean bypassIgnored,
        String seasonMonitoring,
        Set<Integer> tagIds
) {
}
