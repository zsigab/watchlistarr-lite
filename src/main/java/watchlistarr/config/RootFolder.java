package watchlistarr.config;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RootFolder {
    public String path;
    public boolean accessible;
    public RootFolder() {}
}
