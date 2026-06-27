package com.sulfurphysics.mixin;

import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCube.class)
public class SulfurCubeAiMixin {
    @Inject(at = @At("HEAD"), method = "customServerAiStep", cancellable = true)
    private void onAiStep(CallbackInfo ci) {
        SulfurCube self = (SulfurCube)(Object)this;
        if (self.hasBodyItem()) {
            ci.cancel();
        }
    }
}
