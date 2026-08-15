package yofred.dev.justcompatible.compat.bountiful;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;

public final class BountifulCurioTickTracker {
    private static final Map<ItemStack, Long> LAST_TICK = new WeakHashMap<>();

    /** Returns true only for the first attempt to tick this exact stack during a game tick. */
    public static boolean begin(ItemStack stack, long gameTime) {
        Long previous = LAST_TICK.put(stack, gameTime);
        return previous == null || previous.longValue() != gameTime;
    }

    public static boolean wasTicked(ItemStack stack, long gameTime) {
        Long previous = LAST_TICK.get(stack);
        return previous != null && previous.longValue() == gameTime;
    }

    private BountifulCurioTickTracker() {}
}
