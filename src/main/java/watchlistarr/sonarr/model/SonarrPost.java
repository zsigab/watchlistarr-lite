package watchlistarr.sonarr.model;
import java.util.List;

public class SonarrPost {
    public String title;
    public long tvdbId;
    public int qualityProfileId;
    public String rootFolderPath;
    public SonarrAddOptions addOptions;
    public boolean monitored = true;
    public List<Integer> tags;

    public SonarrPost(String title, long tvdbId, int qualityProfileId, String rootFolderPath,
                      SonarrAddOptions addOptions, List<Integer> tags) {
        this.title = title;
        this.tvdbId = tvdbId;
        this.qualityProfileId = qualityProfileId;
        this.rootFolderPath = rootFolderPath;
        this.addOptions = addOptions;
        this.tags = tags;
    }
}
