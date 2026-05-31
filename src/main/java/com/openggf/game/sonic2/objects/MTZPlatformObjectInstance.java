package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.OscillationManager;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.util.LazyMappingHolder;

import java.util.List;
import java.util.logging.Logger;

/**
 * MTZ Platform (Object 0x6B) - Moving platform from Metropolis Zone and Chemical Plant Zone.
 * <p>
 * In CPZ, this object renders as a SINGLE 32x32 block. The multi-block visual effect
 * seen in the game is achieved by placing MULTIPLE separate Object 0x6B instances
 * at the same location, each with different subtypes (movement types 8, 9, 10, 11)
 * which have different radii (0x10, 0x30, 0x50, 0x70). The oscillators for these
 * types are synchronized but at different amplitudes, creating coordinated but
 * differential movement.
 * <p>
 * In MTZ, this object uses different mappings with multi-block frames, but all
 * blocks still move as one unit (single x_pos, y_pos).
 * <p>
 * Movement types (from s2.asm lines 53860-54159):
 * <ul>
 *   <li>Type 0: Stationary</li>
 *   <li>Types 1-2: Horizontal oscillation (amplitudes 0x40, 0x80)</li>
 *   <li>Types 3-4: Vertical oscillation (amplitudes 0x40, 0x80)</li>
 *   <li>Types 5-6: Triggered falling</li>
 *   <li>Type 7: Bouncy platform</li>
 *   <li>Types 8-11: Circular/square motion with radii 0x10, 0x30, 0x50, 0x70</li>
 * </ul>
 */
