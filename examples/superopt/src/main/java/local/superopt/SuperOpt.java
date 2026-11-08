package local.superopt;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SuperOpt implements ModInitializer {
    public static final String MOD_ID = "superopt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ViewCapConfig.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ViewCapCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            int queued = AsyncPlayerSave.queued();
            if (queued > 0) {
                LOGGER.info("Waiting for {} queued player saves", queued);
            }
            AsyncPlayerSave.drain();
        });
        LOGGER.info("SuperOpt initialized (default view cap: {}, async player save: {})",
                ViewCapConfig.getDefaultCap() == 0 ? "off" : ViewCapConfig.getDefaultCap(),
                ViewCapConfig.isAsyncPlayerSaveEnabled());
    }
}
