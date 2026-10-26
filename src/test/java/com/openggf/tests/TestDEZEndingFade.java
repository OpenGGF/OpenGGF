package com.openggf.tests;

import com.openggf.game.GameRng;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Short independent ObjC7_SetupEnding boundaries; these seed defeat ingress, not a cold boss route. */
class TestDEZEndingFade {
    private final Palette[] palettes = {new Palette(), new Palette(), new Palette(), new Palette()};
    private final PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
    private final List<Integer> sounds = new ArrayList<>();
    private final Level level = mock(Level.class);
    private final AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
    private final ObjectManager objects = mock(ObjectManager.class);
    private final GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
    private int creditsRequests;
    private int musicFades;
    private final ObjectServices services = new StubObjectServices() {
        @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
            return new com.openggf.level.objects.ObjectPlayerQuery(() -> player, List::of);
        }
        @Override public Level currentLevel() { return level; }
        @Override public ObjectManager objectManager() { return objects; }
        @Override public PaletteOwnershipRegistry paletteOwnershipRegistryOrNull() { return registry; }
        @Override public GameRng rng() { return rng; }
        @Override public void playSfx(int id) { sounds.add(id); }
        @Override public void fadeOutMusic() { musicFades++; }
        @Override public void requestCreditsTransition() { creditsRequests++; }
    };

    private Sonic2DeathEggRobotInstance boss() throws Exception {
        when(level.getPaletteCount()).thenReturn(4);
        for (int line = 0; line < 4; line++) when(level.getPalette(line)).thenReturn(palettes[line]);
        when(player.getCentreX()).thenReturn((short) 0xEC0);
        when(player.getCentreY()).thenReturn((short) 0x14C);
        var boss = ObjectConstructionContext.construct(services, () -> new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(0x800, 0x14C, 0xC7, 0, 0, false, 0)));
        boss.setServices(services);
        boss.getState().defeated = true;
        set(boss, "bodyRoutine", 0xE);
        set(boss, "defeatPhase", 6);
        set(boss, "robotFollowOffset", 0x20);
        clearInvocations(objects);
        return boss;
    }

    @Test
    void countdownChangesEveryNormalColorAndHandsOffOnPass22ThroughSoundMailbox() throws Exception {
        var boss = boss();
        for (int line = 0; line < 4; line++) {
            for (int color = 0; color < 16; color++) palettes[line].getColor(color).fromSegaFormat(color == 15 ? 0xEEE : 0);
        }
        pass(boss, 29); // loc_3D9BE arms timer; no fade or music call on threshold pass.
        assertEquals(0, word(0, 0));
        assertEquals(22, get(boss, "fadeTimer"));
        assertEquals(0, musicFades);
        for (int step = 1; step <= 21; step++) {
            pass(boss, 29 + step);
            int expected = step <= 7 ? 2 * step : step <= 14 ? 0xE + ((step - 7) << 5)
                    : 0xEE + ((step - 14) << 9);
            for (int line = 0; line < 4; line++) {
                assertEquals(expected, word(line, 0), "native red/green/blue order, pass " + step);
                assertEquals(0xEEE, word(line, 15), "white remains saturated");
                assertEquals("s2.dez.endingFade", registry.ownerAt(PaletteSurface.NORMAL, line, 0));
            }
            assertEquals(0, creditsRequests);
            assertEquals(0, musicFades);
            assertFalse(sounds.contains(0xF9));
            assertFalse(boss.isDestroyed());
        }
        // Native final fill is unconditional, even if another owner changed a colour.
        palettes[3].getColor(9).fromSegaFormat(0);
        pass(boss, 51);
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) assertEquals(0xEEE, word(line, color));
        assertEquals(1, creditsRequests);
        assertEquals(0, musicFades, "FixBugs=0 must not bypass the native SFX mailbox");
        assertEquals(0xF9, sounds.getLast());
        assertTrue(boss.isDestroyed());
    }

    @Test
    void fadeKeepsFollowingAndUsesVintPhaseAfterRestore() throws Exception {
        var boss = boss();
        pass(boss, 30);
        assertEquals(0x20, get(boss, "robotFollowOffset"));
        pass(boss, 31);
        assertTrue(sounds.isEmpty(), "entry-relative phase must not create a rumble");
        var identities = new com.openggf.game.rewind.identity.RewindIdentityTable();
        identities.registerPlayer(player, com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
        identities.registerObject(boss, com.openggf.game.rewind.identity.ObjectRefId.dynamic(0, 0, 0));
        int childId = 1;
        for (var child : boss.getChildComponents()) {
            identities.registerObject((com.openggf.level.objects.ObjectInstance) child,
                    com.openggf.game.rewind.identity.ObjectRefId.dynamic(childId, 0, childId));
            childId++;
        }
        var context = com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(identities);
        var snapshot = boss.captureRewindState(context);
        int[][] paletteBefore = paletteWords();
        pass(boss, 32);
        assertEquals(List.of(0xE1), sounds);
        assertEquals(0x1F, get(boss, "robotFollowOffset"));
        assertEquals(0xEC0 - 0x1F, boss.getState().x);
        assertEquals(0x14C, boss.getState().y);
        var after = boss.captureRewindState(context);
        int[][] paletteAfter = paletteWords();
        boss.restoreRewindState(snapshot, context);
        restorePalettes(paletteBefore);
        sounds.clear();
        pass(boss, 32);
        assertEquals(after, boss.captureRewindState(context));
        assertArrayEquals(paletteAfter, paletteWords());
        assertEquals(List.of(0xE1), sounds);
        verify(objects, times(4)).createDynamicObjectWithReservedSlot(any());
    }

    @Test
    void endingExplosionUsesNewFollowPositionAndNativeMinus30Offsets() throws Exception {
        var boss = boss();
        var created = new ArrayList<BossExplosionObjectInstance>();
        doAnswer(invocation -> {
            var explosion = (BossExplosionObjectInstance) ((java.util.function.Supplier<?>) invocation.getArgument(0)).get();
            created.add(explosion);
            return explosion;
        }).when(objects).createDynamicObjectWithReservedSlot(any());
        var expectedRng = new GameRng(GameRng.Flavour.S1_S2);
        int random = expectedRng.nextWord();
        pass(boss, 32);
        assertEquals(expectedRng.capture(), rng.capture());
        assertEquals(0xEC0 - 0x1F, boss.getState().x);
        assertEquals(0xEC0 - 0x1F + ((random & 255) >> 2) - 0x30, created.getFirst().getX());
        assertEquals(0x14C + (((random >>> 8) & 255) >> 2) - 0x30, created.getFirst().getY());
        random = expectedRng.nextWord();
        when(player.getCentreX()).thenReturn((short) 0xEC5);
        pass(boss, 33);
        assertEquals(0xEC5 - 0x1F + ((random & 255) >> 2) - 0x30, created.getLast().getX());
        assertEquals(0x14C + (((random >>> 8) & 255) >> 2) - 0x30, created.getLast().getY());
        assertEquals(expectedRng.capture(), rng.capture());
    }

    @Test
    void fullNativeObjectRamDoesNotConsumeEndingExplosionRng() throws Exception {
        var camera = mock(com.openggf.camera.Camera.class);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices nativeServices = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public GameRng rng() { return rng; }
            @Override public com.openggf.level.objects.ObjectPlayerQuery playerQuery() {
                return new com.openggf.level.objects.ObjectPlayerQuery(() -> player, List::of);
            }
        };
        var manager = new ObjectManager(List.of(), new com.openggf.game.sonic2.objects.Sonic2ObjectRegistry(),
                0, null, null, null, camera, nativeServices);
        holder[0] = manager;
        when(player.getCentreX()).thenReturn((short) 0xE80);
        when(player.getCentreY()).thenReturn((short) 0x14C);
        var nativeBoss = manager.createDynamicObjectWithReservedSlot(() -> new Sonic2DeathEggRobotInstance(
                new ObjectSpawn(0xE60, 0x14C, 0xC7, 0, 0, false, 0)));
        assertNotNull(nativeBoss);
        nativeBoss.getState().defeated = true;
        set(nativeBoss, "bodyRoutine", 0xE);
        set(nativeBoss, "defeatPhase", 6);
        set(nativeBoss, "robotFollowOffset", 0x20);
        var fillers = new ArrayList<BossExplosionObjectInstance>();
        for (int n = 0; n <= manager.getObjectSlotCapacity(); n++) {
            var filler = manager.createDynamicObjectWithReservedSlot(() -> new BossExplosionObjectInstance(0, 0, 0xC4));
            if (filler == null) break;
            fillers.add(filler);
        }
        assertEquals(manager.getObjectSlotCapacity(), manager.getActiveObjectSlotCount());
        var before = rng.capture();
        nativeBoss.update(31, player);
        assertEquals(before, rng.capture(), "failed AllocateObject must precede RandomNumber");
        manager.removeDynamicObject(fillers.getFirst());
        assertEquals(manager.getObjectSlotCapacity() - 1, manager.getActiveObjectSlotCount());
        nativeBoss.update(32, player);
        var expected = new GameRng(GameRng.Flavour.S1_S2);
        expected.restore(before);
        expected.nextWord();
        assertEquals(expected.capture(), rng.capture(), "the available-slot control consumes exactly one native random word");
        assertEquals(manager.getObjectSlotCapacity(), manager.getActiveObjectSlotCount());
    }

    private void pass(Sonic2DeathEggRobotInstance boss, int vint) {
        registry.beginFrame();
        boss.update(vint, player);
        registry.resolveInto(palettes, null, null, null);
    }
    private int word(int line, int color) { return PaletteWriteSupport.segaWordFromColor(palettes[line].getColor(color)); }
    private int[][] paletteWords() {
        int[][] words = new int[4][16];
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) words[line][color] = word(line, color);
        return words;
    }
    private void restorePalettes(int[][] words) {
        for (int line = 0; line < 4; line++) for (int color = 0; color < 16; color++) palettes[line].getColor(color).fromSegaFormat(words[line][color]);
    }
    private static void set(Object target, String name, int value) throws Exception { field(name).setInt(target, value); }
    private static int get(Object target, String name) throws Exception { return field(name).getInt(target); }
    private static Field field(String name) throws Exception {
        Field f = Sonic2DeathEggRobotInstance.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
}
