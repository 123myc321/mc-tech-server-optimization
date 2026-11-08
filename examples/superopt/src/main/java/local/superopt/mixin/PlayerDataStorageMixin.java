package local.superopt.mixin;

import local.superopt.AsyncPlayerSave;
import local.superopt.SuperOpt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
    @Redirect(
            method = "save(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V"
            )
    )
    private void superopt$captureCompressedWrite(CompoundTag tag, Path tempFile) {
        AsyncPlayerSave.capture(tag, tempFile);
    }

    @Redirect(
            method = "save(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V"
            )
    )
    private void superopt$asyncReplace(Path finalFile, Path tempFile, Path backupFile) {
        AsyncPlayerSave.submit(finalFile, tempFile, backupFile);
    }

    @Inject(method = "save(Lnet/minecraft/world/entity/player/Player;)V", at = @At("RETURN"))
    private void superopt$dropAbandonedWrite(Player player, CallbackInfo callbackInfo) {
        if (AsyncPlayerSave.hasPending()) {
            AsyncPlayerSave.discard();
            SuperOpt.LOGGER.warn("Dropped an abandoned player-data write for {}",
                    player.getPlainTextName());
        }
    }
}
