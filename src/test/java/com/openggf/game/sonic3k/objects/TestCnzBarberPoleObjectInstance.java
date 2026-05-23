package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzBarberPoleObjectInstance {

    @Test
    void normalRelatchUsesRomUnsignedWordCompareForInnerTrackFlag() {
        CnzBarberPoleObjectInstance pole = new CnzBarberPoleObjectInstance(
                new ObjectSpawn(0x0F70, 0x0810, Sonic3kObjectIds.CNZ_BARBER_POLE, 0, 0, false, 0));
        pole.setServices(new TestObjectServices());

        TestPlayableSprite tails = new TestPlayableSprite();
        tails.setHeight(30);
        tails.applyCustomRadii(9, 15);
        tails.setCentreX((short) 0x0F4B);
        tails.setCentreY((short) 0x07B2);
        tails.setSubpixelRaw(0x8000, 0x8600);
        tails.setOnObject(true);
        tails.setLatchedSolidObjectId(Sonic3kObjectIds.CNZ_BARBER_POLE);
        tails.setAir(false);

        pole.update(0x0638, tails);
        tails.setXSpeed((short) 0x0687);
        tails.setGSpeed((short) 0x093C);
        pole.update(0x0639, tails);

        assertTrue(tails.isOnObject());
        assertFalse(tails.getAir());
        assertEquals(0x0F4E, tails.getCentreX() & 0xFFFF);
        assertEquals(0x07B9, tails.getCentreY() & 0xFFFF);
        assertEquals(0x0E, tails.getFlipAngle() & 0xFF);
    }

    @Test
    void queryOnlySidekickParticipatesInBarberPoleLatch() {
        CnzBarberPoleObjectInstance pole = new CnzBarberPoleObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.CNZ_BARBER_POLE, 0, 0, false, 0));
        TestPlayableSprite main = new TestPlayableSprite();
        main.setCentreX((short) 0);
        main.setCentreY((short) 0);
        TestPlayableSprite sidekick = new TestPlayableSprite();
        sidekick.setCentreX((short) 0x0100);
        sidekick.setCentreY((short) 0x00C9);
        pole.setServices(new QueryOnlyPlayerServices(main, List.of(sidekick)));

        pole.update(0, main);

        assertTrue(sidekick.isOnObject());
        assertFalse(sidekick.getAir());
        assertEquals(Sonic3kObjectIds.CNZ_BARBER_POLE, sidekick.getLatchedSolidObjectId());
    }

    @Test
    void playerQueryFailureIsNotSwallowed() {
        CnzBarberPoleObjectInstance pole = new CnzBarberPoleObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, Sonic3kObjectIds.CNZ_BARBER_POLE, 0, 0, false, 0));
        pole.setServices(new ThrowingPlayerQueryServices());

        assertThrows(IllegalStateException.class, () -> pole.update(0, new TestPlayableSprite()));
    }

    private static final class QueryOnlyPlayerServices extends TestObjectServices {
        private final PlayableEntity main;
        private final List<? extends PlayableEntity> queriedSidekicks;

        private QueryOnlyPlayerServices(PlayableEntity main, List<? extends PlayableEntity> queriedSidekicks) {
            this.main = main;
            this.queriedSidekicks = List.copyOf(queriedSidekicks);
        }

        @Override
        public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> main, () -> queriedSidekicks);
        }

        @Override
        public List<PlayableEntity> sidekicks() {
            return List.of();
        }
    }

    private static final class ThrowingPlayerQueryServices extends TestObjectServices {
        @Override
        public ObjectPlayerQuery playerQuery() {
            throw new IllegalStateException("query unavailable");
        }
    }
}
