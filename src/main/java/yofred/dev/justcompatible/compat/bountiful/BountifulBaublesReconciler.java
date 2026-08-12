package yofred.dev.justcompatible.compat.bountiful;

import com.jinqinxixi.bountifulbaubles.modifier.ModifiableBaubleItem;
import java.util.ArrayList;
import java.util.List;
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

public final class BountifulBaublesReconciler {
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) reconcile(player);
    }

    public static void reconcile(ServerPlayer player) {
        if (!JustCompatibleConfig.BOUNTIFUL_RECONCILE.get()) return;
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
        JustCompatible.LOGGER.debug("Reconciled {} equipped Bountiful Baubles for {}", equipped.size(), player.getGameProfile().getName());
    }

    private BountifulBaublesReconciler() {}
}
