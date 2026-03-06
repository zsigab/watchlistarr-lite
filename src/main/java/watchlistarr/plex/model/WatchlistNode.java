package watchlistarr.plex.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WatchlistNode {
    public String id;
    public String title;
    public String type;

    public TokenWatchlistItem toTokenWatchlistItem() {
        return new TokenWatchlistItem(title, id, type != null ? type.toLowerCase() : type,
            "/library/metadata/" + id);
    }
}
