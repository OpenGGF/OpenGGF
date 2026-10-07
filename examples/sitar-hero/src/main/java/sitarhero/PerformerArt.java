package sitarhero;

import com.openggf.mods.scene.*;
import java.util.ArrayList;
import java.util.List;
import sitarhero.model.Role;

/** ROM-backed cosmetic actors; instrument props are new pixel art drawn by this mod. */
final class PerformerArt {
    private record Layer(SceneSpriteSet set, int frame, int x, int y) { }
    private final List<Layer> layers = new ArrayList<>();
    private SceneSpriteSet character;
    private SceneSpriteSet tails;
    private final String performer;
    private final String game;
    private final PerformerMotion motion = new PerformerMotion();
    private PerformerRig rig;

    PerformerArt(SceneRomArt rom, String performer) {
        this.performer = performer;
        game = rom.gameId();
        switch (performer) {
            case "sonic", "tails", "knuckles" -> {
                character = rom.character(performer);
                if (performer.equals("tails")) tails = rom.characterAccessory("tails");
            }
            case "robotnik" -> robotnik(rom);
            case "silver-sonic" -> {
                int[] palette = s2Palette(rom, 0x12); // PalPtr_DEZ, native Silver Sonic line 1
                layers.add(new Layer(rom.sprites(RomSpriteRequest.of(0x8BE12,
                        RomSpriteRequest.Compression.NEMESIS, 0x39E68, 1), palette), 5, 0, 0));
            }
            case "mecha-sonic" -> {
                int[] palette = s3Palette(rom, 0x0A973C);
                System.arraycopy(rom.palette(0x07D850, 16), 0, palette, 16, 16); // Pal_SSZGHZMisc
                layers.add(new Layer(rom.sprites(RomSpriteRequest.streamed(0x175A9E, 0x56E0,
                        0x1853AA, 0x185852, RomSpriteRequest.DplcLayout.OBJECT, 1), palette), 10, 0, 0));
            }
            case "egg-robo" -> {
                SceneSpriteSet set = rom.sprites(RomSpriteRequest.of(0x17B17E,
                        RomSpriteRequest.Compression.KOSINSKI_MODULED, 0x184F34, 0), s3Palette(rom, 0x0A973C));
                // Frame 1 includes the head, torso and glove; frame 2 is the gun.
                // The instrument replaces the gun, while native legs remain separate.
                layers.add(new Layer(set, 4, -0xC, 0x10));
                layers.add(new Layer(set, 1, 0, 0));
            }
            default -> throw new IllegalArgumentException("Unknown performer: " + performer);
        }
    }

    private void robotnik(SceneRomArt rom) {
        switch (rom.gameId()) {
            case "s1" -> layers.add(new Layer(rom.sprites(RomSpriteRequest.of(0x5E4CE,
                    RomSpriteRequest.Compression.NEMESIS, 0x01A1E4, 0), rom.characterPalette("sonic")), 0, 0, 0));
            case "s2" -> {
                int[] palette = s2Palette(rom, 0x0A); // PalPtr_WFZ + Pal_SonicTails
                // WFZ Robotnik composes three native banks at VRAM $500/$518/$564.
                int[] art = {0x8E886, 0x8EA5A, 0x8EE52};
                int[] offset = {0x500, 0x518, 0x564};
                for (int i = 0; i < art.length; i++) layers.add(new Layer(rom.sprites(
                        RomSpriteRequest.of(art[i], RomSpriteRequest.Compression.NEMESIS, 0x3D0EE, 0)
                                .withTileOffset(offset[i]), palette), 0, 0, 0));
            }
            default -> {
                SceneSpriteSet ship = rom.sprites(RomSpriteRequest.of(0x0D771E,
                        RomSpriteRequest.Compression.NEMESIS, 0x06820C, 0), s3Palette(rom, 0x0A8B7C));
                layers.add(new Layer(ship, 5, 0, 8));
                layers.add(new Layer(ship, 2, 0, -8));
            }
        }
    }

