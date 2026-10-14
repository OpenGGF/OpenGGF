package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rules.GameRules;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Retail Obj_Bubbler's Obj_Sonic branch is independent of registration aliases. */
@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestBubblerCharacterIdentity {
    @ParameterizedTest
    @CsvSource({"sonic,sonic,false", "sonic,sonic_p2,false", "sonic,sonic_p8,false",
            "tails,tails,true", "tails,tails_p2,true",
            "knuckles,knuckles,true", "knuckles,knuckles_p2,true"})
    void queriedPlayerUsesItsCharacterOwnerForTheRollingBranch(String character, String registration,
                                                              boolean keepsRolling) {
        AbstractPlayableSprite player = switch (character) {
            case "sonic" -> new NativeSonic(registration);
            case "tails" -> new NativeTails(registration);
            case "knuckles" -> new NativeKnuckles(registration);
            default -> throw new AssertionError(character);
        };
        assertEquals(character, player.characterKey().persisted());
        assertEquals(registration, player.getCode());
        player.setInWater(true);
        player.setRolling(true);
        var bubble = new BubblerObjectInstance(new ObjectSpawn(0x100, 0x100, 0x54, 2, 0, false, 0));
        var services = new QueryServices(player);
        bubble.setServices(services);
        int collectedFromY = 0;
        for (int frame = 0; frame < 90 && services.pickups == 0; frame++) {
            // Declared local overlap; the real owner initializes/grows/collects its bubble.
            player.setCentreXPreserveSubpixel((short) bubble.getX());
            player.setCentreYPreserveSubpixel((short) (bubble.getY() + 1));
            collectedFromY = player.getCentreY();
            bubble.update(frame, null);
        }
        assertEquals(1, services.pickups, "actual large-bubble collection");
        assertEquals(keepsRolling, player.getRolling(),
                "loc_2FC5A uses native character identity, not the engine roster name");
        assertEquals(9, player.getXRadius(), "literal native x_radius");
        assertEquals(keepsRolling ? 15 : 19, player.getYRadius(), "literal native y_radius");
        assertEquals(collectedFromY - (keepsRolling ? 1 : 5), player.getCentreY(),
                "native word-only radius adjustment");
        assertEquals(registration, player.getCode(), "registration identity remains intact");
    }

    private static final class NativeSonic extends Sonic {
        NativeSonic(String registration) {
            super(registration, (short) 0x100, (short) 0x100);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }
    private static final class NativeTails extends Tails {
        NativeTails(String registration) {
            super(registration, (short) 0x100, (short) 0x100);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }
    private static final class NativeKnuckles extends Knuckles {
        NativeKnuckles(String registration) {
            super(registration, (short) 0x100, (short) 0x100);
            setGameRulesForTest(GameRules.SONIC_3K);
        }
    }
    private static final class QueryServices extends TestObjectServices {
        private final AbstractPlayableSprite player;
        private int pickups;
        QueryServices(AbstractPlayableSprite player) {
            this.player = player;
            withWaterSystem(new WaterSystem() {
                @Override public boolean hasWater(int zone, int act) { return true; }
                @Override public int getWaterLevelY(int zone, int act) { return -0x1000; }
            });
        }
        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> null, () -> List.of(player));
        }
        @Override public List<PlayableEntity> sidekicks() { return List.of(); }
        @Override public void playSfx(int soundId) { pickups++; }
    }
}
