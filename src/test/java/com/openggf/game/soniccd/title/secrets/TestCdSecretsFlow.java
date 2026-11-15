package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.TitleScreenProvider.TitleScreenAction;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.fmv.CdFmvNativeAccess;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The sound test through the coordinator ({@code SoundTest .Exits}, IPX.asm:1035-1123): the title's exit opens the
 * sound test's screen; a picture code resumes the native-program host into that picture; Special Stage 8 is
 * recorded as not hosted (no route from the native-program host to {@code SPECIAL_STAGE}); exit 0 returns.
 */
class TestCdSecretsFlow {
    @Test
    void soundTestExitsReachTheirPrograms() throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdFmvNativeAccess.usaCue())) {
            CdSceneCoordinator coordinator = new CdSceneCoordinator(() -> disc);
            coordinator.titleStarting();
            assertEquals(TitleScreenAction.OPTIONS, coordinator.titleExited(CdProgramFlow.EXIT_SOUND_TEST));
            assertInstanceOf(CdSecretsScreen.class, coordinator.programScreen());

            coordinator.soundTestExited(2);
            assertEquals(TitleScreenAction.OPTIONS, coordinator.consumeResume());
            assertEquals(CdNativeProgram.FUNISINF, coordinator.notHosted().getLast().program());
            assertInstanceOf(CdSecretsScreen.class, coordinator.programScreen());

            coordinator.soundTestExited(1);
            assertNull(coordinator.consumeResume(), "Special Stage 8 has no route from OPTIONS");
            assertEquals(CdNativeProgram.SPECSTAGE, coordinator.notHosted().getLast().program());
            assertEquals("SoundTest_SpecStg8", coordinator.notHosted().getLast().nativeCaller());

            int before = coordinator.notHosted().size();
            coordinator.soundTestExited(0);
            assertNull(coordinator.consumeResume());
            assertEquals(before, coordinator.notHosted().size());
        }
    }
}
