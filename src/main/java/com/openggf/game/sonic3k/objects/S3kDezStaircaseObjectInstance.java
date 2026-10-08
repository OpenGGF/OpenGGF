package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.List;

/** SKL $4F, Obj_DEZStaircase / loc_47658..loc_47814 (sonic3k.asm:93322-93511). */
public final class S3kDezStaircaseObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable, SolidObjectProvider, SolidObjectListener {
    private static final SolidObjectParams SOLID = SolidObjectParams.of(0x1B, 0x10, 0x11);
    private S3kDezStaircaseObjectInstance parent;
    private boolean initialized;
    private boolean child;
    private int currentY;
    private int anchorX;
    private int baseY;
    private int renderFlags;
    private int offsetIndex;
    private int subtype;
    private int timer;
    private int contactBits;
    private int offset0;
    private int offset1;
    private int offset2;
    private int offset3;

    public S3kDezStaircaseObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZStaircase");
        currentY = baseY = spawn.y();
        anchorX = spawn.x();
        subtype = spawn.subtype();
        renderFlags = spawn.renderFlags();
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) initialize();
        if (!child) advanceController();
        var controller = child ? parent : this;
        if (controller != null) currentY = (baseY + controller.offset(offsetIndex)) & 0xFFFF;
        updateDynamicSpawn(getX(), currentY);
    }

    private void initialize() {
        initialized = true;
        // Render-facing changes do not change the original status X-flip
        // used to reverse the four parent-word indices ($34/$36/$38/$3A).
        if ((subtype & 7) >= 4) renderFlags ^= 1;
        if ((renderFlags & 2) != 0) renderFlags ^= 1;
        boolean reverse = (spawn.renderFlags() & 1) != 0;
        offsetIndex = reverse ? 3 : 0;
        for (int index = 1; index < 4; index++) {
            final int part = index;
            var made = spawnChild(() -> {
                var section = new S3kDezStaircaseObjectInstance(new ObjectSpawn(
                        (anchorX + part * 0x20) & 0xFFFF, baseY, spawn.objectId(), subtype,
                        spawn.renderFlags(), false, spawn.rawYWord()));
                section.parent = this;
                section.child = true;
                section.initialized = true;
                section.anchorX = anchorX;
                section.renderFlags = renderFlags;
                section.offsetIndex = reverse ? 3 - part : part;
                return section;
            });
            if (made == null || made.isDestroyed()) break; // loc_47680 -> loc_476E4
        }
    }

    private void advanceController() {
        switch (subtype & 7) {
            case 0, 4 -> waitForContact(0x30, 30, false);
            case 2, 6 -> waitForContact(0x0C, 60, true);
            case 1, 3 -> extend(1);
            case 5, 7 -> extend(-1);
            default -> throw new AssertionError();
        }
    }

    private void waitForContact(int mask, int delay, boolean shake) {
        if (timer == 0) {
            if ((contactBits & mask) != 0) timer = delay;
            return;
        }
        timer = (short) (timer - 1);
        if (timer == 0) {
            subtype = (subtype + 1) & 0xFF;
            services().playSfx(Sonic3kSfx.FAN_BIG.id);
        } else if (shake) {
            // loc_4779E uses the remaining timer's low byte, then alternates
            // 0/1 offsets in the four parent words. Expiry leaves the last pose.
            int bit = (timer >>> 2) & 1;
            offset0 = offset2 = bit;
            offset1 = offset3 = bit ^ 1;
        }
    }

    private void extend(int direction) {
        if (offset0 == direction * 0x80) return;
        offset0 = (short) (offset0 + direction);
        // loc_477C2/loc_477EC shift 16:16 values before extracting the high
        // words: arithmetic right shift preserves negative fractional rounding.
        offset1 = (offset0 * 3) >> 2;
        offset2 = offset0 >> 1;
        offset3 = offset0 >> 2;
    }

    private int offset(int index) {
        return switch (index) { case 0 -> offset0; case 1 -> offset1; case 2 -> offset2; case 3 -> offset3;
            default -> throw new IllegalStateException("Invalid staircase word index " + index); };
    }

    @Override public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!initialized || player == null) return;
        var query = services().playerQuery();
        int slot = player == query.mainPlayerOrNull() ? 0 : player == query.nativeP2OrNull() ? 1 : -1;
        if (slot < 0) return;
        var controller = child ? parent : this;
        if (controller == null) return;
        // swap d6 / or.b d6,$32(parent): p1_standing_bit=3, so the
        // returned side/bottom/top bits become 0/2/4 after the swap.
        if (contact.touchSide()) controller.contactBits |= 1 << slot;
        if (contact.touchBottom()) controller.contactBits |= 4 << slot;
        if (contact.standing()) controller.contactBits |= 0x10 << slot;
    }

    @Override public int getY() { return currentY; }
    @Override public SolidObjectParams getSolidParams() { return SOLID; }
    @Override public boolean isSolidFor(PlayableEntity player) { return initialized; }
    @Override public boolean allowsObjectControlledSolidContacts() { return true; }
    @Override public boolean rejectsBit7ObjectControlNewSolidContact(PlayableEntity player) { return true; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return isCoarseXOutOfRange(anchorX, cameraX, coarseXCullRange()); }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return 3; } // priority=$180; art bit 15 clear
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_STAIRCASE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(0, getX(), getY(),
                (renderFlags & 1) != 0, (renderFlags & 2) != 0);
    }

    @Override public S3kDezStaircaseObjectInstance recreateForRewind(RewindRecreateContext context) {
        return new S3kDezStaircaseObjectInstance(context.spawn());
    }
    public int subtypeForTest() { return subtype; }
    public int timerForTest() { return timer; }
    public int offsetForTest(int index) { return offset(index); }
    public boolean childForTest() { return child; }
    public int renderFlagsForTest() { return renderFlags; }
    public S3kDezStaircaseObjectInstance parentForTest() { return parent; }
}
