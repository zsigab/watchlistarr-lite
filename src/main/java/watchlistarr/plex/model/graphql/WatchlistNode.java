package watchlistarr.plex.model.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import watchlistarr.plex.model.rest.TokenWatchlistItem;

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
