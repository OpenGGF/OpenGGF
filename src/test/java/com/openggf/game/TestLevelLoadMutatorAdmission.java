package com.openggf.game;

import com.openggf.game.mutators.LevelMutatorPolicy;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.LevelMutatorTestWorld;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelLoadMutatorAdmission {
    @Test void newlyAdmittedCheckpointDenialDropsAlreadyBankedDeathAuthority() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        var bank = new CheckpointState();
        bank.restoreFromSaved(0x600, 0x200, 0x550, 0x180, 4);
        bank.saveRingState(120, 2);
        var context = new LevelLoadContext();
        context.snapshotCheckpoint(bank);
        services.request(new LevelMutatorPolicy(Set.of(), false, true, false, false));
        assertTrue(context.hasCheckpoint());
        WorldSessionPolicyAccess.beforeAssembly(services.worldSession(), LevelLoadCause.FULL_DEATH_RELOAD);
        assertTrue(LevelLoadMutatorAdmission.normalize(services.worldSession(), LevelLoadCause.FULL_DEATH_RELOAD, context));
        assertFalse(context.hasCheckpoint());
        assertEquals(-1, context.getCheckpointIndex());
        assertEquals(0, context.getCheckpointRings());
        assertTrue(bank.isActive(), "source bank remains available to stage-return owners");
    }

    @Test void noRingsStripsCountersButKeepsStageReturnPositionAndOrdinaryCheckpointActivation() {
        var services = new LevelMutatorTestWorld(new Sonic2GameModule());
        services.policy(new LevelMutatorPolicy(Set.of(), true, true, false, false));
        var bank = new CheckpointState();
        bank.restoreFromSaved(0x600, 0x200, 0x550, 0x180, 4);
        bank.saveRingState(120, 2);
        var context = new LevelLoadContext();
        context.snapshotCheckpoint(bank);
        assertFalse(LevelLoadMutatorAdmission.normalize(services.worldSession(), LevelLoadCause.CHECKPOINT_RESTORE, context));
        assertTrue(context.hasCheckpoint());
        assertEquals(0x600, context.getCheckpointX());
        assertEquals(0, context.getCheckpointRings());
        assertEquals(0, context.getCheckpointRingExtraLifeFlags());
    }
}
