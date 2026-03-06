package watchlistarr.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ItemTest {

    @Test void getTvdbId_present() {
        var item = new Item("Test", List.of("tvdb://12345"), "show", null, null);
        assertEquals(Optional.of(12345L), item.getTvdbId());
    }

    @Test void getTvdbId_absent() {
        var item = new Item("Test", List.of("tmdb://99"), "movie", null, null);
        assertTrue(item.getTvdbId().isEmpty());
    }

    @Test void getTmdbId_present() {
        var item = new Item("Test", List.of("tmdb://67890"), "movie", null, null);
        assertEquals(Optional.of(67890L), item.getTmdbId());
    }

    @Test void getRadarrId_present() {
        var item = new Item("Test", List.of("radarr://1"), "movie", null, null);
        assertEquals(Optional.of(1L), item.getRadarrId());
    }

    @Test void getSonarrId_present() {
        var item = new Item("Test", List.of("sonarr://7"), "show", null, null);
        assertEquals(Optional.of(7L), item.getSonarrId());
    }

    @Test void matches_sameGuid_sameCategory() {
        var a = new Item("Show A", List.of("tvdb://1", "sonarr://10"), "show", null, null);
        var b = new Item("Show A", List.of("tvdb://1"), "show", null, null);
        assertTrue(a.matches(b));
    }

    @Test void matches_differentCategory_returnsFalse() {
        var a = new Item("X", List.of("tvdb://1"), "show", null, null);
        var b = new Item("X", List.of("tvdb://1"), "movie", null, null);
        assertFalse(a.matches(b));
    }

    @Test void matches_noSharedGuid_returnsFalse() {
        var a = new Item("X", List.of("tvdb://1"), "show", null, null);
        var b = new Item("X", List.of("tvdb://2"), "show", null, null);
        assertFalse(a.matches(b));
    }

    @Test void matches_nullGuids_returnsFalse() {
        var a = new Item("X", null, "show", null, null);
        var b = new Item("X", List.of("tvdb://1"), "show", null, null);
        assertFalse(a.matches(b));
    }
}
