package watchlistarr;

import io.quarkus.runtime.annotations.RegisterForReflection;
import watchlistarr.config.LanguageProfile;
import watchlistarr.config.QualityProfile;
import watchlistarr.config.RootFolder;
import watchlistarr.model.GraphQLQuery;
import watchlistarr.model.Item;
import watchlistarr.plex.model.graphql.FriendsData;
import watchlistarr.plex.model.graphql.FriendV2;
import watchlistarr.plex.model.graphql.PageInfo;
import watchlistarr.plex.model.graphql.TokenWatchlistFriend;
import watchlistarr.plex.model.graphql.User;
import watchlistarr.plex.model.graphql.Users;
import watchlistarr.plex.model.graphql.WatchlistData;
import watchlistarr.plex.model.graphql.WatchlistNode;
import watchlistarr.plex.model.graphql.WatchlistNodes;
import watchlistarr.plex.model.graphql.WatchlistUserData;
import watchlistarr.plex.model.rest.Guid;
import watchlistarr.plex.model.rest.MediaContainer;
import watchlistarr.plex.model.rest.TokenWatchlist;
import watchlistarr.plex.model.rest.TokenWatchlistItem;
import watchlistarr.plex.model.rss.RssFeedGenerated;
import watchlistarr.plex.model.rss.RssInfo;
import watchlistarr.plex.model.rss.RssItem;
import watchlistarr.plex.model.rss.Watchlist;
import watchlistarr.radarr.model.AddOptions;
import watchlistarr.radarr.model.RadarrMovie;
import watchlistarr.radarr.model.RadarrMovieExclusion;
import watchlistarr.radarr.model.RadarrPost;
import watchlistarr.sonarr.model.SonarrAddOptions;
import watchlistarr.sonarr.model.SonarrPost;
import watchlistarr.sonarr.model.SonarrSeries;

@RegisterForReflection(targets = {
    // Core model
    Item.class,
    GraphQLQuery.class,

    // Config models (Jackson-deserialized from API responses)
    QualityProfile.class,
    RootFolder.class,
    LanguageProfile.class,

    // Plex RSS models
    RssItem.class,
    Watchlist.class,
    RssInfo.class,
    RssFeedGenerated.class,

    // Plex REST models
    Guid.class,
    TokenWatchlistItem.class,
    MediaContainer.class,
    TokenWatchlist.class,

    // Plex GraphQL models
    User.class,
    FriendV2.class,
    FriendsData.class,
    Users.class,
    PageInfo.class,
    WatchlistNode.class,
    WatchlistNodes.class,
    WatchlistUserData.class,
    WatchlistData.class,
    TokenWatchlistFriend.class,

    // Sonarr models
    SonarrSeries.class,
    SonarrAddOptions.class,
    SonarrPost.class,

    // Radarr models
    RadarrMovie.class,
    RadarrMovieExclusion.class,
    AddOptions.class,
    RadarrPost.class,
})
public class NativeConfig {
}
