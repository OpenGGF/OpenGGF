package com.openggf.tests;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

/** CNZ1: independent production-preset, supported main/donor and finite roster entry floor. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz1EntryMatrix extends CanonicalEntryMatrixSupport {
    @org.junit.jupiter.params.ParameterizedTest(name = "only native P2 owns the intro story writer {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "tails,knuckles", "tails,tails", "tails,tails,knuckles,sonic,knuckles,sonic,tails"})
    void additionalConfiguredFollowersCannotSeizeTheNativeCarryWriter(String followers) {
        var row = new Row("sonic", com.openggf.configuration.WidescreenAspect.NATIVE_4_3, "off", followers);
        var fixture = boot(row);
        fixture.stepIdleFrames(2);
        var admitted = com.openggf.game.GameServices.sprites().getRegisteredSidekicks();
        org.junit.jupiter.api.Assertions.assertEquals(followers.split(",").length, admitted.size());
        var nativeP2 = com.openggf.game.GameServices.level().getObjectManager().getObjectServices()
                .playerQuery().nativeP2OrNull();
        org.junit.jupiter.api.Assertions.assertSame(admitted.getFirst(), nativeP2);
        org.junit.jupiter.api.Assertions.assertTrue(admitted.getFirst().getTailsCarryController()
                .isCarryingMainCharacter(), "ROM Player_2 is the positive story writer");
        for (int i = 1; i < admitted.size(); i++) {
            org.junit.jupiter.api.Assertions.assertFalse(admitted.get(i).getTailsCarryController()
                    .isCarryingMainCharacter(), "extra CPU cannot seize native P1: configured index=" + i);
            org.junit.jupiter.api.Assertions.assertSame(admitted.get(i - 1),
                    admitted.get(i).getCpuController().getLeader(), "ordinary follower chain remains uncapped");
        }
    }

    @Override TestS3kCanonicalEntryLifecycle.Act binding() {
        return new TestS3kCanonicalEntryLifecycle.Act("CNZ1", 3, 0);
    }
}
