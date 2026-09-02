package de.shockbase.levelblock.client.mixin;

import de.shockbase.levelblock.client.LevelBlockClientMod;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public abstract class WorldBorderRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;Lnet/minecraft/world/phys/Vec3;DD)V",
            at = @At("HEAD")
    )
    private void levelblock$renderBoundary(
            WorldBorderRenderState state,
            Vec3 cameraPos,
            double renderDistance,
            double depthFar,
            CallbackInfo callback
    ) {
        LevelBlockClientMod.renderBoundary(cameraPos);
    }
}
