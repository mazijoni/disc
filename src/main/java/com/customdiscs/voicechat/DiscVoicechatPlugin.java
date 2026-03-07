package com.customdiscs.voicechat;

import com.customdiscs.DiscMod;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;

/**
 * Entry point for the Simple Voice Chat API integration.
 * @ForgeVoicechatPlugin causes SVC to auto-discover this class via ServiceLoader.
 */
@ForgeVoicechatPlugin
public class DiscVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return DiscMod.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        DiscMod.LOGGER.info("[CustomDiscs] Simple Voice Chat API initialised (v{})", api.getClass().getPackage().getImplementationVersion());
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onServerStopped);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        JukeboxAudioManager.setApi(event.getVoicechat());
        DiscMod.LOGGER.info("[CustomDiscs] SVC server API ready — proximity disc audio enabled");
    }

    private void onServerStopped(VoicechatServerStoppedEvent event) {
        JukeboxAudioManager.clearAll();
        JukeboxAudioManager.setApi(null);
    }
}
