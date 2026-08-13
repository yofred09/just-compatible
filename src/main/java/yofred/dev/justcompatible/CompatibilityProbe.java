package yofred.dev.justcompatible;

import java.util.Arrays;
import net.neoforged.fml.ModList;

public final class CompatibilityProbe {
    public static boolean waystonesSupported() {
        return loaded("waystones")
                && hasMethods("net.blay09.mods.waystones.api.WaystonesAPI", "getAllWaystones", "getWaystoneAt")
                && hasMethods("net.blay09.mods.waystones.api.MutableWaystone", "setDimension")
                && hasMethods("net.blay09.mods.waystones.core.WaystoneIndexManager", "rebuildIndex");
    }

    public static boolean bountifulSupported() {
        return loaded("bountifulbaubles") && loaded("curios")
                && hasMethods("com.jinqinxixi.bountifulbaubles.modifier.ModifiableBaubleItem", "applyModifier")
                && hasMethods("top.theillusivec4.curios.api.CuriosApi", "getCuriosInventory");
    }

    public static boolean vinerySupported() {
        return loaded("vinery") && hasMethods("net.satisfy.vinery.core.util.WineYears", "getDays");
    }

    public static boolean starcatcherSupported() {
        return loaded("starcatcher")
                && hasMethods("com.wdiscute.starcatcher.registry.fishrestrictions.DimensionRestriction", "adjustChance");
    }

    public static String status(String modId, boolean apiSupported) {
        if (!loaded(modId)) return "mod not installed";
        return apiSupported ? "active" : "unsupported API shape; safely disabled";
    }

    private static boolean loaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static boolean hasMethods(String className, String... names) {
        try {
            Class<?> type = Class.forName(className, false, CompatibilityProbe.class.getClassLoader());
            return Arrays.stream(names).allMatch(name -> Arrays.stream(type.getMethods()).anyMatch(method -> method.getName().equals(name)));
        } catch (LinkageError | ReflectiveOperationException | RuntimeException failure) {
            JustCompatible.LOGGER.warn("Optional integration API unavailable: {}", className);
            return false;
        }
    }

    private CompatibilityProbe() {}
}
