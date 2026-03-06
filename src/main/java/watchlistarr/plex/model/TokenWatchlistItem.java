package watchlistarr.plex.model;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenWatchlistItem {
    public final String title;
    public final String guid;
    public final String type;
    public final String key;
    @JsonProperty("Guid")
    public final List<Guid> Guid = List.of();

    @JsonCreator
    public TokenWatchlistItem(
            @JsonProperty("title") String title,
            @JsonProperty("guid") String guid,
            @JsonProperty("type") String type,
            @JsonProperty("key") String key) {
        this.title = title; this.guid = guid; this.type = type; this.key = key;
    }
}
