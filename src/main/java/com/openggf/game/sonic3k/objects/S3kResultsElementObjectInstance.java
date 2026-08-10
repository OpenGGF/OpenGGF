package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * One native SST created from {@code ObjArray_LevResults}.
 *
 * <p>ROM: {@code Obj_LevelResultsCreate}, {@code LevResults_SlideIn}, and
 * {@code LevResults_SlideOut}. Keeping each entry in a real object slot is
 * load-bearing: AllocateObjectAfterCurrent failure/retry, execution order,
 * rewind identity, and act-transition carry all observe the native SST graph.
 */
public final class S3kResultsElementObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    static final int ENTRY_COUNT = 12;
    private static final int VDP_OFFSET = 128;
    private static final int SLIDE_IN_SPEED = 16;
    private static final int SLIDE_OUT_SPEED = 32;
    private static final int NATIVE_SCREEN_WIDTH = 320;

    enum Role {
        CHARACTER_NAME,
        GENERAL,
        TIME_BONUS,
        RING_BONUS,
        TOTAL
    }

    private transient S3kResultsScreenObjectInstance parentResults;
    private int parentSlot;
    private int entryIndex;
    private Role role;
    private int targetX;
    private int startX;
    private int currentX;
    private int screenY;
    private int mappingFrame;
    private int widthPixels;
    private int exitQueuePriority;
    private boolean slidesFromLeft;
    private boolean exitStarted;
    /** Prior Render_Sprites bit 7 for this screen-space SST. */
    private boolean renderedOnScreen;
    private boolean completionReported;

    S3kResultsElementObjectInstance(S3kResultsScreenObjectInstance parentResults,
                                    int entryIndex,
                                    PlayerCharacter character) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "S3kResultsElement");
        setRomWorldPositioned(false);
        this.parentResults = parentResults;
        this.parentSlot = parentResults != null ? parentResults.getSlotIndex() : -1;
        this.entryIndex = entryIndex;
        applyEntrySpec(entryIndex, character);
        this.currentX = startX;
        this.slidesFromLeft = startX < 0;
    }

    private S3kResultsElementObjectInstance() {
        this(null, 0, PlayerCharacter.SONIC_AND_TAILS);
    }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(ctx.objectServices(),
                S3kResultsElementObjectInstance::new);
    }

    @Override
    protected void afterRewindRestoreSettled() {
        if (parentResults != null && parentResults.getSlotIndex() == parentSlot
                && !parentResults.isDestroyed()) {
            return;
        }
        parentResults = null;
        if (services().objectManager() == null) {
            return;
        }
        for (ObjectInstance object : services().objectManager().getActiveObjects()) {
            if (object instanceof S3kResultsScreenObjectInstance candidate
                    && !candidate.isDestroyed()
                    && candidate.getSlotIndex() == parentSlot) {
                parentResults = candidate;
                return;
            }
        }
    }

    private void applyEntrySpec(int index, PlayerCharacter character) {
        switch (index) {
            case 0 -> setSpec(Role.CHARACTER_NAME, 0xE0, -0x220, 0xB8,
                    characterFrame(character), 0x48, 1);
            case 1 -> setSpec(Role.GENERAL, 0x130, -0x1D0, 0xB8, 0x11, 0x30, 1);
            case 2 -> setSpec(Role.GENERAL, 0xE8, 0x468, 0xCC, 0x10, 0x70, 3);
            case 3 -> setSpec(Role.GENERAL, 0x160, 0x4E0, 0xBC, 0x0F, 0x38, 3);
            case 4 -> setSpec(Role.GENERAL, 0xC0, 0x4C0, 0xF0, 0x0E, 0x20, 5);
            case 5 -> setSpec(Role.GENERAL, 0xE8, 0x4E8, 0xF0, 0x0C, 0x30, 5);
            case 6 -> setSpec(Role.TIME_BONUS, 0x178, 0x578, 0xF0, 1, 0x40, 5);
            case 7 -> setSpec(Role.GENERAL, 0xC0, 0x500, 0x100, 0x0D, 0x20, 7);
            case 8 -> setSpec(Role.GENERAL, 0xE8, 0x528, 0x100, 0x0C, 0x30, 7);
            case 9 -> setSpec(Role.RING_BONUS, 0x178, 0x5B8, 0x100, 1, 0x40, 7);
            case 10 -> setSpec(Role.GENERAL, 0xD4, 0x554, 0x11C, 0x0B, 0x30, 9);
            case 11 -> setSpec(Role.TOTAL, 0x178, 0x5F8, 0x11C, 1, 0x40, 9);
            default -> throw new IllegalArgumentException("Invalid results entry " + index);
        }
        if (index == 0 && character == PlayerCharacter.KNUCKLES) {
            targetX -= 0x30;
            startX -= 0x30;
            widthPixels += 0x30;
        } else if (index == 0 && character == PlayerCharacter.TAILS_ALONE) {
            targetX += 8;
            startX += 8;
            widthPixels -= 8;
        }
    }

    private void setSpec(Role role, int targetVdpX, int startVdpX, int vdpY,
                         int mappingFrame, int widthPixels, int exitQueuePriority) {
        this.role = role;
        this.targetX = targetVdpX - VDP_OFFSET;
        this.startX = startVdpX - VDP_OFFSET;
        this.screenY = vdpY - VDP_OFFSET;
        this.mappingFrame = mappingFrame;
        this.widthPixels = widthPixels;
        this.exitQueuePriority = exitQueuePriority;
    }

    private static int characterFrame(PlayerCharacter character) {
        return switch (character) {
            case TAILS_ALONE -> 0x15;
            case KNUCKLES -> 0x16;
            default -> 0x13;
        };
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parentResults == null || completionReported || isDestroyed()) {
            return;
        }
        if (!exitStarted && parentResults.shouldExitElement(exitQueuePriority)) {
            exitStarted = true;
        }
        if (exitStarted) {
            // LevResults_MoveElement tests the render bit produced by the
            // preceding Render_Sprites pass before applying this dispatch's
            // movement. Crossing an edge therefore retires on the next update.
            if (!renderedOnScreen) {
                completionReported = true;
                parentResults.childExited(this);
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            currentX += slidesFromLeft ? -SLIDE_OUT_SPEED : SLIDE_OUT_SPEED;
            renderedOnScreen = isWithinNativeRenderWindow();
            return;
        }

        if (currentX < targetX) {
            currentX = Math.min(currentX + SLIDE_IN_SPEED, targetX);
        } else if (currentX > targetX) {
            currentX = Math.max(currentX - SLIDE_IN_SPEED, targetX);
        }
        renderedOnScreen = isWithinNativeRenderWindow();
    }

    private boolean isWithinNativeRenderWindow() {
        return withinNativeRenderWindow(currentX, widthPixels);
    }

    static boolean withinNativeRenderWindow(int screenX, int widthPixels) {
        return screenX + widthPixels >= 0
                && screenX - widthPixels < NATIVE_SCREEN_WIDTH;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (parentResults != null && !completionReported) {
            parentResults.appendElementRender(this, commands);
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return screenY;
    }

    public int entryIndex() {
        return entryIndex;
    }

    public S3kResultsScreenObjectInstance parentResults() {
        return parentResults;
    }

    Role role() {
        return role;
    }

    int currentScreenX() {
        return currentX;
    }

    int screenY() {
        return screenY;
    }

    int mappingFrame() {
        return mappingFrame;
    }
}
