package com.openggf.game;

import com.openggf.level.Level;
import com.openggf.game.save.SaveSnapshotProvider;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestStandaloneGameSpec {
    private Level level() {
        return (Level)Proxy.newProxyInstance(Level.class.getClassLoader(),new Class<?>[]{Level.class},
                (proxy,method,args)->null);
    }
    @Test void typedMultiActAssemblyRetainsMusicDescriptorsAndNoRomBoundary() throws Exception {
        Level first=level(),second=level();
        var module=StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,first,32,96,MusicReference.namespaced("sample","theme")),
                        new StandaloneGameSpec.Act(0x401,second,64,160,MusicReference.stock(-1)))
                .supportsSidekick(true).build().module();
        GameDataSource source=new GameDataSource() {
            public java.util.Optional<com.openggf.data.Rom> rom() { return java.util.Optional.empty(); }
            public java.io.InputStream openAsset(String name) { throw new UnsupportedOperationException(); }
            public String identity() { return "sample"; }
        };
        var game=module.createGame(source);
        assertSame(first,game.loadLevel(0x400)); assertSame(second,game.loadLevel(0x401));
        assertThrows(IOException.class,()->game.loadLevel(0x402));
        assertEquals(2,module.getZoneRegistry().getActCount(0));
        assertArrayEquals(new int[]{64,160},module.getZoneRegistry().getStartPosition(0,1));
        assertEquals(MusicReference.namespaced("sample","theme"),module.getLevelMusicReference(0,0));
        assertTrue(module.supportsSidekick());
        assertEquals(GameId.STANDALONE,module.getGameId());
        assertNull(game.getRom());
        assertThrows(UnsupportedOperationException.class,()->module.createGame((com.openggf.data.Rom)null));
        assertNull(module.getAudioProfile().createSmpsLoader(null));
        assertTrue(module.getAudioProfile().getSoundMap().isEmpty());
    }
    @Test void expertOverridesAndCustomSavePayloadRemainAvailable() {
        SaveSnapshotProvider saves=(reason,context)->Map.of("custom",42);
        var spec=StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1)))
                .saveSnapshotProvider(saves).build();
        var module=new AbstractStandaloneGameModule(spec) {
            @Override public boolean supportsSidekick() { return true; }
        };
        assertTrue(module.supportsSidekick());
        assertSame(saves,module.getSaveSnapshotProvider());
        assertEquals(Map.of("custom",42),module.getSaveSnapshotProvider().capture(null,null));
        assertThrows(IllegalArgumentException.class,()->StandaloneGameSpec.builder("sample").build());
        assertThrows(IllegalArgumentException.class,()->StandaloneGameSpec.builder("sample")
                .zone("FIRST",new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1)),
                        new StandaloneGameSpec.Act(0x400,level(),0,0,MusicReference.stock(-1))).build());
    }
}
