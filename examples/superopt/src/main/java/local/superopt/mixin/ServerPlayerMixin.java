package local.superopt.mixin;

import local.superopt.ViewCapConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "requestedViewDistance", at = @At("RETURN"), cancellable = true)
    private void superopt$clampRequestedViewDistance(CallbackInfoReturnable<Integer> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        int cap = ViewCapConfig.getCap(self.getUUID());
        if (cap > 0 && cir.getReturnValueI() > cap) {
            cir.setReturnValue(cap);
        }
    }
}
