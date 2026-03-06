package watchlistarr.plex;

import com.fasterxml.jackson.databind.JsonNode;
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
        for (String token : config.tokens()) {
            String url = "https://plex.tv/api/v2/ping?X-Plex-Token=" + token + "&X-Plex-Client-Identifier=watchlistarr";
            Optional<JsonNode> result = http.get(url);
            if (result.isPresent()) {
                log.info("Pinged plex.tv to update access token expiry");
            }
            else {
                log.warn("Unable to ping plex.tv to update access token expiry");
            }
        }
    }

    // ── RSS watchlist ─────────────────────────────────────────────────────────

    public Set<Item> fetchWatchlistFromRss(String url) {
        String cacheBuster = UUID.randomUUID().toString().substring(0, 12);
        String fullUrl = url + (url.contains("?") ? "&" : "?") + "format=json&cache_buster=" + cacheBuster;
        Optional<JsonNode> result = http.get(fullUrl);
        if (result.isEmpty()) {
            log.warn("Unable to fetch watchlist from Plex (RSS)");
            return Set.of();
        }
        try {
            Watchlist watchlist = http.getMapper().treeToValue(result.get(), Watchlist.class);

            return watchlist.items.stream()
                    .map(item -> new Item(item.title, item.guids, item.category, null, "self"))
                    .collect(Collectors.toSet());
        }
        catch (Exception e) {
            log.warn("Unable to decode RSS watchlist from Plex: {}", e.getMessage());
            return Set.of();
        }
    }

    // ── Self watchlist (token-based, paginated) ───────────────────────────────

    public Set<Item> getSelfWatchlist(PlexConfig config) {
        return config.tokens().stream()
            .flatMap(token -> getSelfWatchlistForToken(config, token, 0).stream())
            .collect(Collectors.toSet());
    }

    private Set<Item> getSelfWatchlistForToken(PlexConfig config, String token, int start) {
        String url = "https://discover.provider.plex.tv/library/sections/watchlist/all"
            + "?X-Plex-Token=" + token
            + "&X-Plex-Container-Start=" + start
            + "&X-Plex-Container-Size=" + CONTAINER_SIZE;

        Optional<JsonNode> result = http.get(url);
        if (result.isEmpty()) {
            log.warn("Unable to fetch self watchlist from Plex token");
            return Set.of();
        }
        try {
            TokenWatchlist watchlist = http.getMapper().treeToValue(result.get(), TokenWatchlist.class);
            MediaContainer container = watchlist.MediaContainer;
            if (container == null) {
                return Set.of();
            }
            Set<Item> items = toItems(config, container.Metadata, "self");
            if (container.totalSize > start + CONTAINER_SIZE) {
                items.addAll(getSelfWatchlistForToken(config, token, start + CONTAINER_SIZE));
            }
            return items;
        }
        catch (Exception e) {
            log.warn("Unable to decode self watchlist from Plex: {}", e.getMessage());
            return Set.of();
        }
    }

    // ── Others' watchlists (GraphQL, paginated) ───────────────────────────────

    public Set<Item> getOthersWatchlist(PlexConfig config) {
        Map<User, String> friends = getFriends(config);
        Set<Item> all = new HashSet<>();
        for (Map.Entry<User, String> entry : friends.entrySet()) {
            User friend = entry.getKey();
            String token  = entry.getValue();
            Set<TokenWatchlistItem> watchlistItems = getWatchlistIdsForUser(config, token, friend, null);
            all.addAll(toItemsFromWatchlistItems(config, watchlistItems, friend.username));
        }
        return all;
    }

    private Map<User, String> getFriends(PlexConfig config) {
        Map<User, String> friends = new LinkedHashMap<>();
        for (String token : config.tokens()) {
            String url = "https://community.plex.tv/api";
            GraphQLQuery query = new GraphQLQuery(
                """
                query GetAllFriends {
                  allFriendsV2 {
                    user { id username }
                  }
                }""");
            Optional<JsonNode> result = http.post(url, token, query);
            if (result.isEmpty()) {
                log.warn("Unable to fetch friends from Plex");
                continue;
            }
            try {
                Users users = http.getMapper().treeToValue(result.get(), Users.class);
                users.data.allFriendsV2.stream()
                    .map(f -> f.user)
                    .filter(Objects::nonNull)
                    .forEach(u -> friends.put(u, token));
            }
            catch (Exception e) {
                log.warn("Unable to decode friends response: {}", e.getMessage());
            }
        }
        return friends;
    }

    private Set<TokenWatchlistItem> getWatchlistIdsForUser(PlexConfig config, String token, User user, String cursor) {
        String url = "https://community.plex.tv/api";
        Map<String, Object> variables = cursor == null
            ? Map.of("first", 100, "uuid", user.id)
            : Map.of("first", 100, "uuid", user.id, "after", cursor);

        GraphQLQuery query = new GraphQLQuery("""
            query GetWatchlistHub ($uuid: ID = "", $first: PaginationInt!, $after: String) {
              user(id: $uuid) {
                watchlist(first: $first, after: $after) {
                  nodes { ...itemFields }
                  pageInfo { hasNextPage endCursor }
                }
              }
            }
            fragment itemFields on MetadataItem { id title type }""", variables);

        Optional<JsonNode> result = http.post(url, token, query);
        if (result.isEmpty()) {
            log.warn("Unable to fetch watchlist for user {}", user.username);
            return Set.of();
        }
        try {
            TokenWatchlistFriend response = http.getMapper().treeToValue(result.get(), TokenWatchlistFriend.class);
            WatchlistNodes watchlist = response.data.user.watchlist;

            Set<TokenWatchlistItem> items = watchlist.nodes.stream()
                    .map(WatchlistNode::toTokenWatchlistItem)
                    .collect(Collectors.toCollection(HashSet::new));

            if (watchlist.pageInfo.hasNextPage && watchlist.pageInfo.endCursor != null && !watchlist.pageInfo.endCursor.isBlank()) {
                items.addAll(getWatchlistIdsForUser(config, token, user, watchlist.pageInfo.endCursor));
            }
            return items;
        }
        catch (Exception e) {
            log.warn("Unable to decode watchlist for user {}: {}", user.username, e.getMessage());
            return Set.of();
        }
    }

    // ── Item resolution (metadata lookup) ────────────────────────────────────

    private Set<Item> toItems(PlexConfig config, List<TokenWatchlistItem> rawItems, String username) {
        Set<Item> result = new HashSet<>();
        for (TokenWatchlistItem raw : rawItems) {
            try {
                result.add(resolveItem(config, raw, username));
            }
            catch (Exception e) {
                log.warn("Found item {} on the watchlist, but cannot find it in Plex's database", raw.title);
            }
        }
        return result;
    }

    private Set<Item> toItemsFromWatchlistItems(PlexConfig config, Set<TokenWatchlistItem> rawItems, String username) {
        return toItems(config, new ArrayList<>(rawItems), username);
    }

    private Item resolveItem(PlexConfig config, TokenWatchlistItem raw, String username) {
        String key = cleanKey(raw.key);
        String token = config.tokens().stream().findFirst().orElse("unknown");
        String url = "https://discover.provider.plex.tv" + key + "?X-Plex-Token=" + token;
        Optional<JsonNode> result = http.get(url);
        if (result.isEmpty()) {
            throw new RuntimeException("No response from Plex metadata for " + raw.title);
        }
        try {
            TokenWatchlist watchlist = http.getMapper().treeToValue(result.get(), TokenWatchlist.class);
            List<String> guids = watchlist.MediaContainer.Metadata.stream()
                .flatMap(m -> m.Guid.stream())
                .map(g -> g.id)
                .collect(Collectors.toList());
            return new Item(raw.title, guids, raw.type, null, username);
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to decode metadata for " + raw.title + ": " + e.getMessage());
        }
    }

    private String cleanKey(String key) {
        return key != null && key.endsWith("/children") ? key.substring(0, key.length() - 9) : key;
    }
}
