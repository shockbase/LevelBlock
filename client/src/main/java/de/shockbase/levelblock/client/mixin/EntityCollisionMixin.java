package de.shockbase.levelblock.client.mixin;

import de.shockbase.levelblock.client.boundary.ClientBoundaryState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Inject(
            method = "collectCollidersIgnoringWorldBorder(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void levelblock$appendBoundaryCollision(
            @Nullable Entity source,
            Level level,
            List<VoxelShape> entityColliders,
            AABB boundingBox,
            CallbackInfoReturnable<List<VoxelShape>> callback
    ) {
        if (source == null) {
            return;
        }
        List<VoxelShape> boundary = ClientBoundaryState.instance().collisionShapes(source, level, boundingBox);
        if (boundary.isEmpty()) {
            return;
        }
        List<VoxelShape> combined = new ArrayList<>(callback.getReturnValue());
        combined.addAll(boundary);
        callback.setReturnValue(combined);
    }
}
