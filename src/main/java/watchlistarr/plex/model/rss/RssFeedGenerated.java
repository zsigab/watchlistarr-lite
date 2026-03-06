package watchlistarr.plex.model.rss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RssFeedGenerated {
    public List<RssInfo> RSSInfo = List.of();
    public RssFeedGenerated() {}
}
