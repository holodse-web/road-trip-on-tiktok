package rttt.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rttt.RTTT;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow @Final ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void rttt$skipRoadSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (level.dimension().equals(RTTT.ROAD_LEVEL)) cir.setReturnValue(false);
    }
}
