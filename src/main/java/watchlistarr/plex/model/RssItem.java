package watchlistarr.plex.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RssItem {
    public String title;
    public List<String> guids;
    public String category;

    public RssItem() {}
}
