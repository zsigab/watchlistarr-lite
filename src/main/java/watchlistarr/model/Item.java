package watchlistarr.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {
    public String title;
    public List<String> guids;
    public String category;
    public Boolean ended;
    public String username;

    public Item() {}

    public Item(String title, List<String> guids, String category, Boolean ended, String username) {
        this.title = title;
        this.guids = guids;
        this.category = category;
        this.ended = ended;
        this.username = username;
    }

    public Optional<Long> getTvdbId()   { return extractId("tvdb://"); }
    public Optional<Long> getTmdbId()   { return extractId("tmdb://"); }
    public Optional<Long> getRadarrId() { return extractId("radarr://"); }
    public Optional<Long> getSonarrId() { return extractId("sonarr://"); }

    private Optional<Long> extractId(String prefix) {
        if (guids == null) {
            return Optional.empty();
        }
        return guids.stream()
            .filter(g -> g.startsWith(prefix))
            .map(g -> g.substring(prefix.length()))
            .map(s -> {
                try {
                    return Long.parseLong(s);
                }
                catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .findFirst();
    }

    public boolean matches(Item that) {
        if (that == null || !Objects.equals(this.category, that.category)) {
            return false;
        }
        if (that.guids == null || this.guids == null) {
            return false;
        }
        return that.guids.stream().anyMatch(guid -> this.guids.contains(guid));
    }

    @Override
    public String toString() {
        return "Item{title='" + title + "', category='" + category + "', ended=" + ended + ", username=" + username + "}";
    }
}
