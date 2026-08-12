package yofred.dev.justcompatible.mixin;

import com.wdiscute.starcatcher.fish.FishProperties;
import com.wdiscute.starcatcher.registry.fishrestrictions.AbstractFishRestriction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yofred.dev.justcompatible.JustCompatibleConfig;

@Pseudo
@Mixin(targets = "com.wdiscute.starcatcher.registry.fishrestrictions.DimensionRestriction", remap = false)
public abstract class StarcatcherDimensionRestrictionMixin {
    @Shadow private String dimensionEntry;

    @Inject(method = "adjustChance", at = @At("HEAD"), cancellable = true, require = 0)
    private void justcompatible$acceptVanillaLikeDimension(int chance, Level level, FishProperties properties,
            Entity entity, ItemStack rod, AbstractFishRestriction.Context context,
            CallbackInfoReturnable<Integer> cir) {
        if (!JustCompatibleConfig.STARCATCHER_DIMENSION_EFFECTS.get()) return;
        ResourceLocation effects = level.dimensionType().effectsLocation();
        String vanillaCategory = switch (effects.toString()) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "the_nether";
            case "minecraft:the_end" -> "the_end";
            default -> "";
        };
        if (dimensionEntry.equals(vanillaCategory)) cir.setReturnValue(0);
    }
}
