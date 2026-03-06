package watchlistarr.plex.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaContainer {
    @JsonProperty("Metadata")
    public List<TokenWatchlistItem> Metadata = List.of();
    public int totalSize;
    public MediaContainer() {}
}
