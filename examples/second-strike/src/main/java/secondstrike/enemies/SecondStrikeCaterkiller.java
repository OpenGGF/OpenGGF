package secondstrike.enemies;

import com.openggf.mods.code.ModContext;
import com.openggf.mods.objects.CaterkillerJrFactory;
import com.openggf.mods.objects.CaterkillerJrRecipe;

/** Owned longer Caterkiller recipe; native code owns movement, contacts and its body graph. */
public final class SecondStrikeCaterkiller {
    private SecondStrikeCaterkiller() { }

    public static void register(ModContext context) {
        context.registerObject("caterkiller", (spawn, registry) ->
                CaterkillerJrFactory.create(spawn, CaterkillerJrRecipe.DOUBLE_LENGTH));
    }
}
