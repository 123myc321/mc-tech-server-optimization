package local.superopt.mixin;

import local.superopt.AsyncPlayerSave;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "stopServer", at = @At("RETURN"))
    private void superopt$drainPlayerSaves(CallbackInfo callbackInfo) {
        AsyncPlayerSave.drain();
    }
}
