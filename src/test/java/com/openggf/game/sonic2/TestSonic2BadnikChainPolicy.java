package com.openggf.game.sonic2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSonic2BadnikChainPolicy {
    @Test void hostOwnsOneRewindWordAndLevelRamClearResetsIt() {
        var host=new Sonic2GameModule();
        var policy=host.getGameService(com.openggf.game.internal.BadnikChainPolicy.class);
        assertEquals(1,host.rewindAdapters().stream().filter(adapter -> adapter == policy).count());
        var chain=(Sonic2BadnikChain)policy;
        chain.incrementAndAward(); chain.incrementAndAward();
        int saved=chain.capture();
        assertEquals(500,chain.incrementAndAward());
        chain.restore(saved);
        assertEquals(500,chain.incrementAndAward());
        var other=new Sonic2GameModule();
        assertEquals(0,other.getGameService(com.openggf.game.internal.BadnikChainPolicy.class).word());
        assertEquals(6,chain.word());
        host.onLevelLoad();
        assertEquals(0,chain.word());
    }
    @Test void nativeWordWrapAndEarlyTableAndRewind() {
        var chain = new Sonic2BadnikChain();
        assertEquals(100, chain.incrementAndAward());
        assertEquals(200, chain.incrementAndAward());
        assertEquals(500, chain.incrementAndAward());
        var saved = chain.capture();
        assertEquals(6, saved);
        for (int i=4; i<16; i++) assertEquals(1000, chain.incrementAndAward());
        assertEquals(10000, chain.incrementAndAward());
        chain.restore(0xFFFE);
        assertEquals(1000, chain.incrementAndAward());
        assertEquals(0, chain.capture());
        assertEquals(100, chain.incrementAndAward());
        chain.restore(saved);
        assertEquals(1000, chain.incrementAndAward());
        chain.reset();
        assertEquals(100, chain.incrementAndAward());
    }
}
