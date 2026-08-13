package yofred.dev.justcompatible.mixin;

import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.jinqinxixi.bountifulbaubles.items.Baubles.MindsEyeItem", remap = false)
public interface MindsEyeStateAccessor {
    @Accessor("MARKED_TARGETS")
    static Map<UUID, ?> justcompatible$getMarkedTargets() { throw new AssertionError(); }

    @Accessor("SHOULD_REMARK")
    static Map<UUID, ?> justcompatible$getShouldRemark() { throw new AssertionError(); }

    @Accessor("SCAN_COOLDOWNS")
    static Map<UUID, ?> justcompatible$getScanCooldowns() { throw new AssertionError(); }
}
