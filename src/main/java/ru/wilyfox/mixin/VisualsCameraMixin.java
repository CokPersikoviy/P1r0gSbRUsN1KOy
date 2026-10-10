package ru.wilyfox.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import ru.wilyfox.client.visuals.Visuals;

@Mixin(Camera.class)
public abstract class VisualsCameraMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"))
    private void froghelper$aspect(Camera camera, float near, float far, float fov, float width, float height, Operation<Void> original) {
        original.call(camera, near, far, fov, Visuals.width(width, height), height);
    }

    @WrapOperation(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;"))
    private Matrix4f froghelper$cullingAspect(Matrix4f matrix, float fov, float aspect, float near, float far, boolean zeroToOne, Operation<Matrix4f> original) {
        return original.call(matrix, fov, Visuals.aspect(aspect), near, far, zeroToOne);
    }
   @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 0))
   private void froghelper$freelookCart(Camera camera, float yRot, float xRot, Operation<Void> original) {
      if (ru.wilyfox.client.visuals.VisualsCamera.active()) {
         original.call(camera, ru.wilyfox.client.visuals.VisualsCamera.yaw(), ru.wilyfox.client.visuals.VisualsCamera.pitch());
      } else {
         original.call(camera, yRot, xRot);
      }
   }

   @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1))
   private void froghelper$freelook(Camera camera, float yRot, float xRot, Operation<Void> original) {
      if (ru.wilyfox.client.visuals.VisualsCamera.active()) {
         original.call(camera, ru.wilyfox.client.visuals.VisualsCamera.yaw(), ru.wilyfox.client.visuals.VisualsCamera.pitch());
      } else {
         original.call(camera, yRot, xRot);
      }
   }

   @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
   private float froghelper$distance(Camera camera, float distance, Operation<Float> original) {
      return (Float)original.call(camera, ru.wilyfox.client.visuals.VisualsCamera.zoom(distance));
   }

   @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
   private float froghelper$fovCap(float fov) {
      return Math.min(fov, 175.0F);
   }

   @ModifyReturnValue(method = "calculateHudFov", at = @At("RETURN"))
   private float froghelper$handFov(float fov) {
      return ru.wilyfox.client.visuals.VisualsHand.fov(fov);
   }

}
