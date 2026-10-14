package com.openggf.game.sonic3k.ending;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingPaletteOwners {
    @AfterEach void reset() { SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState(); SessionManager.clear(); }
    private HeadlessTestFixture boot() {
        var c=SonicConfigurationService.getInstance(); c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS,320);
        GameServices.camera().refreshViewportDimensions(c);c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        return HeadlessTestFixture.builder().withZoneAndAct(13,1).withFreshLevelStartLifecycle().build();
    }
    private int word(int line,int color) {
        return com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                GameServices.level().getCurrentLevel().getPalette(line).getColor(color));
    }
    private void resolve() {
        var level=GameServices.level().getCurrentLevel();
        var palettes=new com.openggf.level.Palette[]{level.getPalette(0),level.getPalette(1),level.getPalette(2),level.getPalette(3)};
        TestEnvironment.objectServices().paletteOwnershipRegistryOrNull().resolveInto(palettes,null,null,palettes[0]);
    }
    private void tick(S3kEndingAizPaletteState state) {
        var services=TestEnvironment.objectServices(); services.paletteOwnershipRegistryOrNull().beginFrame(); state.tick(services); resolve();
    }
    @Test void holdUsesLiteralRomRowsSharedWordTimingAndBothWrapEdges() {
        boot();var state=new S3kEndingAizPaletteState();state.beginHold();tick(state);
        assertEquals(0xEE8,word(2,11));assertEquals(0xE64,word(2,14));assertEquals(0xEA4,word(3,12));assertEquals(0xEE2,word(3,14));
        for(int i=2;i<=8;i++)tick(state);assertEquals(0xEE8,word(2,11));
        tick(state);assertEquals(0xEE2,word(2,11));assertEquals(0xEE8,word(3,13));assertEquals(0xEA4,word(3,14));
        for(int i=10;i<=25;i++)tick(state);assertEquals(0xE64,word(2,11));
        for(int i=26;i<=33;i++)tick(state);assertEquals(0xEE8,word(2,11));
        for(int i=34;i<=57;i++)tick(state);assertEquals(0xE60,word(3,13));assertEquals(0xEE8,word(3,14));
        for(int i=58;i<=65;i++)tick(state);assertEquals(0xEA4,word(3,12));assertEquals(0xEE2,word(3,14));
        var bytes=ByteBuffer.allocate(S3kEndingAizPaletteState.BYTES);state.captureTo(bytes);bytes.flip();
        assertEquals(7,bytes.getShort());assertEquals(72,bytes.getShort());assertEquals(6,bytes.getShort());assertEquals(0,bytes.get());
    }
    @Test void fadeResetPreservesSecondaryCounterAndCycleIgnoresObjectRotationDisable() {
        boot();var state=new S3kEndingAizPaletteState();
        state.restoreFrom(ByteBuffer.allocate(S3kEndingAizPaletteState.BYTES).putShort((short)0).putShort((short)0xFFF8)
                .putShort((short)42).put((byte)0).flip());tick(state);
        assertEquals(0xE64,word(2,11));assertEquals(0xEE8,word(3,14));
        var bytes=ByteBuffer.allocate(S3kEndingAizPaletteState.BYTES);state.captureTo(bytes);bytes.flip();
        assertEquals(7,bytes.getShort());assertEquals(0,bytes.getShort());assertEquals(0,bytes.getShort());
        state.restoreFrom(ByteBuffer.allocate(S3kEndingAizPaletteState.BYTES).putShort((short)123).putShort((short)123)
                .putShort((short)12).put((byte)0xFF).flip());state.beginHold();
        TestEnvironment.objectServices().paletteOwnershipRegistryOrNull().setPaletteRotationDisabled(true);tick(state);
        assertEquals(0xE60,word(3,12));assertEquals(0xEEE,word(3,14));
        state.beginFade();for(int i=0;i<8;i++)tick(state);
        assertEquals(0xEEE,word(3,14),"native secondary gate suppresses only the secondary channel");
        assertEquals(0xEE2,word(2,11),"the explicit AnPal owner is independent of object rotation disable");
    }
    @Test void masterHelperUsesSignedModeAndCapturesItsObjectLocalScriptClock() {
        var f=boot();var state=(EndingZoneRuntimeState)GameServices.zoneRuntimeState();state.ending().initializeProgress(7,7);
        state.screen().masterPaletteMode=1;
        var manager=GameServices.level().getObjectManager();var helper=manager.createDynamicObject(EndingMasterPaletteObjectInstance::new);
        var services=TestEnvironment.objectServices();services.paletteOwnershipRegistryOrNull().beginFrame();helper.update(10,f.sprite());resolve();
        assertEquals(0x680,word(2,14));assertEquals(0x8A0,word(2,15));
        var saved=helper.captureRewindState(com.openggf.game.rewind.schema.RewindCaptureContext.none());
        services.paletteOwnershipRegistryOrNull().setPaletteRotationDisabled(true);helper.update(11,f.sprite());
        assertEquals(saved.objectSubclassExtra(),helper.captureRewindState(com.openggf.game.rewind.schema.RewindCaptureContext.none()).objectSubclassExtra());
        services.paletteOwnershipRegistryOrNull().setPaletteRotationDisabled(false);state.screen().masterPaletteMode=-1;
        for(int i=0;i<10;i++){services.paletteOwnershipRegistryOrNull().beginFrame();helper.update(12+i,f.sprite());resolve();}
        assertEquals(0x8C0,word(1,15));assertEquals(0x8A0,word(2,15),"FAC1 mode switches destination without rewriting the prior line");
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry();var world=registry.capture();
        for(int i=0;i<14;i++){services.paletteOwnershipRegistryOrNull().beginFrame();helper.update(30+i,f.sprite());resolve();}
        var expected=registry.capture();registry.restore(world);same(world,registry.capture());
        helper=manager.activeObjectsOfType(EndingMasterPaletteObjectInstance.class).getFirst();
        for(int i=0;i<14;i++){services.paletteOwnershipRegistryOrNull().beginFrame();helper.update(30+i,f.sprite());resolve();}
        same(expected,registry.capture());state.screen().masterPaletteMode=0;
        var stopped=helper.captureRewindState(com.openggf.game.rewind.schema.RewindCaptureContext.none());helper.update(99,f.sprite());
        assertEquals(stopped.objectSubclassExtra(),helper.captureRewindState(com.openggf.game.rewind.schema.RewindCaptureContext.none()).objectSubclassExtra());
        assertFalse(helper.isDestroyed(),"native zero FAC1 parks this owner until clearRAM");
    }
    private void same(CompositeSnapshot a,CompositeSnapshot b) {
        assertEquals(a.entries().keySet(),b.entries().keySet());
        for(String key:a.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,a.get(key),b.get(key)).isEmpty(),key);
    }
}
