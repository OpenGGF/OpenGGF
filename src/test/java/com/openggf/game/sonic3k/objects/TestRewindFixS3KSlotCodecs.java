package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.DynamicObjectRewindCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard that verifies {@link Sonic3kObjectRegistry} registers a rewind codec for
 * each of the three slot-machine bonus-stage dynamic objects.
 *
 * <p>Without these codecs, {@code recreateDynamicObject()} returns {@code null}
 * for slot-stage objects captured in a rewind keyframe and they are silently
 * dropped on restore — softlocking the bonus stage on rewind. The codecs resolve
 * the live {@link com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController}
 * via the injected {@code ObjectServices#bonusStageProviderOrNull()} accessor and
 * reconstruct a structurally correct instance; all mutable scalar state is then
 * reapplied by the generic field capturer.
 */
class TestRewindFixS3KSlotCodecs {

    @Test
    void slotBonusCageHasRewindCodec() {
        assertHasCodec(S3kSlotBonusCageObjectInstance.class);
    }

    @Test
    void slotRingRewardHasRewindCodec() {
        assertHasCodec(S3kSlotRingRewardObjectInstance.class);
    }

    @Test
    void slotSpikeRewardHasRewindCodec() {
        assertHasCodec(S3kSlotSpikeRewardObjectInstance.class);
    }

    private static void assertHasCodec(Class<?> targetClass) {
        List<DynamicObjectRewindCodec> codecs = new Sonic3kObjectRegistry().dynamicRewindCodecs();
        Set<String> classNames = codecs.stream()
                .map(DynamicObjectRewindCodec::className)
                .collect(Collectors.toUnmodifiableSet());
        assertTrue(
                classNames.contains(targetClass.getName()),
                "Expected a DynamicObjectRewindCodec with className() == \""
                        + targetClass.getName()
                        + "\" in Sonic3kObjectRegistry.dynamicRewindCodecs(), but none was found. "
                        + "Slot-stage objects must have rewind codecs or they are silently dropped "
                        + "on restore, softlocking the bonus stage on rewind.");
    }
}
