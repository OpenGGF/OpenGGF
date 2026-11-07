package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.objects.CdObjectAnimation;
import java.util.Objects;

/**
 * Finite borrowed operands for CC solids/hazards. This format holder authenticates nothing:
 * Root must borrow every view from the actual CdProgramAssets-backed programme definition.
 * Canonical tables may be shorter than the finite native read hull: ADD.B motion indices
 * admit offsets through252, Drum admits255..257, Door reads its full low nibble.
 * Adjacent actual programme bytes are data operands here, never decoded instructions.
 * No media reader, disassembly fallback, inferred period delta, art residency or live authority.
 * Source N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Collision Chaos object Main.asm bodies.
 */
public record CdR31SolidHazardAssets(
        int spikesMap, int blockMap, int hiddenMap, int metalMap, int doorMap, int fireMap, int drumMap,
        CdAssetBytes spikesProperties, CdAssetBytes blockMotion, CdAssetBytes metalRight, CdAssetBytes metalLeft,
        CdAssetBytes metalUp, CdAssetBytes metalDown,
        CdAssetBytes metalDrop5, CdAssetBytes metalDrop6, CdAssetBytes metalDrop7, CdAssetBytes doorDelays, CdAssetBytes drumMotion, CdAssetBytes drumPriorities,
        CdObjectAnimation.AnimationTable fireAnimation) {
    public CdR31SolidHazardAssets {
        for (int address : new int[]{spikesMap, blockMap, hiddenMap, metalMap, doorMap, fireMap, drumMap}) {
            if (address < 0x200000 || address >= 0x240000) throw new IllegalArgumentException("Original Word RAM mapping");
        }
        extent(spikesProperties, 12); extent(blockMotion, 256); extent(metalRight, 256); extent(metalLeft, 256); extent(metalUp, 256); extent(metalDown, 256);
        extent(metalDrop5, 256); extent(metalDrop6, 256); extent(metalDrop7, 256);
        extent(doorDelays, 16); extent(drumMotion, 258); extent(drumPriorities, 256);
        Objects.requireNonNull(fireAnimation);
    }
    private static void extent(CdAssetBytes bytes, int size) {
        if (Objects.requireNonNull(bytes).length() != size) throw new IllegalArgumentException("Finite CC table extent " + size);
    }
}
