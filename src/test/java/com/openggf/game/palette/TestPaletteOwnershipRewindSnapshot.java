package com.openggf.game.palette;

import com.openggf.game.rewind.snapshot.PaletteOwnershipSnapshot;
import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPaletteOwnershipRewindSnapshot {

    @Test
    void roundTripPreservesOwnershipArray() {
        PaletteOwnershipRegistry reg = new PaletteOwnershipRegistry();
        // Capture after reset — should be all "none"
        PaletteOwnershipSnapshot snap = reg.capture();
        // Set some custom owners by restoring a modified snapshot
        String[] owners = snap.owners().clone();
        owners[0] = "htz-sky";
        owners[3] = "cnz-bumper";
        PaletteOwnershipSnapshot modified = new PaletteOwnershipSnapshot(owners);
        reg.restore(modified);
        PaletteOwnershipSnapshot roundTrip = reg.capture();
        assertEquals("htz-sky", roundTrip.owners()[0]);
        assertEquals("cnz-bumper", roundTrip.owners()[3]);
    }

    @Test
    void keyIsPaletteOwnership() {
        assertEquals("palette-ownership", new PaletteOwnershipRegistry().key());
    }

    @Test
    void captureIsDefensiveCopy() {
        PaletteOwnershipRegistry reg = new PaletteOwnershipRegistry();
        PaletteOwnershipSnapshot snap = reg.capture();
        // Mutation of the captured array must not affect the snapshot
        snap.owners()[0] = "mutated";
        // Capture again and verify the registry's state hasn't changed
        PaletteOwnershipSnapshot fresh = reg.capture();
        assertEquals("none", fresh.owners()[0]);
    }

    @Test
    void beginFrameWipesOwnership() {
        PaletteOwnershipRegistry reg = new PaletteOwnershipRegistry();
        String[] owners = new String[128];
        java.util.Arrays.fill(owners, "none");
        owners[0] = "some-owner";
        reg.restore(new PaletteOwnershipSnapshot(owners));
        reg.beginFrame();
        PaletteOwnershipSnapshot afterBegin = reg.capture();
        assertEquals("none", afterBegin.owners()[0]);
    }

    @Test
    void restoreClearsTransientWritesFromLaterFrames() {
        PaletteOwnershipRegistry reg = new PaletteOwnershipRegistry();
        Palette[] palettes = new Palette[] {new Palette()};
        reg.submit(PaletteWrite.normal("frame-a", 1, 0, 0, new byte[] {0x0E, 0x00}));
        reg.resolveInto(palettes, null, null, null);
        PaletteOwnershipSnapshot frameASnapshot = reg.capture();

        reg.submit(PaletteWrite.normal("frame-b", 1, 0, 0, new byte[] {0x00, 0x0E}));
        reg.restore(frameASnapshot);
        reg.resolveInto(palettes, null, null, null);

        assertEquals("frame-a", reg.ownerAt(PaletteSurface.NORMAL, 0, 0));
        assertEquals((byte) 0x00, palettes[0].colors[0].r);
        assertEquals((byte) 0xFF, palettes[0].colors[0].b);
        assertFalse(reg.hasResolvedThisFrame(),
                "restore must discard queued writes because snapshots are captured at frame boundaries");
    }

    @Test
    void snapshotUsesCompactOwnerIdsInsteadOfFlatStringComponent() {
        java.util.Map<String, Class<?>> components = java.util.Arrays.stream(
                        PaletteOwnershipSnapshot.class.getRecordComponents())
                .collect(java.util.stream.Collectors.toMap(
                        java.lang.reflect.RecordComponent::getName,
                        java.lang.reflect.RecordComponent::getType));

        assertFalse(components.containsKey("owners"));
        assertEquals(byte[].class, components.get("ownerIds"));
        assertEquals(String[].class, components.get("ownerTable"));
    }

    @Test
    void targetPaletteBytesAndOwnersRoundTripDefensively() {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        byte[] patch = {0x00, 0x22, 0x00, 0x44};
        registry.applyTargetPatch("fbz.background", 3, 2, patch);
        PaletteOwnershipSnapshot snapshot = registry.capture();

        registry.clear();
        registry.applyTargetPatch("later", 3, 2, new byte[]{0x00, 0x66, 0x00, (byte) 0x88});
        byte[] leaked = snapshot.targetSegaData();
        leaked[3 * 32 + 4] = 0x7F;
        registry.restore(snapshot);

        assertArrayEquals(patch, registry.targetSegaData(3, 2, 2));
        assertEquals("fbz.background", registry.targetOwnerAt(3, 2));
        assertArrayEquals(patch, java.util.Arrays.copyOfRange(snapshot.targetSegaData(), 3 * 32 + 4, 3 * 32 + 8));
    }

    @Test
    void malformedTargetSnapshotIsRejected() {
        PaletteOwnershipSnapshot snapshot = new PaletteOwnershipRegistry().capture();
        assertThrows(IllegalArgumentException.class, () -> new PaletteOwnershipSnapshot(
                snapshot.ownerIds(), snapshot.ownerTable(), false,
                new byte[127], snapshot.targetOwnerIds(), snapshot.targetOwnerTable()));
    }

    @Test
    void packedSnapshotRejectsDuplicateAndOversizedOwnerTables() {
        byte[] ids = new byte[128];
        assertThrows(IllegalArgumentException.class, () -> new PaletteOwnershipSnapshot(
                ids, new String[]{"duplicate", "duplicate"}, false));
        String[] oversized = new String[256];
        for (int i = 0; i < oversized.length; i++) oversized[i] = "owner-" + i;
        assertThrows(IllegalArgumentException.class, () -> new PaletteOwnershipSnapshot(ids, oversized, false));
    }
}
