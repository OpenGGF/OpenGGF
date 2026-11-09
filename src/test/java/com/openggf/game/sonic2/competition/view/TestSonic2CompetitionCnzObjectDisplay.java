package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.objects.LauncherSpringObjectInstance;
import com.openggf.game.sonic2.objects.RingPrizeObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.Pattern;
import com.openggf.level.render.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions for the visible CNZ owners previously excluded from both native sprite pages. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionCnzObjectDisplay {
    @Test void reachedCageBumpersAndBombUseCertifiedRomFramesInsteadOfDeferredExclusion() throws Exception {
        for (int id : new int[]{0x44,0xD6,0xD8,0xD3}) {
            assertFalse(Sonic2CompetitionSpriteArtTable.PENDING_ADOPTION.containsKey(id));
            var row=Sonic2CompetitionSpriteArtTable.captureRows().stream().filter(r->r.id()==id).findFirst().orElseThrow();
            var pieces=Sonic2CompetitionSpriteMapper.readFrame(
                    RomByteReader.fromRom(TestEnvironment.currentRom()),row.display().mappingsAddress(),0);
            assertFalse(pieces.isEmpty(),row.role());
        }
        // Raw Init operands, including each palette, art address and bucket, are independently
        // checked for all four rows by TestSonic2CompetitionSpriteArtTable.
    }

    @Test void plungerKeepsOneNativeOwnerWithMovingHeadAndFixedBaseInBothOrientations() throws Exception {
        var rom=RomByteReader.fromRom(TestEnvironment.currentRom());
        for(int subtype : new int[]{0,1}) {
            var spring=new LauncherSpringObjectInstance(new ObjectSpawn(400,800,0x85,subtype,0,false,0),"plunger");
            assertTrue(Sonic2CompetitionObjectDisplay.adopted(spring));
            var slot=spring.nativeDisplaySlot();
            assertEquals(0x44,slot.renderFlags());assertEquals(4,spring.getPriorityBucket());
            assertEquals(subtype==0 ? 0x211 : 0x201,slot.artTile());
            assertEquals(subtype==0 ? 0x2B07E : 0x2B0EC,slot.mapping().address());
            assertEquals(1,slot.multiSprite().mainFrame());
            assertEquals(subtype==0 ? 24 : 32,slot.multiSprite().width());
            assertEquals(List.of(new Sonic2CompetitionDisplayInputs.Child(400,subtype==0?832:800,2)),slot.multiSprite().children());
            assertFalse(Sonic2CompetitionSpriteMapper.readFrame(rom,slot.mapping().address(),1).isEmpty());
            assertFalse(Sonic2CompetitionSpriteMapper.readFrame(rom,slot.mapping().address(),2).isEmpty());
            // Obj85_Init has separate ROM mapping/art writes for the two variants.
            String init=java.util.HexFormat.of().formatHex(rom.slice(0x2ABD4,0x40));
            assertTrue(init.contains("217c%08x0004".formatted(slot.mapping().address())));
            assertTrue(init.contains("317c%04x0002".formatted(subtype==0?0x422:0x402)));
        }
    }

    @Test void ringPrizeUsesItsRingSheetSpinAndSparkleFramesWithoutAnObjectArtProvider() {
        var sheet=new ObjectSpriteSheet(new Pattern[]{new Pattern()},java.util.Collections.nCopies(8,
                new SpriteMappingFrame(List.of(new SpriteMappingPiece(0,0,1,1,0,false,false,0)))),1,1);
        var renderer=new PatternSpriteRenderer(sheet,new GraphicsManager());
        var ring=new RingPrizeObjectInstance(100,200,100,200,26,new int[]{1});
        assertTrue(Sonic2CompetitionObjectDisplay.adopted(ring));
        for(int frame : new int[]{0,1,2,3,4,5,6,7}) {
            var slot=Sonic2CompetitionObjectDisplay.project(ring,null,()->renderer.drawFrameIndex(frame,100,200));
            assertEquals(0xDC,slot.id());assertEquals(0x12382,slot.mapping().address());
            assertEquals(frame,slot.mapping().frame());assertEquals(0x235E,slot.artTile());
            assertEquals(100,slot.x());assertEquals(200,slot.y());
        }
        assertNull(Sonic2CompetitionObjectDisplay.project(ring,null,()->{}));
    }
}
