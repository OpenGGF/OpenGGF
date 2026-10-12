package sitarhero;

import sitarhero.model.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionReuseTest {
    @Test void songRoleLookupsReuseImmutableMetadataAndPreserveCatalogueOrder() throws Exception {
        List<SongSpec> songs = SongCatalog.all(); SongSpec first = songs.getFirst();
        var roles = new IdentityHashMap<List<Role>, Boolean>();
        for (int i = 0; i < 1000; i++) roles.put(first.availableRoles(), true);
        var text = new StringBuilder();
        for (SongSpec song : songs) text.append(song.id()).append('|').append(song.label()).append('|').append(song.game())
                .append('|').append(song.musicId()).append('|').append(song.durationFrames()).append('|').append(song.zone())
                .append('|').append(song.act()).append('|').append(song.availableRoles());
        String signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
        System.out.println("Sitar role-list identities=" + roles.size() + " catalogue=" + signature);
        assertEquals("e74c6403a6d6fa031740eccca0f3f3917c6a5649ebe49023247bce50689b62d1", signature, "content and draw order pinned before preparation");
        assertEquals(1, roles.size());
        SongSpec independent = SongCatalog.all().getFirst(); assertEquals(first, independent); assertNotSame(first.availableRoles(), independent.availableRoles());
        assertThrows(UnsupportedOperationException.class, () -> first.availableRoles().clear());
    }
    @Test void everyRomSelectionReusesItsSnapshotAndAnotherSceneOwnsSeparateDefinitions() {
        SongCatalog.Prepared first = new SongCatalog.Prepared(), second = new SongCatalog.Prepared();
        for (int mask = 0; mask < 8; mask++) {
            var games = new java.util.ArrayList<String>();
            if ((mask & 1) != 0) games.add("s1"); if ((mask & 2) != 0) games.add("s2"); if ((mask & 4) != 0) games.add("s3k");
            assertEquals(SongCatalog.available(games), first.available(games));
            assertSame(first.available(games), first.available(games));
            assertThrows(UnsupportedOperationException.class, () -> first.available(games).clear());
        }
        assertEquals(first.all(), second.all()); assertNotSame(first.all(), second.all());
        for (SongSpec song : first.all()) {
            assertEquals(SongCatalog.arrangement(song.id()), first.arrangement(song.id()));
            assertNotSame(song.availableRoles(), second.all().stream().filter(other -> other.id().equals(song.id())).findFirst().orElseThrow().availableRoles());
        }
        assertSame(first.available(List.of("s1", "s3k")), first.available(List.of("s3k", "s1", "unknown")));
    }

}
