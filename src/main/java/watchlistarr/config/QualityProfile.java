package watchlistarr.config;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QualityProfile {
    public String name;
    public int id;
    public QualityProfile() {}
}
