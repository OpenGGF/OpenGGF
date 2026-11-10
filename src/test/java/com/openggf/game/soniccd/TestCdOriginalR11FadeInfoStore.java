package com.openggf.game.soniccd;

import com.openggf.game.save.SaveManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original Ready globals; the detached immutable value check does not invoke a restart. */
class TestCdOriginalR11FadeInfoStore extends TestCdOriginalR11LoadingWorld {
    @Test void originalReadyFadeInfoValueStoreKeepsTheOtherOwnedScalars()throws Exception{
        var source=TestCdOriginalBootPrefix.source();
        var attempt=SonicCdGameModule.prepareSelectedProfile(source,new SaveManager(root),1,SonicCdGameModule.ProfileEntry.NEW_GAME,0);
        var module=attempt.module().orElseThrow();
        try(var boot=boot()){
            var loop=boot.bootSonicCdSelectedProfile(source,attempt);
            for(int row=0;row<512&&module.originalR11LoadingImage().phase()!=SonicCdR31aLoadingVisit.Phase.READY;row++)step(loop,0);
            assertEquals(SonicCdR31aLoadingVisit.Phase.READY,module.originalR11LoadingImage().phase());
            var before=module.originalR11LoadingImage().globals();var tail=before.tail().orElseThrow();
            assertEquals(0x202F,tail.paletteFadeInfoWord());
            var after=before.fadeInfo(0x003F);assertEquals(0x003F,after.tail().orElseThrow().paletteFadeInfoWord());
            assertEquals(before,after.fadeInfo(0x202F));assertEquals(tail,before.tail().orElseThrow());
            assertThrows(IllegalArgumentException.class,()->before.fadeInfo(-1));
            assertThrows(IllegalArgumentException.class,()->before.fadeInfo(65536));
            assertEquals(before,module.originalR11LoadingImage().globals());assertFalse(module.originalR11EntryFrame().closed());
        }
    }
}
