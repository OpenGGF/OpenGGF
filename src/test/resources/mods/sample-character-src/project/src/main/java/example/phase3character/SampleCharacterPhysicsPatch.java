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
            private final PhysicsProvider inherited = base.getPhysicsProvider();
            private final PhysicsProfile runner = new PhysicsProfile(
                    (short)0x10,(short)0x80,(short)0x10,(short)0x500,(short)0x640,
                    (short)0x20,(short)0x14,(short)0x50,(short)0x20,(short)0x80,
                    (short)0x80,(short)0xE00,(short)28,(short)38,(short)9,(short)19,
                    (short)7,(short)14,false,(short)2);
            @Override public PhysicsProvider getPhysicsProvider() {
                return new PhysicsProvider() {
                    @Override public PhysicsProfile getProfile(String character) {
                        return key.equals(character) ? runner : inherited.getProfile(character);
                    }
                    @Override public PhysicsProfile getInitProfile(String character) {
                        return key.equals(character) ? null : inherited.getInitProfile(character);
                    }
                    @Override public PhysicsModifiers getModifiers() { return inherited.getModifiers(); }
                    @Override public com.openggf.game.rules.GameRules getRules() { return inherited.getRules(); }
                };
            }
        };
    }
}
