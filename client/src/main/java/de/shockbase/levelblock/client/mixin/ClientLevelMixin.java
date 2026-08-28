package de.shockbase.levelblock.client.mixin;

import de.shockbase.levelblock.client.LevelBlockClientMod;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(
            method = "setBlocksDirty(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("TAIL")
    )
    private void levelblock$terrainChanged(
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            CallbackInfo callback
    ) {
        LevelBlockClientMod.terrainChanged((ClientLevel) (Object) this, pos);
    }
}
