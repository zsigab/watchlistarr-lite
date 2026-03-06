package watchlistarr.plex.model.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenWatchlistFriend {
    public WatchlistData data;
    public TokenWatchlistFriend() {}
}
