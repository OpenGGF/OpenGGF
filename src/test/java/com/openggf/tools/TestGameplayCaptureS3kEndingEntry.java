package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Declared capture inventory must precede the actual native ending initialization. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureS3kEndingEntry {
    @ParameterizedTest
    @CsvSource({"sonic,1111111,0","sonic,3333333,2","tails,1111111,0","tails,3333333,2"})
    void nativeNoTitleEntrySelectsTheActualConfiguredProgressBeforeItsFirstObjectPass(
            String character,String emeralds,int expectedProgress) throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,character,"","off",null,null,null,
                emeralds,false,false);
        try(var session=new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),13,1,settings);
            var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();
            assertFalse(state.ending().initialized(),"capture setup has not dispatched native ending init");
            assertTrue(GameServices.level().hasPendingInitialProcessSpritesPass());
            session.step(null);session.renderFrame();
            assertTrue(state.ending().initialized());assertEquals(expectedProgress,state.ending().progress());
            var rescue=GameServices.level().getObjectManager()
                    .activeObjectsOfType(EndingRescueDirectorObjectInstance.class).getFirst();
            assertEquals(2,rescue.routine());assertEquals(0,state.screen().scrollYFixed);
            assertSame(GameServices.sprites().getMainPlayable(),session.player());
        }
    }
}
