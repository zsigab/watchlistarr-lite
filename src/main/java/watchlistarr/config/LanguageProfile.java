package watchlistarr.config;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LanguageProfile {
    public String name;
    public int id;
    public LanguageProfile() {}
}
