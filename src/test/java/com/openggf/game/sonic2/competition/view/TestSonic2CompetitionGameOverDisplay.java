package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic2.objects.Sonic2GameOverCardObjectInstance;
import com.openggf.level.objects.*;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.HexFormat;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionGameOverDisplay {
    @Test void obj39InitProjectsItsRomWordsIntoBothViewsRegardlessOfParent() throws Exception {
        var rom = RomByteReader.fromRom(TestEnvironment.currentRom());
        String init = HexFormat.of().formatHex(rom.slice(0x13F88, 0x44));
        assertTrue(init.contains("217c00014c6c0004"), "Obj39_Init mapping operand");
        assertTrue(init.contains("317c84de0002"), "Obj39_Init art operand before Adjust2PArtPointer");
        assertTrue(init.contains("317c00f0000a"), "Obj39_Init screen Y operand");
        for (int parent : new int[]{0xB000, 0xB040}) for (int frame = 0; frame < 4; frame++) {
            var services = new StubObjectServices().withIsolatedObjectManager();
            final int mapping = frame;
            var card = ObjectConstructionContext.construct(services,
                    () -> Sonic2GameOverCardObjectInstance.nativeWrite(null, mapping, parent));
            ObjectLifetimeOps.addDynamicAtReservedSlot(services.objectManager(), card, 2);
            assertEquals(0x39, card.getLiveObjectId(), "native code identity before art is ready");
            card.update(0, null);
            var slot = Sonic2CompetitionObjectDisplay.project(card, mock(ObjectArtProvider.class),
                    () -> fail("native card must not need an ordinary draw capture"));
            assertNotNull(slot);
            assertEquals(0x39, slot.id()); assertEquals(0, slot.renderFlags() & 0x7F);
            assertEquals((frame & 1) == 0 ? 0x60 : 0x1E0, slot.x());
            assertEquals(0xF0, slot.y()); assertEquals(0x826F, slot.artTile());
            assertEquals(new Mapping(0x14C6C, frame), slot.mapping());
            assertEquals(0, card.getPriorityBucket());
            var input = new Build(new Camera(9000, 600), new Camera(3000, 1000),
                    new Hud(false, 0, new PlayerHud(0,0,0,0), new PlayerHud(0,0,0,0), 0),
                    new RingWindow(List.of()), new RingWindow(List.of()), 0,
                    List.of(new Admission(2,0,0,slot)), false);
            var builder = new Sonic2CompetitionSatBuilder(rom);
            var p1 = builder.buildPlayer1(input);
            var p2 = builder.buildPlayer2(p1, input);
            int address = 0x14C6C + rom.readS16BE(0x14C6C + frame * 2);
            int count = rom.readU16BE(address);
            assertEquals(2, count);
            for (int player = 0; player < 2; player++) {
                var page = player == 0 ? p1 : p2;
                int start = page.entries().size() - 1 - count;
                for (int i = 0; i < count; i++) {
                    int piece = address + 2 + i * 8;
                    var sat = page.entries().get(start + i);
                    int x = (slot.x() + rom.readS16BE(piece + 6)) & 511;
                    assertEquals(x == 0 ? 1 : x, sat.rawX());
                    assertEquals(slot.y() + 128 + player * 224 + (byte)rom.readU8(piece), sat.rawY());
                    assertEquals((slot.artTile() + rom.readU16BE(piece + 4)) & 65535, sat.word());
                }
            }
        }
    }

    @Test void corruptedNativeArtWordFailsTheComparison() {
        var real=new Sonic2CompetitionCardReference.State(2,57,2,2,96,240,0x14C6C,0x826F,128,0,0xB040,0,true);
        var corrupt=new Sonic2CompetitionCardReference.State(2,57,2,2,96,240,0x14C6C,0x8270,128,0,0xB040,0,true);
        var rejected=assertThrows(AssertionError.class,()->Sonic2CompetitionCardReference.requireEqual(List.of(corrupt),List.of(real),7589));
        assertTrue(rejected.getMessage().contains("art="));
    }

    @Test void ordinaryS2DrawRetainsItsCameraRelativeCoordinates() throws Exception {
        var rom=TestEnvironment.currentRom();
        var sheet=new com.openggf.game.sonic2.Sonic2ObjectArt(rom,RomByteReader.fromRom(rom)).loadGameOverSheet();
        var renderer=new com.openggf.level.render.PatternSpriteRenderer(sheet,new com.openggf.graphics.GraphicsManager());
        var art=mock(ObjectArtProvider.class);
        org.mockito.Mockito.when(art.getRenderer(ObjectArtKeys.GAME_OVER)).thenReturn(renderer);
        var render=new ObjectRenderManager(art);
        var camera=new com.openggf.camera.Camera(com.openggf.configuration.SonicConfigurationService.getInstance());
        camera.setX((short)1000);camera.setY((short)200);
        var services=new StubObjectServices() {
            @Override public com.openggf.camera.Camera camera() { return camera; }
            @Override public ObjectRenderManager renderManager() { return render; }
        }.withIsolatedObjectManager();
        var ordinary=ObjectConstructionContext.construct(services,()->new Sonic2GameOverCardObjectInstance(2));
        ObjectLifetimeOps.addDynamicAtReservedSlot(services.objectManager(),ordinary,2);
        ordinary.update(0,null);
        var captured=com.openggf.level.render.SpriteFrameCaptureInternalAccess.capture(
                ()->ordinary.appendRenderCommands(new java.util.ArrayList<>()));
        assertEquals(1,captured.calls().size());
        var call=(com.openggf.level.render.SpriteFrameCaptureInternalAccess.FrameCall)captured.calls().getFirst();
        assertEquals(2,call.frameIndex());assertEquals(968,call.originX());assertEquals(312,call.originY());
        assertNull(ordinary.nativeDisplaySlot(),"ordinary1P draw never acquires native2P display authority");
    }

    @Test void ordinaryS2CardKeepsItsExistingRoutineAndDrawGate() {
        for (int frame = 0; frame < 4; frame++) {
            var services = new StubObjectServices().withIsolatedObjectManager();
            final int mapping = frame;
            var ordinary = ObjectConstructionContext.construct(services, () -> new Sonic2GameOverCardObjectInstance(mapping));
            var nativeCard = ObjectConstructionContext.construct(services, () -> new Sonic2GameOverCardObjectInstance(mapping,1));
            ObjectLifetimeOps.addDynamicAtReservedSlot(services.objectManager(), ordinary, 2);
            ObjectLifetimeOps.addDynamicAtReservedSlot(services.objectManager(), nativeCard, 3);
            for (int pass = 0; pass < 18; pass++) {
                ordinary.update(pass,null); nativeCard.update(pass,null);
                assertEquals(ordinary.getRoutine(), nativeCard.getRoutine());
                assertEquals(ordinary.getVdpX(), nativeCard.getVdpX());
                assertEquals(ordinary.getWaitTimer(), nativeCard.getWaitTimer());
                assertEquals(ordinary.isDisplayedThisFrame(), nativeCard.isDisplayedThisFrame());
                assertEquals(-1, ordinary.getLiveObjectId(), "1P placeholder/ordinary rendering contract unchanged");
            }
        }
    }
}
