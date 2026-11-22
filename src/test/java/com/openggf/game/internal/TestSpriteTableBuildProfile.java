package com.openggf.game.internal;

import com.openggf.game.GameModule;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.soniccd.SonicCdGameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class TestSpriteTableBuildProfile {
    @Test
    void nestedHostsInheritTheNativeSpriteTableProfile() {
        assertInherited(new Sonic1GameModule());
        assertInherited(new Sonic2GameModule());
        assertInherited(new Sonic3kGameModule());
        assertInherited(new SonicCdGameModule());
    }

    private static void assertInherited(GameModule nativeModule) {
        var wrapped = new DelegatingGameModule(nativeModule, "inner") {};
        var host = new DelegatingGameModule(wrapped, "outer") {};
        assertSame(nativeModule, SpriteTableBuildProfile.forModule(host), nativeModule.getIdentifier());
    }
}
