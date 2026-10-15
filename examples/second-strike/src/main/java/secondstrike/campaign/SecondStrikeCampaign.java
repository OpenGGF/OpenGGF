package secondstrike.campaign;

import com.openggf.game.sonic3k.difficulty.BlueSphereLayoutPolicy;
import com.openggf.mods.code.ModContext;

import java.util.Objects;

/** Campaign-owned providers; native stage rewards and progression retain their owners. */
public final class SecondStrikeCampaign {
    private SecondStrikeCampaign() { }

    public static void register(ModContext context) {
        Objects.requireNonNull(context, "context");
        HydrocityCampaign.register(context);
        MarbleGardenCampaign.register(context);
        CarnivalNightCampaign.register(context);
        IceCapCampaign.register(context);
        LaunchBaseCampaign.register(context);
        context.registerService("campaign-boards", BlueSphereLayoutPolicy.class, new CampaignBlueSphereBoards());
    }
}
