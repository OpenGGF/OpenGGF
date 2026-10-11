package threeislands.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SaveCodecTest {
    @Test
    void everythingThatMattersRoundTrips() {
        Progress progress = new Progress(77);
        progress.join(HeroId.TAILS);
        progress.hero(HeroId.SONIC).gainXp(500);
        progress.hero(HeroId.TAILS).setHp(3);
        progress.addRings(321);
        progress.addItem(Item.FLAME_SHIELD, 4);
        progress.addEmerald(0);
        progress.addEmerald(5);
        progress.clear(Zone.GREEN_HILL);
        progress.setIsland(Island.WEST);
        progress.markSeen("prologue");
        progress.setResume(Zone.EMERALD_HILL, 1234);
        progress.rng.nextLong();
        for (int i = 0; i < 99; i++) progress.tick();
        String text = SaveCodec.encode(progress);
        Progress loaded = SaveCodec.decode(text);
        assertEquals(text, SaveCodec.encode(loaded), "re-encoding a loaded save is identical");
        assertEquals(progress.rng.nextLong(), loaded.rng.nextLong(), "the random stream continues");
        assertEquals(3, loaded.hero(HeroId.TAILS).hp());
        assertEquals(Zone.EMERALD_HILL.ordinal(), loaded.resumeZone());
        assertTrue(loaded.hasJoined(HeroId.TAILS));
        assertTrue(loaded.seen("prologue"));
    }

    @Test
    void unknownKeysAreIgnoredAndDamageIsRejected() {
        String text = SaveCodec.encode(new Progress(1)) + "future.feature=7\n";
        assertEquals(40, SaveCodec.decode(text).rings());
        assertThrows(IllegalArgumentException.class, () -> SaveCodec.decode("not a save"));
        assertThrows(RuntimeException.class, () -> SaveCodec.decode(SaveCodec.HEADER + "\nrings=lots\n"));
        assertThrows(RuntimeException.class, () -> SaveCodec.decode(SaveCodec.HEADER + "\nhero.SHADOW=1,0,1,1\n"));
        assertThrows(IllegalArgumentException.class, () -> SaveCodec.decode(SaveCodec.HEADER + "\nscene=../../x\n"));
    }

    @Test
    void loadedValuesAreClampedToTheRules() {
        Progress loaded = SaveCodec.decode(SaveCodec.HEADER + "\nrings=999999\njoined=0\nhero.SONIC=99,0,99999,99999\n");
        assertEquals(Progress.MAX_RINGS, loaded.rings());
        assertTrue(loaded.hasJoined(HeroId.SONIC), "Sonic is always in the party");
        assertEquals(Hero.MAX_LEVEL, loaded.hero(HeroId.SONIC).level());
        assertEquals(loaded.hero(HeroId.SONIC).maxHp(), loaded.hero(HeroId.SONIC).hp());
    }
}
