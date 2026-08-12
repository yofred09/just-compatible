package yofred.dev.justcompatible.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yofred.dev.justcompatible.JustCompatibleConfig;

@Pseudo
@Mixin(targets = "net.satisfy.vinery.core.util.WineYears", remap = false)
public abstract class VineryWineClockMixin {
    @Inject(method = "getDays", at = @At("HEAD"), cancellable = true, require = 0)
    private static void justcompatible$sharedServerClock(Level level, CallbackInfoReturnable<Integer> cir) {
        if (!JustCompatibleConfig.VINERY_SHARED_CLOCK.get() || !(level instanceof ServerLevel serverLevel)) return;
        long greatest = serverLevel.getGameTime();
        for (ServerLevel candidate : serverLevel.getServer().getAllLevels()) {
            greatest = Math.max(greatest, candidate.getGameTime());
        }
        cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, greatest / 24000L));
    }
}
