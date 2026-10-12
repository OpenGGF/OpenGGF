package example.phase3standalone;

import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.level.Level;
import com.openggf.level.objects.*;

/** Declarative no-ROM setup; the registry retains the sample's own gameplay factories. */
public final class SampleStandaloneModule extends AbstractStandaloneGameModule {
    public SampleStandaloneModule(String owner, Level level) {
        super(StandaloneGameSpec.builder(owner)
                .zone("SAMPLE PLAINS", new StandaloneGameSpec.Act(0x400, level, 32, 96,
                        MusicReference.namespaced(owner, "zone-theme")))
                .supportsSidekick(true)
                .physics(PhysicsProvider.fixed(SampleCharacter.physicsSpec().profile(),
                        PhysicsModifiers.STANDARD, GameRules.SONIC_2))
                .touchResponses((byte) 0, (byte) 0, (byte) 8, (byte) 8)
                .object(owner + ":walker", (spawn, registry) -> new SampleBadnik(spawn)).build());
    }

}