public class MTZPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(MTZPlatformObjectInstance.class.getName());

    // Subtype properties from Obj6B_Properties (line 53879-53881)
    // Format: {width_pixels, y_radius, mapping_frame}
    private static final int[][] SUBTYPE_PROPERTIES = {
            {32, 12, 1}, // Index 0: width=32, y_radius=12, frame 1
            {16, 16, 0}, // Index 1: width=16, y_radius=16, frame 0
    };

    // Circular motion radii for types 8-11
    private static final int[] CIRCULAR_RADII = {0x10, 0x30, 0x50, 0x70};

    /** Shared Obj65_a level-art mappings for MTZ rendering, lazily loaded from ROM. */
    private static final LazyMappingHolder MAPPINGS = new LazyMappingHolder();

    // Position tracking
    private int x;
    private int y;
    private int baseX;      // objoff_34 - Original X position
    private int baseY;      // objoff_30 - Original Y position
    private int yFixed;     // 16.8 fixed-point Y for falling/bouncing
    private int yVel;       // Y velocity for falling/bouncing

    // Subtype configuration
    private int moveType;
    private int widthPixels;
    private int yRadius;
    private int mappingFrame;

    // State tracking
    private int flipState;      // objoff_2E - Circular motion quadrant (0-3)
    private int bounceAccel;
    private boolean xFlip;

    // Contact tracking
    private boolean contactStanding;

    public MTZPlatformObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        init();
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }
    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly line 53930-53937:
        // d1 = width_pixels + 11 (half-width for collision)
        int halfWidth = widthPixels + 0x0B;
        return new SolidObjectParams(halfWidth, yRadius, yRadius + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        // Obj6B uses regular SolidObject (jsrto JmpTo14_SolidObject), not PlatformObject,
        // so it's fully solid from all sides, not just the top.
        return false;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fromProvider(this);
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (contact.standing() || contact.touchTop()) {
            contactStanding = true;
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        applyMovement(frameCounter);
        updateDynamicSpawn(x, y);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        // Flip from status (objoff_2E low bits): bit0 = x_flip, bit1 = y_flip.
        boolean yFlip = (flipState & 0x02) != 0;

        // ROM Obj6B selects mappings/art by zone (s2.asm:53911-53916):
        //   CPZ (cmpi.b #chemical_plant_zone): Obj6B_MapUnc_2800E + ArtNem_CPZStairBlock
        //   otherwise (MTZ): Obj65_Obj6A_Obj6B_MapUnc_26EC8 + ArtKos_LevelArt line 3
        // This is per-object zone routing at the owning object boundary, mirroring the
        // ROM's own Current_Zone branch — not a zone carve-out in shared engine code.
        if (services().currentZone() == Sonic2ZoneConstants.ROM_ZONE_CPZ) {
            // CPZ stair-block: dedicated Nemesis sheet (single 32x32 block = sheet frame 2).
            PatternSpriteRenderer renderer =
                    renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_STAIR_BLOCK);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(2, x, y, xFlip, yFlip);
            }
            return;
        }

        // MTZ: render the shared Obj65_a level-art mappings against level art
        // (ArtTile_ArtKos_LevelArt, base tile 0) on VDP palette line 3.
        // mapping_frame from Obj6B_Properties (s2.asm:53928) -> SUBTYPE_PROPERTIES[*][2].
        List<SpriteMappingFrame> mappings = MAPPINGS.get(
                Sonic2Constants.MAP_UNC_MTZ_PLATFORM_LEVELART_ADDR,
                S2SpriteDataLoader::loadMappingFrames, "Obj65a");
        if (mappings.isEmpty() || mappingFrame >= mappings.size()) {
            return;
        }
        SpriteMappingFrame mapFrame = mappings.get(mappingFrame);
        if (mapFrame == null || mapFrame.pieces().isEmpty()) {
            return;
        }
        GraphicsManager graphicsManager = services().graphicsManager();
        if (graphicsManager == null) {
            return;
        }
        SpritePieceRenderer.renderPieces(
                mapFrame.pieces(),
                x, y,
                0,   // level art starts at tile 0
                3,   // palette line 3
                xFlip, yFlip,
                (patternIndex, pieceHFlip, pieceVFlip, paletteIndex, px, py) -> {
                    int descIndex = patternIndex & 0x7FF;
                    if (pieceHFlip) {
                        descIndex |= 0x800;
                    }
                    if (pieceVFlip) {
                        descIndex |= 0x1000;
                    }
                    descIndex |= (paletteIndex & 0x3) << 13;
                    graphicsManager.renderPattern(new PatternDesc(descIndex), px, py);
                });
    }

    private void init() {
        // Property extraction: (subtype >> 2) & 0x1C gives byte offset
        int propsOffset = (spawn.subtype() >> 2) & 0x1C;
        int propsIndex = Math.min(propsOffset / 4, SUBTYPE_PROPERTIES.length - 1);
        if (propsIndex < 0) {
            propsIndex = 0;
        }

        widthPixels = SUBTYPE_PROPERTIES[propsIndex][0];
        yRadius = SUBTYPE_PROPERTIES[propsIndex][1];
        // ROM Obj6B_Init (s2.asm:53925-53928) reads width_pixels, y_radius AND mapping_frame
        // from Obj6B_Properties (3rd byte of the 4-byte entry). Properties entry 0 = {32,12,1},
        // entry 1 = {16,16,0}. So the mapping_frame comes straight from the table.
        mappingFrame = SUBTYPE_PROPERTIES[propsIndex][2];

        // Store base positions
        baseX = spawn.x();
        baseY = spawn.y();
        x = baseX;
        y = baseY;
        yFixed = baseY << 8;

        // Movement type from bits 0-3 (clamped to 0-11)
        moveType = spawn.subtype() & 0x0F;
        if (moveType > 11) {
            moveType = 0;
        }

        // flipState (objoff_2E) is initialized from status byte which has both xFlip and yFlip
        // This creates a 2-bit quadrant value (0-3) for circular motion
        // Bit 0 = xFlip, Bit 1 = yFlip
        xFlip = (spawn.renderFlags() & 0x01) != 0;
        flipState = spawn.renderFlags() & 0x03;  // Both bits: 0-3 range

        // Special init for circular motion (types 8-11):
        // If oscillator delta is negative, toggle bit 0 of flipState
        if (moveType >= 8 && moveType <= 11) {
            int oscOffset = 0x2A + ((moveType - 8) * 4);
            int oscWord = OscillationManager.getWord(oscOffset);
            if (oscWord < 0) {
                flipState ^= 1;  // Toggle bit 0 (bchg #0,objoff_2E)
            }
        }

        yVel = 0;
        bounceAccel = 0;

        updateDynamicSpawn(x, y);
    }

    /**
     * Applies movement based on movement type.
     */
    private void applyMovement(int frameCounter) {
        boolean standing = contactStanding;
        contactStanding = false;

        switch (moveType) {
            case 0 -> { /* Stationary - no movement */ }
            case 1 -> applyHorizontalOscillation(0x08, 0x40);
            case 2 -> applyHorizontalOscillation(0x1C, 0x80);
            case 3 -> applyVerticalOscillation(0x08, 0x40);
            case 4 -> applyVerticalOscillation(0x1C, 0x80);
            case 5 -> applyTriggerFall(standing);
            case 6 -> applyFalling();
            case 7 -> applyBouncy(standing);
            case 8, 9, 10, 11 -> applyCircularMotion(moveType - 8);
        }
    }

    /**
     * Movement types 1-2: Horizontal oscillation.
     */
    private void applyHorizontalOscillation(int oscOffset, int amplitude) {
        int oscValue = OscillationManager.getByte(oscOffset) & 0xFF;

        if (xFlip) {
            oscValue = -oscValue + amplitude;
        }

        x = baseX - oscValue;
        y = baseY;
    }

    /**
     * Movement types 3-4: Vertical oscillation.
     */
    private void applyVerticalOscillation(int oscOffset, int amplitude) {
        int oscValue = OscillationManager.getByte(oscOffset) & 0xFF;

        if (xFlip) {
            oscValue = -oscValue + amplitude;
        }

        x = baseX;
        y = baseY - oscValue;
    }

    /**
     * Movement type 5: Trigger on standing.
     */
    private void applyTriggerFall(boolean standing) {
        int oscValue = OscillationManager.getByte(0) & 0xFF;
        y = baseY + (oscValue >> 1);
        x = baseX;

        if (standing) {
            moveType = 6;
            yFixed = y << 8;
        }
    }

    /**
     * Movement type 6: Falling platform.
     */
    private void applyFalling() {
        yFixed += yVel;
        y = yFixed >> 8;
        yVel += 8;
        x = baseX;

        Camera camera = services().camera();
        int maxY = camera != null ? camera.getMaxY() + 224 : baseY + 500;

        // ROM loc_27EE2 (s2.asm:54057-54061): when y_pos passes Camera_Max_Y + screen_height
        // (224), set subtype=0 (immobile) and STOP — the platform keeps falling out of view.
        // Do NOT reset y_pos or recycle the platform (the old moveType=5 + y reset was wrong).
        if (y > maxY) {
            moveType = 0;
        }
    }

    /**
     * Movement type 7: Bouncy platform.
     */
    private void applyBouncy(boolean standing) {
        x = baseX;

        if (bounceAccel == 0) {
            if (standing) {
                bounceAccel = 8;
            }
            return;
        }

        yFixed += yVel;
        y = (yFixed >> 8) & 0x7FF;

        if (yVel == 0x2A8) {
            bounceAccel = -bounceAccel;
        }

        yVel += bounceAccel;

        if (yVel == 0) {
            moveType = 0;
            bounceAccel = 0;
            y = baseY;
            yFixed = baseY << 8;
        }
    }

    /**
     * Movement types 8-11: Circular/square motion.
     * Each type uses a different oscillator with different radius but synchronized timing.
     * Multiple Object 0x6B instances at the same location with types 8, 9, 10, 11
     * create the visual effect of blocks moving at different rates.
     */
    private void applyCircularMotion(int typeIndex) {
        int radius = CIRCULAR_RADII[typeIndex];
        int oscOffset = 0x28 + (typeIndex * 4);

        int d0 = OscillationManager.getByte(oscOffset) & 0xFF;
        if (typeIndex == 0) {
            d0 = d0 >> 1;
        }
        int d3 = OscillationManager.getWord(oscOffset + 2);

        // Advance quadrant when delta crosses zero
        if (d3 == 0) {
            flipState = (flipState + 1) & 0x03;
        }

        // Position calculation based on current quadrant
        switch (flipState & 0x03) {
            case 0 -> {
                x = baseX - radius + d0;
                y = baseY - radius;
            }
            case 1 -> {
                x = baseX + radius;
                y = baseY + radius - d0 - 1;
            }
            case 2 -> {
                x = baseX + radius - d0 - 1;
                y = baseY + radius;
            }
            case 3 -> {
                x = baseX - radius;
                y = baseY - radius + d0;
            }
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int halfWidth = widthPixels + 0x0B;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - yRadius;
        int bottom = y + yRadius + 1;

        ctx.drawLine(left, top, right, top, 0.6f, 0.8f, 0.2f);
        ctx.drawLine(right, top, right, bottom, 0.6f, 0.8f, 0.2f);
        ctx.drawLine(right, bottom, left, bottom, 0.6f, 0.8f, 0.2f);
        ctx.drawLine(left, bottom, left, top, 0.6f, 0.8f, 0.2f);

        // Draw center cross
        ctx.drawLine(x - 4, y, x + 4, y, 0.6f, 0.8f, 0.2f);
        ctx.drawLine(x, y - 4, x, y + 4, 0.6f, 0.8f, 0.2f);
    }

}
