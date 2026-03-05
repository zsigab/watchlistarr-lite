package watchlistarr.config;
import java.util.Set;

public record SonarrConfig(
        String baseUrl,
        String apiKey,
        int qualityProfileId,String rootFolder,
        boolean bypassIgnored,
        String seasonMonitoring,
        int languageProfileId,
        Set<Integer> tagIds
) {
}
