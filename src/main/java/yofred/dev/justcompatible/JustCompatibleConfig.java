package yofred.dev.justcompatible;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class JustCompatibleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue WAYSTONES_ENABLED = BUILDER
            .comment("Enable the Waystones world-migration adapter.")
            .define("integrations.waystones.enabled", true);
    public static final ModConfigSpec.BooleanValue WAYSTONES_AUTO_DETECT = BUILDER
            .comment("Find moved waystones by matching their UUID and block position across loaded server dimensions.")
            .define("integrations.waystones.autoDetect", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_MAPPINGS = BUILDER
            .comment("Optional mappings in old_dimension=new_dimension form. No world id is hardcoded.")
            .defineListAllowEmpty("dimensionMappings", List.of(), value -> value instanceof String text && text.contains("="));
    public static final ModConfigSpec.IntValue MAX_WAYSTONES_PER_RUN = BUILDER
            .comment("Safety limit for one scan or repair operation.")
            .defineInRange("integrations.waystones.maxPerRun", 1000, 1, 100000);
    public static final ModConfigSpec.BooleanValue VINERY_SHARED_CLOCK = BUILDER
            .comment("Use the highest active server dimension time for Vinery ageing. Does not modify wine items or world data.")
            .define("integrations.vinery.sharedClock", true);
    public static final ModConfigSpec.BooleanValue STARCATCHER_DIMENSION_EFFECTS = BUILDER
            .comment("Treat dimensions with vanilla Overworld/Nether/End effects as their corresponding Starcatcher category.")
            .define("integrations.starcatcher.dimensionEffects", true);
    public static final ModConfigSpec.BooleanValue BOUNTIFUL_RECONCILE = BUILDER
            .comment("Reconcile only Bountiful Baubles attribute IDs with currently equipped Curios after login or dimension travel.")
            .define("integrations.bountifulBaubles.reconcileAttributes", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private JustCompatibleConfig() {}
}
