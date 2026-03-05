package watchlistarr.config;
import java.util.Set;

public record RadarrConfig(
        String baseUrl,
        String apiKey,
        int qualityProfileId,
        String rootFolder,
        boolean bypassIgnored,
        Set<Integer> tagIds
) {
}
