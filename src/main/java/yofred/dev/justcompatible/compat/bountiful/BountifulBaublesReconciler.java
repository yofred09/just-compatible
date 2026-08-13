package yofred.dev.justcompatible.compat.bountiful;

import com.jinqinxixi.bountifulbaubles.ModComponents;
import com.jinqinxixi.bountifulbaubles.modifier.ModifiableBaubleItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.CuriosApi;
import yofred.dev.justcompatible.JustCompatible;
import yofred.dev.justcompatible.JustCompatibleConfig;
import yofred.dev.justcompatible.mixin.MindsEyeStateAccessor;

public final class BountifulBaublesReconciler {
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
    }

    public static void reconcile(ServerPlayer player) {
        if (!JustCompatibleConfig.BOUNTIFUL_RECONCILE.get()) return;
        resetSessionClocks(player);
        int repairedClocks = repairMigratedClocks(player);
        List<ItemStack> equipped = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.getCurios().values().stream()
                        .flatMap(slot -> {
                            var stacks = slot.getStacks();
                            List<ItemStack> values = new ArrayList<>();
                            for (int i = 0; i < stacks.getSlots(); i++) values.add(stacks.getStackInSlot(i));
                            return values.stream();
                        })
                        .filter(stack -> !stack.isEmpty() && stack.getItem() instanceof ModifiableBaubleItem)
                        .toList())
                .orElse(List.of());

        // Remove only identifiers owned by Bountiful. Other mods' attributes are never touched.
        for (var attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            List<ResourceLocation> stale = instance.getModifiers().stream()
                    .map(AttributeModifier::id)
                    .filter(id -> id.getNamespace().equals("bountifulbaubles"))
                    .toList();
            stale.forEach(instance::removeModifier);
        }
        for (ItemStack stack : equipped) {
            ((ModifiableBaubleItem) stack.getItem()).applyModifier(player, stack);
        }
        JustCompatible.LOGGER.info("Reconciled {} equipped Bountiful Baubles and {} migrated item clocks for {}",
                equipped.size(), repairedClocks, player.getGameProfile().getName());
    }

    private static void resetSessionClocks(ServerPlayer player) {
        UUID id = player.getUUID();
        MindsEyeStateAccessor.justcompatible$getMarkedTargets().remove(id);
        MindsEyeStateAccessor.justcompatible$getShouldRemark().remove(id);
        MindsEyeStateAccessor.justcompatible$getScanCooldowns().remove(id);
    }

    /** Only inspects the player's small personal containers. It never scans worlds or loads chunks. */
    private static int repairMigratedClocks(ServerPlayer player) {
        long now = player.level().getGameTime();
        long maxFuture = JustCompatibleConfig.BOUNTIFUL_MAX_FUTURE_TICKS.get();
        Set<ItemStack> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) stacks.add(player.getInventory().getItem(i));
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) stacks.add(player.getEnderChestInventory().getItem(i));
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> handler.getCurios().values().forEach(slot -> {
            var contents = slot.getStacks();
            for (int i = 0; i < contents.getSlots(); i++) stacks.add(contents.getStackInSlot(i));
        }));

        int repaired = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("bountifulbaubles")) continue;
            Long lastUse = stack.get(ModComponents.LAST_USE_TIME.get());
            if (lastUse != null && lastUse > now) {
                stack.set(ModComponents.LAST_USE_TIME.get(), Math.max(0L, now - 160L));
                repaired++;
            }
            Long darkEgg = stack.get(ModComponents.DARK_EGG_COOLDOWN.get());
            if (darkEgg != null && darkEgg > now + maxFuture) {
                stack.remove(ModComponents.DARK_EGG_COOLDOWN.get());
                repaired++;
            }
            Long madAura = stack.get(ModComponents.MAD_AURA_COOLDOWN.get());
            if (madAura != null && madAura > now + maxFuture) {
                stack.remove(ModComponents.MAD_AURA_COOLDOWN.get());
                repaired++;
            }
        }
        return repaired;
    }

    private BountifulBaublesReconciler() {}
}
