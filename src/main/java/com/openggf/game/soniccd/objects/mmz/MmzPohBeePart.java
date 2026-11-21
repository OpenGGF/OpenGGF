package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** One of Poh-Bee's swinging weights or a link. All parts use the body's native object ID. */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
final class MmzPohBeePart extends MmzObject implements TouchResponseProvider, SpawnRewindRecreatable,
        ObjectVisualFacing.NonDirectional {
    /** loc_20DAA2 (objects/r8/poh_bee.asm:112-136): the two weights start at different angles and lengths. */
    enum Role {
        LONG(0x800, 10), SHORT(0x1000, 4), LINK(0, 0);

        final int startAngle;
        final int links;

        Role(int startAngle, int links) {
            this.startAngle = startAngle;
            this.links = links;
        }
    }

    private Role role;
    private int bodySlot;
    private boolean initialised;
    private int angle;
    private int speed;
    private int acceleration;
    private int[] linkSlots;

    MmzPohBeePart(ObjectSpawn spawn) {
        this(spawn, Role.LINK, -1);
    }

    MmzPohBeePart(ObjectSpawn spawn, Role role, MmzPohBee body) {
        this(spawn, role, body.getSlotIndex());
        copyDrawState(body);
    }

    private MmzPohBeePart(ObjectSpawn spawn, Role role, int bodySlot) {
        super(spawn, "Poh-Bee chain");
        this.role = role;
        this.bodySlot = bodySlot;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        // loc_20DC18 (objects/r8/poh_bee.asm:230-239) reads the ID in the saved slot each turn.
        if (!slotHoldsId(bodySlot, MmzPohBee.ID)) {
            setDestroyed(true);
            return;
        }
        if (role == Role.LINK) {
            if (!initialised) {
                // loc_20DBF0 (objects/r8/poh_bee.asm:220-228).
                priority = 3;
                width = 8;
                yRadius = 8;
                tileWord = MmzObjectArt.POH_BEE_TILE;
                initialised = true;
            }
        } else if (slotOccupant(bodySlot) instanceof MmzPohBee body) {
            if (!initialised && !initWeight(body)) return;
            swing(body);
        }
    }

    /** loc_20DAA2 and loc_20DB06 (objects/r8/poh_bee.asm:112-163). */
    private boolean initWeight(MmzPohBee body) {
        priority = 2;
        width = 0xC;
        yRadius = 0xC;
        tileWord = MmzObjectArt.POH_BEE_TILE;
        angle = role.startAngle;
        acceleration = 8;
        linkSlots = new int[role.links];
        for (int i = 0; i < linkSlots.length; i++) {
            var linkSpawn = new ObjectSpawn(x, y, MmzPohBee.ID, spawn.subtype(), spawn.renderFlags(), false, 0);
            var link = new MmzPohBeePart(linkSpawn, Role.LINK, bodySlot);
            link.copyDrawState(this);
            if (!findNextObjSlot(link)) {
                // loc_20DB06 (scddisasm/src/objects/r8/poh_bee.asm:138-142) clears the body before returning.
                body.setDestroyed(true);
                services().objectManager().releaseSlot(body);
                return false;
            }
            linkSlots[i] = link.getSlotIndex();
        }
        initialised = true;
        return true;
    }

    /** loc_20DB5E (objects/r8/poh_bee.asm:165-216): reverse acceleration on crossing a quarter turn. */
    private void swing(MmzPohBee body) {
        int previousAngle = angle;
        speed = (short) (speed + acceleration);
        angle = (short) (angle + speed);
        if ((previousAngle < 0x4000) != (angle < 0x4000)) acceleration = -acceleration;

        int dx = TrigLookupTable.cosHex(angle >>> 8) << 11;
        int dy = TrigLookupTable.sinHex(angle >>> 8) << 11;
        int fixedX = body.fixedX();
        int fixedY = body.fixedY() + (12 << 16);
        for (int slot : linkSlots) {
            fixedX += dx;
            fixedY += dy;
            if (slotOccupant(slot) instanceof MmzPohBeePart link && link.bodySlot == bodySlot && link.role == Role.LINK) {
                link.place(fixedX, fixedY);
            }
        }
        place(fixedX + dx, fixedY + dy);
    }

    private void place(int fixedX, int fixedY) {
        x = fixedX >>> 16;
        y = fixedY >>> 16;
        xSub = fixedX & 0xFFFF;
        ySub = fixedY & 0xFFFF;
    }

    /** loc_20DAA2 (objects/r8/poh_bee.asm:112-121): only the weight hurts. */
    @Override public int getCollisionFlags() { return initialised && role != Role.LINK ? 0xAF : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) {
            int flip = spawn.renderFlags();
            drawSheet(MmzPohBee.sheetFor(spawn), role == Role.LINK ? 2 : 3, (flip & 1) != 0, (flip & 2) != 0);
        }
    }
}
