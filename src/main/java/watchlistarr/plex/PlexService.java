package watchlistarr.plex;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import watchlistarr.config.PlexConfig;
import watchlistarr.http.HttpService;
import watchlistarr.model.GraphQLQuery;
import watchlistarr.model.Item;
import watchlistarr.plex.model.*;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PlexService {

    private static final Logger log = LoggerFactory.getLogger(PlexService.class);
    private static final int CONTAINER_SIZE = 300;

    @Inject HttpService http;

    // ── Ping ─────────────────────────────────────────────────────────────────

    public void ping(PlexConfig config) {
        for (var token : config.tokens()) {
            var url = "https://plex.tv/api/v2/ping?X-Plex-Token=" + token + "&X-Plex-Client-Identifier=watchlistarr";
            var result = http.get(url);
            if (result.isPresent()) {
                log.info("Pinged plex.tv to update access token expiry");
            } else {
                log.warn("Unable to ping plex.tv to update access token expiry");
            }
        }
    }

    // ── RSS watchlist ─────────────────────────────────────────────────────────

    public Set<Item> fetchWatchlistFromRss(String url) {
        var cacheBuster = UUID.randomUUID().toString().substring(0, 12);
        var fullUrl = url + (url.contains("?") ? "&" : "?") + "format=json&cache_buster=" + cacheBuster;
        var result = http.get(fullUrl);
        if (result.isEmpty()) {
            log.warn("Unable to fetch watchlist from Plex (RSS)");
            return Set.of();
        }
        try {
            var watchlist = http.getMapper().treeToValue(result.get(), Watchlist.class);
            return watchlist.items != null ? watchlist.items : Set.of();
        } catch (Exception e) {
            log.warn("Unable to decode RSS watchlist from Plex: {}", e.getMessage());
            return Set.of();
        }
    }

    // ── Self watchlist (token-based, paginated) ───────────────────────────────

    public Set<Item> getSelfWatchlist(PlexConfig config) {
        Set<Item> all = new HashSet<>();
        for (var token : config.tokens()) {
            all.addAll(getSelfWatchlistForToken(config, token, 0));
        }
        return all;
    }

    private Set<Item> getSelfWatchlistForToken(PlexConfig config, String token, int start) {
        var url = "https://discover.provider.plex.tv/library/sections/watchlist/all"
            + "?X-Plex-Token=" + token
            + "&X-Plex-Container-Start=" + start
            + "&X-Plex-Container-Size=" + CONTAINER_SIZE;

        var result = http.get(url);
        if (result.isEmpty()) {
            log.warn("Unable to fetch self watchlist from Plex token");
            return Set.of();
        }
        try {
            var watchlist = http.getMapper().treeToValue(result.get(), TokenWatchlist.class);
            var container = watchlist.MediaContainer;
            if (container == null) return Set.of();

            var items = toItems(config, container.Metadata);
            if (container.totalSize > start + CONTAINER_SIZE) {
                items.addAll(getSelfWatchlistForToken(config, token, start + CONTAINER_SIZE));
            }
            return items;
        } catch (Exception e) {
            log.warn("Unable to decode self watchlist from Plex: {}", e.getMessage());
            return Set.of();
        }
    }

    // ── Others' watchlists (GraphQL, paginated) ───────────────────────────────

    public Set<Item> getOthersWatchlist(PlexConfig config) {
        var friends = getFriends(config);
        Set<Item> all = new HashSet<>();
        for (var entry : friends.entrySet()) {
            var friend = entry.getKey();
            var token  = entry.getValue();
            var watchlistItems = getWatchlistIdsForUser(config, token, friend, null);
            all.addAll(toItemsFromWatchlistItems(config, watchlistItems));
        }
        return all;
    }

    private Map<User, String> getFriends(PlexConfig config) {
        Map<User, String> friends = new LinkedHashMap<>();
        for (var token : config.tokens()) {
            var url = "https://community.plex.tv/api";
            var query = new GraphQLQuery(
                """
                query GetAllFriends {
                  allFriendsV2 {
                    user { id username }
                  }
                }""");
            var result = http.post(url, token, query);
            if (result.isEmpty()) { log.warn("Unable to fetch friends from Plex"); continue; }
            try {
                var users = http.getMapper().treeToValue(result.get(), Users.class);
                users.data.allFriendsV2.stream()
                    .map(f -> f.user)
                    .filter(Objects::nonNull)
                    .forEach(u -> friends.put(u, token));
            } catch (Exception e) {
                log.warn("Unable to decode friends response: {}", e.getMessage());
            }
        }
        return friends;
    }

    private Set<TokenWatchlistItem> getWatchlistIdsForUser(PlexConfig config, String token, User user, String cursor) {
        var url = "https://community.plex.tv/api";
        var variables = cursor == null
            ? Map.of("first", 100, "uuid", user.id)
            : Map.of("first", 100, "uuid", user.id, "after", cursor);

        var query = new GraphQLQuery("""
            query GetWatchlistHub ($uuid: ID = "", $first: PaginationInt!, $after: String) {
              user(id: $uuid) {
                watchlist(first: $first, after: $after) {
                  nodes { ...itemFields }
                  pageInfo { hasNextPage endCursor }
                }
              }
            }
            fragment itemFields on MetadataItem { id title type }""", variables);

        var result = http.post(url, token, query);
        if (result.isEmpty()) { log.warn("Unable to fetch watchlist for user {}", user.username); return Set.of(); }
        try {
            var response = http.getMapper().treeToValue(result.get(), TokenWatchlistFriend.class);
            var watchlist = response.data.user.watchlist;
            var items = watchlist.nodes.stream()
                    .map(WatchlistNode::toTokenWatchlistItem)
                    .collect(Collectors.toCollection(HashSet::new));
            if (watchlist.pageInfo.hasNextPage && watchlist.pageInfo.endCursor != null && !watchlist.pageInfo.endCursor.isBlank()) {
                items.addAll(getWatchlistIdsForUser(config, token, user, watchlist.pageInfo.endCursor));
            }
            return items;
        } catch (Exception e) {
            log.warn("Unable to decode watchlist for user {}: {}", user.username, e.getMessage());
            return Set.of();
        }
    }

    // ── Item resolution (metadata lookup) ────────────────────────────────────

    private Set<Item> toItems(PlexConfig config, List<TokenWatchlistItem> rawItems) {
        var result = new HashSet<Item>();
        for (var raw : rawItems) {
            try {
                result.add(resolveItem(config, raw));
            } catch (Exception e) {
                log.warn("Found item {} on the watchlist, but cannot find it in Plex's database", raw.title);
            }
        }
        return result;
    }

    private Set<Item> toItemsFromWatchlistItems(PlexConfig config, Set<TokenWatchlistItem> rawItems) {
        return toItems(config, new ArrayList<>(rawItems));
    }

    private Item resolveItem(PlexConfig config, TokenWatchlistItem raw) {
        var key = cleanKey(raw.key);
        var token = config.tokens().stream().findFirst().orElse("unknown");
        var url = "https://discover.provider.plex.tv" + key + "?X-Plex-Token=" + token;
        var result = http.get(url);
        if (result.isEmpty()) throw new RuntimeException("No response from Plex metadata for " + raw.title);
        try {
            var watchlist = http.getMapper().treeToValue(result.get(), TokenWatchlist.class);
            var guids = watchlist.MediaContainer.Metadata.stream()
                .flatMap(m -> m.Guid.stream())
                .map(g -> g.id)
                .collect(Collectors.toList());
            return new Item(raw.title, guids, raw.type, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode metadata for " + raw.title + ": " + e.getMessage());
        }
    }

    private String cleanKey(String key) {
        return key != null && key.endsWith("/children") ? key.substring(0, key.length() - 9) : key;
    }
}
