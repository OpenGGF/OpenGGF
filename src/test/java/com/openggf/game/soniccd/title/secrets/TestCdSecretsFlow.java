package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.TitleScreenProvider.TitleScreenAction;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.fmv.CdFmvNativeAccess;
import com.openggf.game.soniccd.program.CdProgramFlow;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.special.CdSpecialStageEntry;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The sound test through the coordinator ({@code SoundTest .Exits}, IPX.asm:1035-1123): the title's exit opens the
 * sound test's screen; a picture code resumes the native-program host into that picture.
 * Code 1 returns a stage launch to the sound test. Exit 0 returns to the title.
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

            var stage = assertInstanceOf(CdProgramRequest.SpecialStage.class, coordinator.soundTestExited(1));
            assertEquals(CdSpecialStageEntry.secret(), stage.entry());
            assertNull(coordinator.consumeResume(), "the sound test hands straight to the stage");
            assertEquals(CdNativeProgram.FUNISINF, coordinator.notHosted().getLast().program());

            int before = coordinator.notHosted().size();
            coordinator.soundTestExited(0);
            assertNull(coordinator.consumeResume());
            assertEquals(before, coordinator.notHosted().size());
        }
    }
}
