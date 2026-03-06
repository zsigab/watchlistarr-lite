package watchlistarr.plex.model.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Watchlist {
    public List<RssItem> items = List.of();
    public Watchlist() {}
}
