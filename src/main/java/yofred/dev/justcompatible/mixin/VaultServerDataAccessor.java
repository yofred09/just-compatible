package yofred.dev.justcompatible.mixin;

import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VaultServerData.class)
public interface VaultServerDataAccessor {
    @Accessor("stateUpdatingResumesAt")
    long justcompatible$getStateUpdatingResumesAt();

    @Invoker("pauseStateUpdatingUntil")
    void justcompatible$pauseStateUpdatingUntil(long value);
}
