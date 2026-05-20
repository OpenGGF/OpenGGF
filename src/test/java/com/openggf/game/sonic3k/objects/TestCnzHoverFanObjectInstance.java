package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCnzHoverFanObjectInstance {

    @Test
    void hoverFanLiftsUniqueEngineParticipantsOnce() {
        CnzHoverFanInstance fan = new CnzHoverFanInstance(
                new ObjectSpawn(0x100, 0x100, 0x46, 0x00, 0, false, 0));
        TestablePlayableSprite main = playerAt("sonic", 0x100, 0x100);
        TestablePlayableSprite tails = playerAt("tails", 0x100, 0x100);
        TestablePlayableSprite knuckles = playerAt("knuckles", 0x100, 0x100);

        fan.setServices(new TestObjectServices()
                .withSidekicks(List.of(tails, tails, knuckles)));

        fan.update(0, main);

        assertEquals(main.getCentreY(), tails.getCentreY(),
                "Duplicate sidekick entries must not apply the hover fan lift twice");
        assertEquals(main.getCentreY(), knuckles.getCentreY(),
                "Every unique engine sidekick should receive the same one-frame lift as the main player");
    }

    @Test
    void hoverFanKeepsUpdatePlayerFallbackWhenQueryOnlyFindsSidekicks() {
        CnzHoverFanInstance fan = new CnzHoverFanInstance(
                new ObjectSpawn(0x100, 0x100, 0x46, 0x00, 0, false, 0));
        TestablePlayableSprite main = playerAt("sonic", 0x100, 0x100);
        TestablePlayableSprite tails = playerAt("tails", 0x100, 0x100);

        fan.setServices(new TestObjectServices()
                .withSidekicks(List.of(tails)));

        fan.update(0, main);

        assertEquals(tails.getCentreY(), main.getCentreY(),
                "The update player must still participate when ObjectPlayerQuery cannot resolve main");
    }

    private static TestablePlayableSprite playerAt(String code, int centreX, int centreY) {
        TestablePlayableSprite player = new TestablePlayableSprite(code, (short) 0, (short) 0);
        player.setCentreX((short) centreX);
        player.setCentreY((short) centreY);
        return player;
    }
}
