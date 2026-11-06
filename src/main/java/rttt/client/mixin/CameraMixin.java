package rttt.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rttt.client.CameraHook;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow private boolean detached;

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void rttt$sideCamera(float partialTicks, CallbackInfo ci) {
        if (!CameraHook.active()) return;
        this.detached = false;
        this.setRotation(180f, CameraHook.pitch());
        this.setPosition(CameraHook.x(partialTicks), CameraHook.y(partialTicks), CameraHook.z(partialTicks));
    }

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    private void rttt$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (CameraHook.active()) cir.setReturnValue(CameraHook.fov());
    }
}
