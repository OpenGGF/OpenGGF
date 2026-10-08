package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms CNZ1 miniboss (object id 0xA6) is exposed as implemented in the
 * S3KL zone-set's id allowlist used by ObjectDiscoveryTool and audit reports.
 *
 * <p>0xA6 is zone-set-specific:
 * <ul>
 *   <li>S3KL (zones 0-6): CNZMiniboss — implemented in this workstream.</li>
 *   <li>SKL (zones 7-13): DEZMiniboss — also implemented, so the numeric slot is shared.</li>
 * </ul>
 */
class TestCnzMinibossRegistered {

    @Test
    void cnzMinibossIdIsInS3klImplementedSet() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        // S3KL set is exposed via getImplementedIds() when no level is set
        // (default S3KL behaviour). For zone-set-aware lookup, getImplementedIdsForLevel
        // routes through SHARED + S3KL/SKL.
        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.CNZ_MINIBOSS),
                "S3KL_IMPLEMENTED_IDS must contain CNZ_MINIBOSS (0xA6)");
    }

    @Test
    void numericSlotIsImplementedInBothPointerTables() {
        // Shared coverage means both numeric slots are implemented, not that
        // the two pointer tables select the same boss class.
        assertTrue(Sonic3kObjectProfile.SHARED_IMPLEMENTED_IDS.contains(Sonic3kObjectIds.CNZ_MINIBOSS));
    }
}
