package com.openggf.level;

import com.openggf.game.mutators.LevelMutatorPolicyAccess;
import com.openggf.game.mutators.StageEntryKind;
import com.openggf.game.session.WorldSession;

/** Final transition defense; accepted native source permits bypass a later LIVE denial. */
final class LevelStageEntryAdmission {
    private final WorldSession world;
    LevelStageEntryAdmission(WorldSession world) { this.world = world; }
    boolean special() { return LevelMutatorPolicyAccess.directEntryAllowed(world, StageEntryKind.SPECIAL); }
    boolean bonus() { return LevelMutatorPolicyAccess.directEntryAllowed(world, StageEntryKind.BONUS); }
}
