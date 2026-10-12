package example.phase3character;

import com.openggf.game.*;
import com.openggf.game.patch.*;
import java.util.*;

public final class SampleCharacterPhysicsPatch implements GamePatch {
    private final String key;
    public SampleCharacterPhysicsPatch(String owner) { key = CharacterKey.mod(owner, "runner").persisted(); }
    @Override public String id() { return "runner-physics"; }
    @Override public String displayName() { return "Phase Runner physics"; }
    @Override public String baseGameId() { return "s2"; }
    @Override public boolean activatesFor(GameplayLaunchRequest request) { return key.equals(request.mainCharacter()); }
    @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
    @Override public List<String> providedMainCharacters() { return List.of(); }
    @Override public GameModule apply(GameModule base, PatchContext context) {
        return new DelegatingGameModule(base, id()) {
            private final PhysicsProvider physics = base.getPhysicsProvider()
                    .transform(key::equals, ignored -> SampleCharacter.physicsSpec().profile());
            @Override public PhysicsProvider getPhysicsProvider() {
                return physics;
            }
        };
    }
}
