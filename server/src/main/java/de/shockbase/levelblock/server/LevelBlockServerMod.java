package de.shockbase.levelblock.server;

import de.shockbase.levelblock.server.command.LevelBlockCommands;
import de.shockbase.levelblock.server.network.ServerNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public final class LevelBlockServerMod implements ModInitializer {

    public static final String MOD_ID = "levelblock-server";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicReference<LevelBlockRuntime> runtime = new AtomicReference<>();
    private final ServerNetwork network = new ServerNetwork();

    @Override
    public void onInitialize() {
        network.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                LevelBlockCommands.register(dispatcher, runtime::get)
        );
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            runtime.set(new LevelBlockRuntime(server, network, LOGGER));
            LOGGER.info("LevelBlock Server gestartet.");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LevelBlockRuntime active = runtime.get();
            if (active != null) {
                active.tick(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LevelBlockRuntime active = runtime.getAndSet(null);
            if (active != null) {
                active.close();
            }
        });
    }
}
