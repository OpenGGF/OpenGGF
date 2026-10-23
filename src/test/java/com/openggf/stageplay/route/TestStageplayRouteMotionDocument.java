package com.openggf.stageplay.route;

import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayRouteMotionDocument {
    @Test void motionIsExplicitAndStrictlyRoundTrips() throws Exception {
        var motion = new RouteMotion(StartAction.SPINDASH, TravelSpeed.RUN,
                EndingAction.OVERSHOOT, EndingFacing.LEFT);
        var command = new PuppetCommand("move",0,PuppetCommandKind.MOVE_TO,400,237,1,0,
                ObjectTarget.none(),WaitCondition.NONE,0,motion);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(motion,json.readValue(json.writeValueAsString(command),PuppetCommand.class).motion());
        assertEquals(RouteMotion.DEFAULT,new StageplayRouteRequest("hero",0,400,237,300).motion());
        var empty=StageplayDocument.empty("s2",0,0,0,0,320,224);
        assertEquals(empty,StageplayStore.decode(StageplayStore.encode(empty)));
        assertThrows(IllegalArgumentException.class,()->new RouteMotion(null,TravelSpeed.WALK,
                EndingAction.GENTLE_STOP,EndingFacing.INHERIT));
    }
    @Test void abilitiesBelongToEachActorAndRespectItsNativeDonor() {
        for(var module:java.util.List.of(new Sonic1GameModule(),new Sonic2GameModule(),new Sonic3kGameModule())) {
            var registry=module.getPlayableCharacterRegistry();
            for(var key:java.util.List.of(CharacterKey.SONIC,CharacterKey.TAILS,CharacterKey.KNUCKLES)) {
                assertEquals(module.getRules().playerCapability().spindashEnabled(),
                        registry.movementAbilities(key,module).contains(CharacterDefinition.MovementAbility.SPINDASH));
            }
            var original=registry.find(CharacterKey.SONIC).orElseThrow();
            var without=new CharacterDefinition(original.key(),original.displayName(),original.spriteFactory(),
                    original.respawnStrategyFactory(),original.behavesLike(),original.secondaryAbility(),
                    original.supportsSuperForm(),original.artSupplier());
            assertTrue(com.openggf.game.PlayableCharacterRegistry.empty().register(without.key(),without)
                    .movementAbilities(without.key(),module).isEmpty(),"Actor can decline a donor-supported move");
        }
    }
}
