package yofred.dev.justcompatible.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yofred.dev.justcompatible.compat.vault.VaultTimerMigration;

@Mixin(targets = "net.minecraft.world.level.block.entity.vault.VaultBlockEntity$Server")
abstract class VaultServerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void justcompatible$observeTimer(ServerLevel level, BlockPos pos, BlockState state,
            VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, CallbackInfo ci) {
        VaultTimerMigration.observe(level, pos, serverData);
    }
}
