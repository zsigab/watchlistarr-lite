package watchlistarr.plex.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenWatchlist {
    @JsonProperty("MediaContainer")
    public MediaContainer MediaContainer;
    public TokenWatchlist() {}
}
