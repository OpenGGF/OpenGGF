package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestIczSegmentColumnObjectInstance {

    @Test
    public void registryCreatesIczSegmentColumnForId0xB3InS3klZoneSet() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        ObjectSpawn spawn = new ObjectSpawn(0x1200, 0x0580, Sonic3kObjectIds.ICZ_SEGMENT_COLUMN, 0, 0, false, 0);

        ObjectInstance instance = registry.create(spawn);

        assertTrue(instance instanceof IczSegmentColumnObjectInstance);
    }

    @Test
    public void primaryNameFor0xB3MatchesS3klPointerTable() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        assertEquals("ICZSegmentColumn", registry.getPrimaryName(Sonic3kObjectIds.ICZ_SEGMENT_COLUMN));
    }

    @Test
    public void subtypeZeroBuildsThreeRomStackSegments() {
        IczSegmentColumnObjectInstance column = new IczSegmentColumnObjectInstance(
                new ObjectSpawn(0x1200, 0x0580, Sonic3kObjectIds.ICZ_SEGMENT_COLUMN, 0, 0, false, 0));

        List<IczSegmentColumnObjectInstance.SegmentSpec> specs = column.segmentSpecsForTesting();

        assertEquals(3, specs.size());
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(0, 0x1200, 0x0580, 0x0A), specs.get(0));
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(2, 0x1200, 0x0560, 0x0A), specs.get(1));
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(4, 0x1200, 0x0540, 0x0A), specs.get(2));
    }

    @Test
    public void nonzeroSubtypeBuildsFourSegmentsWithTopCapFrame() {
        IczSegmentColumnObjectInstance column = new IczSegmentColumnObjectInstance(
                new ObjectSpawn(0x1200, 0x0580, Sonic3kObjectIds.ICZ_SEGMENT_COLUMN, 2, 0, false, 0));

        List<IczSegmentColumnObjectInstance.SegmentSpec> specs = column.segmentSpecsForTesting();

        assertEquals(4, specs.size());
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(0, 0x1200, 0x0580, 0x0A), specs.get(0));
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(2, 0x1200, 0x0560, 0x0A), specs.get(1));
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(4, 0x1200, 0x0540, 0x0A), specs.get(2));
        assertEquals(new IczSegmentColumnObjectInstance.SegmentSpec(6, 0x1200, 0x0520, 0x03), specs.get(3));
    }

    @Test
    public void segmentUsesSolidObjectFullExtentsFromDisassembly() {
        IczSegmentColumnObjectInstance.Segment segment =
                IczSegmentColumnObjectInstance.Segment.forTesting(0x1200, 0x0580, 0, null);

        SolidObjectParams params = segment.getSolidParams();

        assertEquals(0x2B, params.halfWidth());
        assertEquals(0x10, params.airHalfHeight());
        assertEquals(0x10, params.groundHalfHeight());
        assertEquals(0x20, segment.getTopLandingHalfWidth(null, params.halfWidth()));
        assertEquals(5, segment.getPriorityBucket());
    }

    @Test
    public void iczPlanIncludesLevelArtEntryForWallAndColumnMappings() {
        Sonic3kPlcArtRegistry.ZoneArtPlan plan = Sonic3kPlcArtRegistry.getPlan(0x05, 0);

        Sonic3kPlcArtRegistry.LevelArtEntry entry = plan.levelArt().stream()
                .filter(e -> Sonic3kObjectArtKeys.ICZ_WALL_AND_COLUMN.equals(e.key()))
                .findFirst()
                .orElse(null);

        assertNotNull(entry);
        assertEquals(Sonic3kConstants.MAP_ICZ_WALL_AND_COLUMN_ADDR, entry.mappingAddr());
        assertEquals(1, entry.artTileBase());
        assertEquals(2, entry.palette());
    }
}
