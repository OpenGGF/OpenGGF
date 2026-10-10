package starpost.fishing;
import com.openggf.mods.scene.*;
import starpost.art.Art;
import starpost.core.Game;
import starpost.scene.Shell;
/** ROM water cycling, pool bed/shadows and scheduled Barnaby; native objects own movement/contact. */
public final class LakeView {
 private final Art source;
 private final FishArt fishArt;
 private final int surface;
 private final SceneImage[] fall=new SceneImage[4],pool=new SceneImage[4];
 private int cycledSeason=-1,poolBed;
 public LakeView(Art source) { this.source=source; fishArt=new FishArt(source); surface=LakeOverlay.poolY(source)-128; }
 public void draw(Shell shell,SceneCanvas canvas,int cx,int cy,long ticks,java.util.function.IntUnaryOperator floor) {
  cycle(shell.game.calendar.season()); int step=(int)(ticks/6%4); var tint=SceneDraw.plain();
  canvas.drawRegion(fall[step],0,0,256,surface,512-cx,128-cy,256,surface,tint);
  canvas.fill(256-cx,surface+128-cy,512,256-surface,poolBed);
  canvas.draw(pool[step],256-cx,surface+128-cy,tint);
  drawShadows(canvas,cx,cy,ticks);
  canvas.fill(256-cx,surface+128-cy,512,1,0x60FFFFFF);
  drawBarnaby(shell,canvas,cx,cy,tint,ticks,floor);
 }
    private void cycle(int season) {
        if (season == cycledSeason) {
            return;
        }
        cycledSeason = season;
        Art art = source;
        Art.Seasonal look = art.season(season);
        int[] base = new int[4];
        System.arraycopy(art.ghzPalette, 32 + 8, base, 0, 4);
        int[] steps=source.s1.palette(0x1B7E,16);
        poolBed = look.tone.apply(base[0] | 0xFF000000);
        SceneImage waterfall = art.kit.blockImage(52);
        SceneImage bridge = art.kit.blockImage(51).crop(0, surface, Art.BLOCK, Art.BLOCK - surface);
        for (int s = 0; s < 4; s++) {
            fall[s] = look.tone.apply(recolour(waterfall, base, steps, s));
            pool[s] = look.tone.apply(twice(recolour(bridge, base, steps, s)));
        }
    }

    /** A colour as the light's tint would draw it (fills are not tinted). */
    private static int multiply(int argb, int tint) {
        int r = (argb >>> 16 & 255) * (tint >>> 16 & 255) / 255;
        int g = (argb >>> 8 & 255) * (tint >>> 8 & 255) / 255;
        int b = (argb & 255) * (tint & 255) / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The pool's rows twice over: one picture across the jetty's block and the waterfall's. */
    private static SceneImage twice(SceneImage image) {
        int w = image.width(), h = image.height();
        int[] px = new int[w * 2 * h];
        int[] src = image.pixels();
        for (int y = 0; y < h; y++) {
            System.arraycopy(src, y * w, px, y * w * 2, w);
            System.arraycopy(src, y * w, px, y * w * 2 + w, w);
        }
        return new SceneImage(w * 2, h, px);
    }

    private static SceneImage recolour(SceneImage image, int[] base, int[] steps, int step) {
        int[] px = image.pixels();
        for (int i = 0; i < px.length; i++) {
            for (int k = 0; k < 4; k++) {
                if (px[i] >>> 24 != 0 && (px[i] & 0xFFFFFF) == (base[k] & 0xFFFFFF)) {
                    px[i] = steps[step * 4 + k] | 0xFF000000;
                    break;
                }
            }
        }
        return new SceneImage(image.width(), image.height(), px);
    }

    private void drawShadows(SceneCanvas canvas,int cx,int cy,long ticks) {
        SceneDraw shade = SceneDraw.plain().withFlash(0xFF002448).withAlpha(0.35f);
        String[] kinds = {"bubble_bass", "loop_pike", "ring_carp", "checker_perch"};
        for (int i = 0; i < 4; i++) {
            SceneImage fish = fishArt.picture(kinds[i]);
            if (fish == null) {
                continue;
            }
            double t = (ticks + i * 400) / (220.0 + i * 37);
            float x = 256 + 60 + i * 110 + (float) Math.sin(t) * 50;
            float y = surface+128 + 22 + i * 9 + (float) Math.sin(t * 2.3) * 4;
            boolean left = Math.cos(t) > 0;
            canvas.draw(fish, x - cx, y - cy, shade.withFlipX(!left));
        }
    }

    /** Barnaby the Rocky on the end of his jetty when his day puts him there, line in the water. */
    private void drawBarnaby(Shell shell,SceneCanvas canvas,int cx,int cy,SceneDraw tint,long ticks,java.util.function.IntUnaryOperator floor) {
        if (!barnabyHere(shell.game)) {
            return;
        }
        SceneSpriteSet seal = shell.art.animal("rocky");
        if (seal == null || seal.frameCount() < 3) {
            return;
        }
        float feet = floor.applyAsInt(482);
        long cycle = ticks % 1500;
        boolean landing = cycle > 1400;
        SceneSprite body = seal.frame(landing ? 1 : 0);
        float hop = landing ? -Math.abs((float) Math.sin((cycle - 1400) / 8.0)) * 6 : 0;
        canvas.draw(body, 482 - cx, feet - cy + hop - (body.height() - body.originY()), tint.withFlipX(true));
        float tipX = 482 + 16, tipY = feet - 22;
        PondLine.drawLine(canvas, 482 + 4 - cx, feet - 8 - cy, tipX - cx, tipY - cy, 0xFF924900, 0);
        float bobX = tipX + 40, bobY = surface+128 + (float) Math.sin(ticks / 15.0) * 0.8f;
        if (landing) {
            SceneImage fish = fishArt.picture("bubble_bass");
            if (fish != null) {
                canvas.draw(fish, tipX - cx - 4, tipY - cy - 6 + hop, tint);
            }
        } else {
            PondLine.drawLine(canvas, tipX - cx, tipY - cy, bobX - cx, bobY - cy, 0xC0FFFFFF, 8);
            canvas.fill(Math.round(bobX - cx) - 1, Math.round(bobY - cy) - 3, 3, 3, 0xFFDB2400);
        }
    }

    /** Whether Barnaby's schedule has him on the jetty now (the People system's own plan). */
    private static boolean barnabyHere(Game game) {
        starpost.people.People people = game.section(starpost.people.People.class);
        if (people == null) {
            return false;
        }
        starpost.people.VillagerDef barnaby = people.cast.get("barnaby");
        if (barnaby == null || !people.present(barnaby, game)) {
            return false;
        }
        starpost.people.Spot spot = barnaby.schedule().resolve(game.calendar.season(), game.calendar.weekday(),
                game.raining, game.calendar.minutes(), game.flags);
        return spot != null && !spot.inside() && spot.anchor().equals("jetty");
    }

}
