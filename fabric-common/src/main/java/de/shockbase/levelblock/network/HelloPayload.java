package de.shockbase.levelblock.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HelloPayload(int protocolVersion) implements CustomPacketPayload {

    public static final Type<HelloPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(NetworkProtocol.MOD_NAMESPACE, "hello")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            HelloPayload::protocolVersion,
            HelloPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
