package watchlistarr.config;
import java.util.Set;

public record PlexConfig(
        Set<String> watchlistUrls,
        Set<String> tokens,
        boolean skipFriendSync,
        boolean hasPlexPass
) {
}
