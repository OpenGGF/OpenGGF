package com.openggf.mods.code;

import com.openggf.mods.ModManifest;

import java.io.IOException;

/** The normal owner transaction registers declared campaign data without loading creator classes. */
public final class ModCampaignRegistration {
    private ModCampaignRegistration() { }

    public static void register(ModManifest manifest, ModContext context) throws IOException {
        if (manifest.campaign() == null) return;
        var campaign = ModCampaignDefinition.read(context.modAssets(), manifest.campaign(), manifest.baseGame());
        var definitions = campaign.validateLevels(context.modAssets());
        var scenes = new java.util.LinkedHashSet<String>();
        for (var definition : definitions) for (var object : definition.objects()) {
            if (object.authoredProperties() instanceof com.openggf.level.objects.authored.AuthoredObjectProperties.StageplayTrigger trigger) {
                if (!trigger.blocking()) throw new IOException("Authored Stageplay requires blocking playback: " + trigger.resource());
                scenes.add(trigger.resource());
            }
        }
        AuthoredStageplayServices.register(context, java.util.List.copyOf(scenes));
        context.registerZone(campaign.contribution(manifest.insertAfter()));
    }
}
