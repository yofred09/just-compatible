package yofred.dev.justcompatible.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.SlotContext;
import yofred.dev.justcompatible.compat.bountiful.BountifulCurioTickTracker;

@Mixin(targets = {
        "com.jinqinxixi.bountifulbaubles.items.Baubles.BlazeHeartItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.DarkEggItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.EmberItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.GloryShardsItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.KarmaItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.MadAuraItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.MossyBeltItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.MossyRingItem",
        "com.jinqinxixi.bountifulbaubles.items.Baubles.TurtleShellItem"
}, remap = false)
public abstract class BountifulCurioTickMixin {
    @Inject(method = "curioTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void justcompatible$runOncePerTick(SlotContext context, ItemStack stack, CallbackInfo callback) {
        if (!BountifulCurioTickTracker.begin(stack, context.entity().level().getGameTime())) callback.cancel();
    }
}
