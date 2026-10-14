package flappytails;

import com.openggf.mods.state.SnapshotRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionReuseTest {
    @Test void repeatedFlightLookupsReuseOwnerDefinitionsWithoutChangingSeededDraws() throws Exception {
        Run run = new Run(Mode.CLASSIC, 0x5eed, 50, false);
        var shown = new IdentityHashMap<Zone, Boolean>(); var ahead = new IdentityHashMap<Zone, Boolean>();
        for (int i = 0; i < 1000; i++) { shown.put(run.zoneShown(), true); ahead.put(run.zoneAhead(), true); }
        String signature = seededSignature();
        System.out.println("Flappy definition identities: shown=" + shown.size() + " ahead=" + ahead.size() + " seed=" + signature);
        assertEquals("dc37149fbfb1a6dabb2162aa40416da5e605e6b6a8ca50703fb423ca3011c3a5", signature, "content and draw order pinned before preparation");
        assertEquals(1, shown.size()); assertEquals(1, ahead.size());
        Run independent = new Run(Mode.CLASSIC, 0x5eed, 50, false);
        assertEquals(run.zoneShown(), independent.zoneShown()); assertNotSame(run.zoneShown(), independent.zoneShown());
    }
    @Test void preparedLapVariantsAreBoundedImmutableAndSeparateForAnotherOwner() {
        Zone.Tour first = new Zone.Tour(), second = new Zone.Tour();
        assertEquals(first.zones(), second.zones()); assertNotSame(first.zones(), second.zones());
        assertThrows(UnsupportedOperationException.class, () -> first.zones().clear());
        for (int lap : new int[]{0, 1, 2, 4, 7, 20, 100}) for (int zone = 0; zone < Zone.TOUR_LENGTH; zone++) {
            int gates = lap * Zone.TOUR_LENGTH * Zone.GATES + zone * Zone.GATES;
            assertEquals(Zone.at(gates).forLap(lap), first.at(gates));
            assertNotSame(first.at(gates), second.at(gates));
        }
        assertSame(first.at(100 * Zone.TOUR_LENGTH * Zone.GATES), first.at(20 * Zone.TOUR_LENGTH * Zone.GATES));
    }

    private static String seededSignature() throws Exception {
        Course course = new Course(); course.reset(0x5eed, 0);
        for (int i = 0; i < 1200; i++) course.advance(0x240);
        var text = new StringBuilder().append(course.scrollFine());
        for (var gate : course.gates()) text.append('|').append(gate.number).append(',').append(gate.x).append(',').append(gate.centre).append(',').append(gate.gap);
        for (var ring : course.rings()) text.append("|r").append(ring.x).append(',').append(ring.y);
        for (var bird : course.badniks()) text.append("|b").append(bird.x).append(',').append(bird.baseY).append(',').append(bird.amplitude).append(',').append(bird.phase);
        var field = Course.class.getDeclaredField("random"); field.setAccessible(true);
        text.append('|').append(((SnapshotRandom) field.get(course)).snapshot());
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
