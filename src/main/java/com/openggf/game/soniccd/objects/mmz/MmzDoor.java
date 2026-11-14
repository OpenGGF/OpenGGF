package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * Metallic Madness's door, object $29 in every index ({@code DoorObject}, objects/r8/door.asm:3-224): two solid halves
 * (width 8, height $10, tile $47E) that part 2 pixels a frame, the top to Y - $41 and the bottom to Y + $21 from the
 * closed Y - $20 / Y. Subtypes 1 and 2 open when the player is in a box left of the door and close when he leaves a
 * wider one; subtype 0 (the Hotaru intro's door at ($CA8, $1D0) on act 3) opens once when its {@code var_3f} is set
 * ({@code st $3F(a1)}, HotaruObject_0_RoutineC, hotaru.asm:562-571) and stays open.
 *
 * <p>The door is a zone object (R8 Shared.asm:195-268 links door.asm in every variant); the boss stage's Hotaru
 * allocates one through {@link #requestOpen()} and {@link Opener}. Native runs both halves' {@code SolidObject} from the
 * bottom half's routine (top first, sub_20D112, door.asm:169-175) and only then moves both halves (:116-117, 154-155).
 * Here each half calls its own from its own slot (the top half is allocated after the bottom one, so its call comes later
 * in the frame); the bottom half leaves the top half's new Y pending, and the top half takes it after its solid call,
 * so both solids see the halves where they were before the move. R83C-fight row 3199: the player's head meets the
 * opening top half on its unmoved position.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzDoor extends MmzObject implements ReevaluatingSolidObject, RewindRecreatable, ObjectVisualFacing.NonDirectional {
    public static final int ID = 0x29;

    /** Told when the door leaves the object manager, so a holder of the link forgets it (the slot no longer holds a $29). */
    public interface Opener {
        void doorRemoved(MmzDoor door);
    }

    /** var_2a: the bottom half the top half belongs to; var_2c: the top half. */
    private MmzDoor bottomHalf;
    private MmzDoor topHalf;
    /** var_2e: the closed centre; var_30 / var_32: the closed and open Y. */
    private int centre;
    private int closedY;
    private int openY;
    /** var_3f: the open request. */
    private boolean openRequest;
    /** The object that allocated this door and opens it (the Hotaru intro's link). */
    private AbstractObjectInstance opener;
    /** The top half (the {@code var_2a} link's holder), also once its bottom half has gone. */
    private boolean upper;
    /** The top half's Y written by its bottom half's move this frame, taken after its own solid call; -1 for none. */
    private int pendingY = -1;
    private int routine;
    private int mapFrame;
    private int sheetIndex;
    private boolean skipDraw;
    // AnimateObject's bytes (Object Animate.asm:14-94): oAnim is always 0 for the door.
    private int animFrame;
    private int animTime;
    private boolean animStarted;

    public MmzDoor(ObjectSpawn spawn) {
        super(spawn, "Door");
    }

    private MmzDoor(int x, int y, MmzDoor bottomHalf) {
        super(new ObjectSpawn(x, y, ID, 0, 0, false, 0), "Door");
        this.bottomHalf = bottomHalf;
        this.upper = true;
    }

    /** {@code st obj.var_3f(a1)}: ask the door to open (hotaru.asm:562-571). */
    public void requestOpen() {
        openRequest = true;
    }

    public void setOpener(AbstractObjectInstance opener) {
        this.opener = opener;
    }

    public void clearOpener() {
        opener = null;
    }

    private int doorType() {
        return getSpawn().subtype() & 0xFF;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // DoorObject_0_Routine0 ends in rts (no DrawObject); the bottom half draws both halves (loc_20D066).
        skipDraw = upper ? bottomHalf == null || bottomHalf.skipDraw : routine == 0;
        if (upper) {
            // loc_20D14A (door.asm:195-199): the top half has no routine of its own; it goes when its bottom half does.
            if (bottomHalf == null || bottomHalf.isDestroyed()) {
                setDestroyed(true);                               // loc_20D156: DeleteObject (:203-204)
                return;
            }
            solidObject(Entry.SOLID);
            if (pendingY >= 0) {                                  // the move after sub_20D112 (door.asm:116-117, 154-155)
                y = pendingY;
                pendingY = -1;
            }
            return;
        }
        switch (routine) {
            case 0 -> init();
            case 2 -> closed(player);
            case 4 -> opening();
            case 6 -> open(player);
            case 8 -> closing();
            default -> { }
        }
    }

    /** DoorObject_0_Routine0 / sub_20CFF6 (door.asm:22-63). */
    private void init() {
        routine = 2;
        centre = (y - 0x10) & 0xFFFF;                             // :24-25
        sheetIndex = doorType() + 1;                              // off_20D02C: DoorSprites1-3 by subtype (:26-31, :67-70)
        MmzDoor top = new MmzDoor(x, y, this);
        var objects = services().objectManager();
        if (objects == null) return;
        objects.addDynamicObjectAfterCurrent(top);                // jsr SpawnObjectAfter (:32)
        if (top.isDestroyed()) {
            setDestroyed(true);                                   // bne.w loc_20D156 (:33)
            return;
        }
        topHalf = top;
        top.sheetIndex = sheetIndex;
        top.halfInit(centre, -0x10, -0x31);                       // sub_20CFF6 then sub d0 / d1 (:39-42)
        halfInit(centre, 0x10, 0x31);                             // add d0 / d1 (:43-47)
    }

    /** sub_20CFF6 (door.asm:52-63) with the offsets d0 = $10 and d1 = $31 applied by the caller (:40-47). */
    private void halfInit(int base, int closedOffset, int openOffset) {
        priority = 3;
        tileWord = MmzObjectArt.DOOR_TILE;
        width = 8;
        yRadius = 0x10;
        centre = base;
        y = (base + closedOffset) & 0xFFFF;
        closedY = y;
        openY = (base + openOffset) & 0xFFFF;
    }

    /** DoorObject_0_Routine2 (door.asm:74-110). */
    private void closed(PlayableEntity player) {
        solidObject(Entry.SOLID);
        boolean open;
        if (openRequest) {
            openRequest = false;
            open = true;
        } else {
            open = doorType() != 0 && near(player, doorType() == 1 ? 8 : -0x48, 0x40);
        }
        if (open) routine = 4;
        animateHalves();
    }

    /** DoorObject_0_Routine4 (door.asm:114-127). */
    private void opening() {
        solidObject(Entry.SOLID);
        y = (y + 2) & 0xFFFF;
        if (topHalf != null) topHalf.moveTo((topHalf.y - 2) & 0xFFFF);
        if ((short) y >= (short) openY) {
            routine = 6;
            y = openY;
            if (topHalf != null) topHalf.moveTo(topHalf.openY);
        }
        animateHalves();
    }

    /** DoorObject_0_Routine6 (door.asm:131-148). */
    private void open(PlayableEntity player) {
        solidObject(Entry.SOLID);
        if (doorType() != 0 && !near(player, doorType() == 1 ? -0x28 : -0x48, 0x70)) routine = 8;
        animateHalves();
    }

    /** DoorObject_0_Routine8 (scddisasm/src/objects/r8/door.asm:152-165). */
    private void closing() {
        solidObject(Entry.SOLID);
        y = (y - 2) & 0xFFFF;
        if (topHalf != null) topHalf.moveTo((topHalf.y + 2) & 0xFFFF);
        if ((short) y <= (short) closedY) {
            routine = 2;
            y = closedY;
            if (topHalf != null) topHalf.moveTo(topHalf.closedY);
        }
        animateHalves();
    }

    /** The top half's new Y, taken in its own slot after its solid call (see the class comment). */
    private void moveTo(int newY) {
        pendingY = newY & 0xFFFF;
    }

    /**
     * sub_20D12A (scddisasm/src/objects/r8/door.asm:179-191): carry from {@code player.x - x - d4 - d5} (the player is within d5 pixels from x + d4),
     * then from {@code player.y - centre + $30 - $60} (within $30 of the centre).
     */
    private boolean near(PlayableEntity player, int d4, int d5) {
        if (player == null) return false;
        int dx = (player.getCentreX() - x - d4) & 0xFFFF;
        if (dx >= (d5 & 0xFFFF)) return false;
        int dy = (player.getCentreY() - centre + 0x30) & 0xFFFF;
        return dy < 0x60;
    }

    /** loc_20D066 (scddisasm/src/objects/r8/door.asm:100-104): AnimateObject (DoorAnims); the top half shows the same frame. */
    private void animateHalves() {
        animate();
        if (topHalf != null) topHalf.mapFrame = mapFrame;
    }

    /**
     * {@code AnimateObject} (Level/Object Animate.asm:14-94) over {@code DoorAnims}, animation 0 only, in plain fields so the
     * object rewinds by the generic capture: $FF loops, $FE steps back, $FD changes animation (the door has none), $FC is
     * routine + 2 (the door ignores the result).
     */
    private void animate() {
        var provider = artProvider();
        if (provider == null) return;
        int table = MmzObjectCatalogue.offset(provider.payload(), "doorAnimations");
        if (table < 0) return;
        var image = provider.image();
        if (!animStarted) {                                       // oAnim ($0) != oPrevAnim ($FF)
            animStarted = true;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return;
        int script = table + (short) image.u16(table);
        animTime = image.u8(script);
        int value = image.u8(script + 1 + animFrame);
        if (value < 0x80) {
            mapFrame = value & 0x1F;
            animFrame = (animFrame + 1) & 0xFF;
        } else if (value == 0xFF) {
            animFrame = 0;
            mapFrame = image.u8(script + 1) & 0x1F;
            animFrame = (animFrame + 1) & 0xFF;
        } else if (value == 0xFE) {
            animFrame = (animFrame - image.u8(script + 2 + animFrame)) & 0xFF;
            mapFrame = image.u8(script + 1 + animFrame) & 0x1F;
            animFrame = (animFrame + 1) & 0xFF;
        }
    }

    /** jmp CheckObjectDespawn (loc_20D066, door.asm:109-110); the top half has none. */
    @Override protected boolean despawnsByCheckObjDespawn() { return !upper; }

    /** Links to a removed half or opener are not rewound: the survivor sees the slot gone, as {@code cmpi.b #$29} does. */
    @Override
    protected void onRemovedFromObjectManager() {
        if (topHalf != null) topHalf.bottomHalf = null;
        if (bottomHalf != null) bottomHalf.topHalf = null;
        if (opener instanceof Opener o) o.doorRemoved(this);
        topHalf = null;
        bottomHalf = null;
        opener = null;
    }

    /** A shell; the generic capture restores the halves, routine and links afterwards. */
    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new MmzDoor(ctx.spawn());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || skipDraw || sheetIndex == 0) return;
        drawSheet(MmzObjectArt.DOOR + sheetIndex, mapFrame, false, false);
    }
}
