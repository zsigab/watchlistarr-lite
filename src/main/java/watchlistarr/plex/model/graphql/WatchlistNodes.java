package watchlistarr.plex.model.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WatchlistNodes {
    public List<WatchlistNode> nodes = List.of();
    public PageInfo pageInfo;
    public WatchlistNodes() {}
}
