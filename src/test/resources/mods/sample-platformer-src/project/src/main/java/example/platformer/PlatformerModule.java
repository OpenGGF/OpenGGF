package example.platformer;

import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.level.Level;
import com.openggf.level.objects.*;

/** Declarative no-ROM setup; the registry retains the sample's own gameplay factories. */
public final class PlatformerModule extends AbstractStandaloneGameModule {
    public PlatformerModule(String owner, Level level) {
        super(StandaloneGameSpec.builder(owner)
                .zone("BOLT PLAINS", new StandaloneGameSpec.Act(0x400, level, 64, 160,
                        MusicReference.namespaced(owner, "zone-theme")))
                .supportsSidekick(false)
                .physics(PhysicsProvider.fixed(BoltCharacter.physicsSpec().profile(),
                        PhysicsModifiers.STANDARD, GameRules.SONIC_2))
                .touchResponses((byte) 0, (byte) 0, (byte) 8, (byte) 8)
                .object(owner + ":zapbug", (spawn, registry) -> new ZapBug(spawn))
                .object(owner + ":springpad", (spawn, registry) -> new SpringPad(spawn)).build());
    }

}
