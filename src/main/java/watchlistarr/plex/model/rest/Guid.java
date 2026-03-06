package watchlistarr.plex.model.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Guid {
    public String id;
    public Guid() {}
}