    private static int[] s3Palette(SceneRomArt rom, int zone) {
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x0A8A3C, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(zone, 48), 0, palette, 16, 48);
        return palette;
    }
    private static int[] s2Palette(SceneRomArt rom, int id) {
        byte[] pointer = rom.read(0x2782 + id * 8, 4);
        int address = (pointer[1] & 255) << 16 | (pointer[2] & 255) << 8 | pointer[3] & 255;
        int[] palette = new int[64];
        System.arraycopy(rom.palette(0x29E2, 16), 0, palette, 0, 16);
        System.arraycopy(rom.palette(address, 48), 0, palette, 16, 48);
        return palette;
    }

    /** Called only for a judged note-on, including HOPO, chord and direct-drum hits. */
    void notePlayed(Role role, int lanes, long ticks) { motion.notePlayed(role, lanes, ticks); }

    void draw(SceneCanvas c, int x, int y, long ticks, String instrument, boolean playing) {
        Role role = switch (instrument) {
            case "Bongos" -> Role.BONGOS;
            case "Synth" -> Role.SYNTH;
            case "Harp" -> Role.HARP;
            default -> Role.SITAR;
        };
        motion.beginDraw(ticks, playing);
        if (rig == null) {
            List<PerformerCutout.Positioned> pose = new ArrayList<>();
            if (character != null) {
                int[] frames = character.animationFrames(5);
                int frame = frames.length == 0 ? 0 : frames[0];
                pose.add(new PerformerCutout.Positioned(character.frame(frame), 0, 0));
            }
            for (Layer layer : layers) pose.add(new PerformerCutout.Positioned(layer.set().frame(layer.frame()), layer.x(), layer.y()));
            rig = PerformerRig.of(pose, performer, game);
        }
        int bob = ticks % 120 < 30 ? -1 : 0; // quiet breathing, never a fake note attack
        SceneDraw style = SceneDraw.plain();
        // Obj_Tails_Tail_AniSelection: native standing tail frames $22..$26
        // share the body's origin; mapping frame zero is a transparent placeholder.
        if (tails != null) c.draw(tails.frame(0x22 + (int) (ticks / 8 % 5)), x, y + bob, style);
        c.draw(rig.pixels.body(), x, y + bob, style);
        if (rig.foot) {
            var foot = rig.pixels.limbs().get(rig.armCount);
            int kick = role == Role.BONGOS ? Math.max(0, motion.stroke(role, 16, ticks)) : 0;
            c.draw(foot.turn(0), x + foot.x, y + bob + foot.y + kick / 2, style);
        }
        int contactY = y + bob + rig.contactY;
        instrument(c, x, contactY - 3, instrument);
        if (role == Role.SYNTH) for (int lane = 0; lane < 5; lane++) {
            if (motion.age(role, 1 << lane, ticks) < 4) c.fill(x - 21 + lane * 10, contactY + 4, 5, 3, 0xFF9DE6FF);
        }
        for (int hand = 0; hand < rig.armCount; hand++) {
            var limb = rig.pixels.limbs().get(hand);
            int lanes = role == Role.BONGOS && rig.armCount > 1 ? (hand == 0 ? 5 : 10)
                    : role == Role.BONGOS ? 15 : role == Role.SYNTH && rig.armCount > 1 ? (hand == 0 ? 7 : 28) : 31;
            int stroke = motion.stroke(role, lanes, ticks);
            int turn = hand == 0 ? -stroke : stroke;
            if (role == Role.BONGOS && rig.armCount == 1
                    && motion.age(role, 10, ticks) < motion.age(role, 5, ticks)) turn = stroke;
            // Raised native eggmobile sleeves articulate down towards the prop; ordinary
            // standing gloves and robot claws keep their native shoulder positions.
            int rest = performer.equals("robotnik") && game.equals("s3k") ? (hand == 0 ? -8 : 8) : 0;
            int dx = role == Role.HARP ? (hand == 0 ? 2 : 0) : 0;
            int dy = role == Role.SITAR || role == Role.HARP ? stroke / 2 : stroke;
            c.draw(limb.turn(rest + Integer.signum(turn)), x + limb.x + dx, y + bob + limb.y + dy, style);
        }
        accents(c, x, contactY, role, ticks);
    }

    private void accents(SceneCanvas c, int x, int y, Role role, long ticks) {
        if (role == Role.BONGOS) {
            ring(c, x - 13, y, motion.age(role, 5, ticks), 0xFFFFD090);
            ring(c, x + 13, y, motion.age(role, 10, ticks), 0xFFFFD090);
            int age = motion.age(role, 16, ticks);
            if (age < 7) c.fill(x - 4, y + 25 + (age < 4 ? 2 : 0), 8, 2, 0xFFFFE6A0);
            ring(c, x, y + 25, age, 0xFF93DFFF);
        } else {
            for (int lane = 0; lane < 5; lane++) {
                int age = motion.age(role, 1 << lane, ticks);
                int px = role == Role.SYNTH ? x - 21 + lane * 10 : role == Role.HARP ? x + 2 + lane * 4 : x + 5;
                ring(c, px, role == Role.HARP ? y - 5 : y + 2, age, 0xFFB8F2FF);
            }
        }
    }

    private static void ring(SceneCanvas c, int x, int y, int age, int color) {
        if (age >= PerformerMotion.RING_TICKS) return;
        int radius = 3 + age / 3;
        int argb = ((PerformerMotion.RING_TICKS - age) * 180 / PerformerMotion.RING_TICKS << 24) | (color & 0xFFFFFF);
        // Sparse pixel arcs leave the hands and instrument legible at the native scale.
        c.fill(x - radius, y - 3, 1, 3, argb); c.fill(x + radius, y - 3, 1, 3, argb);
        c.fill(x - radius + 1, y - 5, 2, 1, argb); c.fill(x + radius - 2, y - 5, 2, 1, argb);
    }

    private void instrument(SceneCanvas c, int x, int y, String role) {
        switch (role) {
            case "Bongos" -> {
                for (int i = 0; i < 2; i++) {
                    c.fill(x - 24 + i * 25, y + 4, 22, 17, 0xFF8D4424);
                    c.fill(x - 24 + i * 25, y + 3, 22, 5, 0xFFFFD090);
                    c.fill(x - 22 + i * 25, y + 9, 3, 10, 0xFFE8A04B);
                }
                c.fill(x - 5, y + 27, 10, 4, 0xFF555575);
            }
            case "Synth" -> {
                c.fill(x - 30, y + 3, 60, 15, 0xFF283850);
                for (int i = 0; i < 8; i++) {
                    c.fill(x - 27 + i * 7, y + 6, 6, 9, 0xFFFFF4DB);
                    if (i % 3 != 0) c.fill(x - 24 + i * 7, y + 5, 3, 6, 0xFF141C32);
                }
                c.fill(x - 25, y + 18, 3, 15, 0xFF8DA0BC); c.fill(x + 22, y + 18, 3, 15, 0xFF8DA0BC);
            }
            case "Harp" -> {
                c.fill(x + 28, y - 20, 5, 46, 0xFFE6A329); c.fill(x + 2, y + 23, 31, 5, 0xFFE6A329);
                for (int i = 0; i < 6; i++) {
                    int height = 14 + i * 5;
                    c.fill(x + 4 + i * 4, y + 22 - height, 1, height, 0xFFFFF0AA);
                    c.fill(x + 2 + i * 4, y + 18 - height, 5, 4, 0xFFE6A329);
                }
            }
            default -> {
                // Stepped gourd silhouette and long stringed neck remain new prop art;
                // every pixel of the actor's foreground hands still comes from its ROM.
                c.fill(x + 1, y - 1, 13, 20, 0xFF743E23); c.fill(x - 2, y + 2, 19, 14, 0xFF743E23);
                c.fill(x + 2, y + 1, 11, 16, 0xFFE8A447); c.fill(x, y + 4, 15, 10, 0xFFE8A447);
                for (int i = 0; i < 7; i++) c.fill(x - 5 - i * 3, y + 4 - i * 2, 5, 5, 0xFFFFD277);
                c.fill(x + 5, y + 6, 5, 5, 0xFF3B2823);
                for (int i = 0; i < 31; i++) c.fill(x - 22 + i, y - 5 + i / 2, 1, 1, 0xFFFFE8AF);
                c.fill(x + 1, y + 17, 14, 2, 0xFFFFDC86);
            }
        }
    }
}
