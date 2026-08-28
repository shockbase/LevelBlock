package de.shockbase.levelblock.client;

import de.shockbase.levelblock.client.boundary.ClientBoundaryState;
import de.shockbase.levelblock.client.boundary.ClientBoundaryRenderer;
import de.shockbase.levelblock.network.BoundarySyncPayload;
import de.shockbase.levelblock.network.HelloPayload;
import de.shockbase.levelblock.network.LobbySyncPayload;
import de.shockbase.levelblock.network.NetworkProtocol;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public final class LevelBlockClientMod implements ClientModInitializer {

    private static final int MAX_BOUNDARY_PACKET_BYTES = 8 * 1024 * 1024;
    private static final ClientBoundaryRenderer RENDERER =
            new ClientBoundaryRenderer(ClientBoundaryState.instance());

    public static void terrainChanged(ClientLevel level, BlockPos pos) {
        RENDERER.terrainChanged(level, pos);
    }

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(
                BoundarySyncPayload.TYPE,
                BoundarySyncPayload.CODEC,
                MAX_BOUNDARY_PACKET_BYTES
        );
        PayloadTypeRegistry.clientboundPlay().register(LobbySyncPayload.TYPE, LobbySyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(BoundarySyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientBoundaryState.instance().apply(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(LobbySyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientBoundaryState.instance().apply(payload))
        );
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                sender.sendPacket(new HelloPayload(NetworkProtocol.VERSION));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                {
                    RENDERER.clear();
                    ClientBoundaryState.instance().clear();
                }
        );
        ClientTickEvents.END_CLIENT_TICK.register(RENDERER::tick);
    }
}
