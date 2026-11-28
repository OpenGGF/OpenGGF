package mutators;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/** Registers the example catalogue. The engine supplies its title and pause screens. */
public final class MutatorsMod implements GgfMod {
    @Override public void register(ModContext context) {
        context.registerMutator(Gravity.definition());
        context.registerMutator(Ringfall.definition());
        context.registerMutator(BigHead.definition());
        context.registerMutator(Stealth.definition());
        context.registerMutator(NoPowerups.definition());
        context.registerMutator(LevelFilters.checkpoints());
        context.registerMutator(LevelFilters.rings());
        context.registerMutator(GameSpeed.definition());
        context.registerMutator(LevelFilters.specialStages());
        context.registerMutator(LevelFilters.bonusStages());
        context.registerMutator(ViolentExplosions.definition());
        context.registerMutator(HomingAttack.definition());
        context.registerMutator(BetaFlight.definition());
        context.registerMutator(StartingState.rings());
        context.registerMutator(StartingState.time());
        context.registerMutator(StartingState.score());
        context.registerMutator(StartingState.lives());
        context.registerMutator(HungryForRings.definition());
    }
}
