package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import java.util.List;
import slaytherobotnik.core.EventOption;
import slaytherobotnik.run.EventRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/** A "?" event: illustration, text and options. */
final class EventView implements RunScreen.RoomView {
    private final EventRoom room;
    private final Hotspots spots = new Hotspots();
    private int bannerTicks;
    /** The slot machine's reels, for that event; null elsewhere or without the ROM's reel art. */
    private SlotReels reels;
    private boolean pulled;

    EventView(EventRoom room) {
        this.room = room;
    }

    private void layout(Shell shell) {
        spots.clear();
        int y = 150;
        for (int i = 0; i < room.options().size(); i++) {
            EventOption o = room.options().get(i);
            spots.add("o" + i, 150, y, shell.width() - 160, 15, o.enabled());
            y += 17;
        }
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        if (room.banner() != null) {
            bannerTicks = 120;
            shell.sfx(Sounds.SFX_SUPER_EMERALD);
        }
        if (bannerTicks > 0) {
            bannerTicks--;
        }
        if (playIllustration(shell)) {
            return;
        }
        layout(shell);
        String picked = spots.update(shell.in);
        if (picked != null) {
            shell.sfx(Sounds.SFX_STARPOST);
            room.choose(Integer.parseInt(picked.substring(1)));
        }
    }

    /**
     * Plays what the event asked its picture to show ({@code EventRoom.illustrate}): the slot
     * machine's reels spin to the rolled face, clattering as the stage does, and the event
     * carries on when they stop. Returns true while it waits (no input meanwhile). Pictures
     * that show nothing carry on at once.
     */
    private boolean playIllustration(Shell shell) {
        if (reels == null && room.def().art().equals("event:slot_machine") && shell.art.slotStrips() != null) {
            reels = new SlotReels(shell.art.slotStrips(), (int) shell.ticks);
        }
        if (reels == null) {
            room.illustrationShown();
            return false;
        }
        if (room.awaitingIllustration() && !pulled) {
            reels.pull(Integer.parseInt(room.illustration()), (int) shell.ticks);
            pulled = true;
            shell.sfx(Sounds.SFX_SWITCH);
        }
        reels.tick();
        if (reels.running() && shell.ticks % 16 == 0) {
            shell.sfx(Sounds.SFX_SLOT_MACHINE);
        }
        if (pulled && !reels.running()) {
            pulled = false;
            // Robotnik's spike balls and the Bar's dud sting; anything else pays out.
            String face = room.illustration();
            shell.sfx(face.equals("4") || face.equals("6") ? Sounds.SFX_SPIKES : Sounds.SFX_RING);
            room.illustrationShown();
        }
        return room.awaitingIllustration();
    }

    private String lastBanner;

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        LevelStages.draw(shell, c, 160);
        c.fill(0, 0, w, shell.height(), 0x70000010);
        SmallFont f = shell.font;
        String title = room.def().title().toUpperCase();
        f.drawOutlined(c, title, 150, 36, Colors.GOLD, 2);
        // Illustration panel.
        Gfx.panel(c, 10, 34, 130, 150);
        EventArt.draw(shell, c, room.def().art(), 12, 36, 126, 146, reels);
        Gfx.panel(c, 146, 52, w - 152, 94);
        List<String> lines = f.wrap(room.text(), w - 166);
        int y = 58;
        for (String line : lines) {
            f.drawMarkup(c, line, 152, y, Colors.TEXT);
            y += SmallFont.LINE;
        }
        layout(shell);
        for (int i = 0; i < room.options().size(); i++) {
            EventOption o = room.options().get(i);
            Hotspots.Spot s = spots.spot("o" + i);
            Gfx.button(c, f, "", s.x(), s.y(), s.w(), s.h(), spots.isFocused(s.id()), o.enabled(), shell.ticks);
            f.drawShadowed(c, o.label().toUpperCase(), s.x() + 5, s.y() + 5, o.enabled() ? Colors.GOLD : Colors.TEXT_DIM);
            f.drawMarkup(c, o.detail(), s.x() + 9 + f.width(o.label()), s.y() + 5,
                    o.enabled() ? Colors.TEXT : Colors.TEXT_DIM);
        }
        if (room.banner() != null) {
            lastBanner = room.banner();
            room.clearBanner();
        }
        if (bannerTicks > 0 && lastBanner != null) {
            String b = lastBanner.toUpperCase();
            f.drawOutlined(c, b, (w - f.width(b)) / 2, 138, Colors.GOLD, 1);
        }
    }
}
