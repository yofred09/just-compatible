package yofred.dev.justcompatible;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import yofred.dev.justcompatible.compat.waystones.WaystoneMigration;

public final class JustCompatibleCommands {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("justcompatible")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("scan").executes(context -> scanAll(context.getSource())))
                .then(Commands.literal("repair")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> repairAll(context.getSource())))
                .then(Commands.literal("waystones")
                        .then(Commands.literal("scan").executes(context -> scan(context.getSource())))
                        .then(Commands.literal("repair")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> repair(context.getSource())))));
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Just Compatible 0.2.1 (server-side)"), false);
        source.sendSuccess(() -> Component.literal("Waystones adapter: " + CompatibilityProbe.status("waystones", CompatibilityProbe.waystonesSupported())), false);
        source.sendSuccess(() -> Component.literal("Vinery shared clock: " + integrationStatus("vinery", JustCompatibleConfig.VINERY_SHARED_CLOCK.get(), CompatibilityProbe.vinerySupported())), false);
        source.sendSuccess(() -> Component.literal("Starcatcher dimension detection: " + integrationStatus("starcatcher", JustCompatibleConfig.STARCATCHER_DIMENSION_EFFECTS.get(), CompatibilityProbe.starcatcherSupported())), false);
        source.sendSuccess(() -> Component.literal("Bountiful Baubles reconciliation: " + integrationStatus("bountifulbaubles", JustCompatibleConfig.BOUNTIFUL_RECONCILE.get(), CompatibilityProbe.bountifulSupported())), false);
        return 1;
    }

    private static int scanAll(CommandSourceStack source) {
        info(source);
        if (CompatibilityProbe.waystonesSupported() && JustCompatibleConfig.WAYSTONES_ENABLED.get()) return scan(source);
        source.sendSuccess(() -> Component.literal("No persistent data requires migration. Runtime adapters do not write world data."), false);
        return 1;
    }

    private static int repairAll(CommandSourceStack source) {
        if (CompatibilityProbe.waystonesSupported() && JustCompatibleConfig.WAYSTONES_ENABLED.get()) return repair(source);
        source.sendSuccess(() -> Component.literal("Nothing persistent to repair. Runtime adapters are already active."), false);
        return 1;
    }

    private static String status(String modId, boolean enabled) {
        return !ModList.get().isLoaded(modId) ? "mod not installed" : enabled ? "active" : "disabled";
    }

    private static String integrationStatus(String modId, boolean enabled, boolean supported) {
        if (!ModList.get().isLoaded(modId)) return "mod not installed";
        if (!enabled) return "disabled";
        return supported ? "active" : "unsupported API shape; safely disabled";
    }

    private static int scan(CommandSourceStack source) {
        if (!available(source)) return 0;
        WaystoneMigration.ScanResult result = WaystoneMigration.scan(source.getServer());
        source.sendSuccess(() -> Component.literal("Inspected " + result.inspected() + " waystones; " + result.proposals().size() + " safe migrations found; " + result.unresolved().size() + " unresolved."), false);
        result.proposals().stream().limit(25).forEach(proposal -> source.sendSuccess(() -> Component.literal(
                proposal.waystone().getName().getString() + ": " + proposal.from().location() + " -> " + proposal.to().location() + " [" + proposal.evidence() + "]"), false));
        result.unresolved().stream().limit(25).forEach(message -> source.sendFailure(Component.literal(message)));
        source.sendSuccess(() -> Component.literal("This was a dry run. Use /justcompatible waystones repair to apply safe matches."), false);
        return result.proposals().size();
    }

    private static int repair(CommandSourceStack source) {
        if (!available(source)) return 0;
        WaystoneMigration.ScanResult result = WaystoneMigration.scan(source.getServer());
        if (result.proposals().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No safe Waystones migrations were found."), false);
            return 0;
        }
        try {
            Path backup = WaystoneMigration.repair(source.getServer(), result);
            source.sendSuccess(() -> Component.literal("Migrated " + result.proposals().size() + " waystones. Backup: " + backup), true);
            return result.proposals().size();
        } catch (Exception exception) {
            JustCompatible.LOGGER.error("Waystones migration failed", exception);
            source.sendFailure(Component.literal("Migration failed before completion: " + exception.getMessage()));
            return 0;
        }
    }

    private static boolean available(CommandSourceStack source) {
        if (!JustCompatibleConfig.WAYSTONES_ENABLED.get()) {
            source.sendFailure(Component.literal("The Waystones adapter is disabled in the server config."));
            return false;
        }
        if (!CompatibilityProbe.waystonesSupported()) {
            source.sendFailure(Component.literal("Waystones is absent or this version exposes a different API. The integration was safely disabled."));
            return false;
        }
        return true;
    }

    private JustCompatibleCommands() {}
}
