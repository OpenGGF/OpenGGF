package com.openggf.level.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.game.sonic2.objects.badniks.MasherBadnikInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-backed geometry/profile regression; declared off-camera geometry, no recorded state import. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestTouchScanRenderBitRule {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void hostRulesRetainTheOtherGamesAndKis2Inheritance() {
        assertTrue(GameRules.SONIC_1.objectInteraction().touchScanRequiresObjectRenderBit());
        assertFalse(GameRules.SONIC_2.objectInteraction().touchScanRequiresObjectRenderBit());
        assertFalse(Kis2Rules.RULES.objectInteraction().touchScanRequiresObjectRenderBit());
        assertTrue(GameRules.SONIC_3K.objectInteraction().touchScanRequiresObjectRenderBit());
        assertTrue(GameRules.SONIC_3K.objectInteraction().touchResponseUsesPreviousCollisionResponseList());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void realEnemyOutsidePrimaryCameraStillTouchesOnlyWithTheUngatedHostRule(boolean gated) throws Exception {
        var rom=TestEnvironment.currentRom();var bytes=RomByteReader.fromRom(rom);
        // Touch_Loop (s2.asm:85209-85219): MOVE.B collision_flags(a1),d0, then BNE.
        assertEquals(0x1029,bytes.readU16BE(0x3F5A0));
        assertEquals(0x0020,bytes.readU16BE(0x3F5A2));
        var table=new Sonic2GameModule().createTouchResponseTable(bytes);
        assertEquals(12,table.getWidthRadius(9));assertEquals(16,table.getHeightRadius(9));
        var host=mock(GameModule.class);
        when(host.getRules()).thenReturn(gated?GameRules.SONIC_1:GameRules.SONIC_2);
        var services=new TestObjectServices().withGameModule(host);
        var enemy=spy(new MasherBadnikInstance(new ObjectSpawn(1000,100,0x5C,0,0,false,100)));
        enemy.setServices(services);enemy.snapshotPreUpdatePosition();
        AbstractObjectInstance.updateCameraBounds(0,0,320,224,0);
        assertFalse(enemy.isOnScreenForTouch(),"real camera/first-execution predicate rejects this position");
        assertEquals(9,enemy.getCollisionFlags());
        assertTrue(enemy.getTouchResponseProfile().requiresRenderFlagForTouch(),
                "an object profile requesting the gate cannot re-enable it under the ungated host");
        // Stub only the response side effect, keeping the actual enemy profile,
        // ROM extents, camera predicate, scan and overlap algorithm.
        doNothing().when(enemy).onPlayerAttack(any(),any());
        var manager=mock(ObjectManager.class);
        when(manager.services()).thenReturn(services);
        when(manager.objectCallbacks()).thenReturn(new ObjectCallbackRouter(null));
        when(manager.getTouchResponseObjects()).thenReturn(List.of(enemy));
        var actor=mock(AbstractPlayableSprite.class);
        when(actor.getCentreX()).thenReturn((short)1000);when(actor.getCentreY()).thenReturn((short)100);
        when(actor.getYRadius()).thenReturn((short)19);
        when(actor.getAnimationId()).thenReturn(ObjectManager.ANIM_ROLL);when(actor.getRolling()).thenReturn(true);
        // Deliberately opposite donor/player rules prove that the host owns this scan.
        when(actor.getGameRules()).thenReturn(gated?GameRules.SONIC_2:GameRules.SONIC_1);
        new ObjectTouchResponseController(manager,table).update(actor,1,false);
        verify(enemy,times(gated?0:1)).onPlayerAttack(eq(actor),any());
    }
}
