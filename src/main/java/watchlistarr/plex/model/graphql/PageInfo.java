package watchlistarr.plex.model.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PageInfo {
    public boolean hasNextPage;
    public String endCursor;
    public PageInfo() {}
    public Optional<String> getEndCursor() { return Optional.ofNullable(endCursor); }
}
