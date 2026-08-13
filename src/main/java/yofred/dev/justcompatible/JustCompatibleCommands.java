package yofred.dev.justcompatible;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import yofred.dev.justcompatible.compat.waystones.WaystoneMigration;
import yofred.dev.justcompatible.compat.vault.VaultTimerMigration;
import yofred.dev.justcompatible.bootstrap.ModDoctorLocator;

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
                .then(Commands.literal("doctor")
                        .then(Commands.literal("status").executes(context -> doctorStatus(context.getSource())))
                        .then(Commands.literal("report").executes(context -> doctorReport(context.getSource())))
                        .then(Commands.literal("restore")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> doctorRestore(context.getSource()))))
                .then(Commands.literal("waystones")
                        .then(Commands.literal("scan").executes(context -> scan(context.getSource())))
                        .then(Commands.literal("repair")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> repair(context.getSource()))))
                .then(Commands.literal("vaults")
                        .then(Commands.literal("scan").executes(context -> scanVaults(context.getSource())))
                        .then(Commands.literal("repair")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> repairVaults(context.getSource())))));
    }

    private static int doctorStatus(CommandSourceStack source) {
        try {
            List<String> lines = ModDoctorLocator.readReport();
            lines.stream().limit(4).forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
            return lines.size();
        } catch (IOException error) {
            source.sendFailure(Component.literal("Could not read Mod Doctor status: " + error.getMessage()));
            return 0;
        }
    }

    private static int doctorReport(CommandSourceStack source) {
        try {
            List<String> lines = ModDoctorLocator.readReport();
            lines.stream().limit(100).forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
            return lines.size();
        } catch (IOException error) {
            source.sendFailure(Component.literal("Could not read Mod Doctor report: " + error.getMessage()));
            return 0;
        }
    }

    private static int doctorRestore(CommandSourceStack source) {
        try {
            int restored = ModDoctorLocator.restoreAll();
            source.sendSuccess(() -> Component.literal("Restored " + restored + " quarantined mod files. Restart required."), true);
            return restored;
        } catch (IOException error) {
            source.sendFailure(Component.literal("Restore aborted safely: " + error.getMessage()));
            return 0;
        }
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Just Compatible " + JustCompatible.VERSION + " (server-side)"), false);
        source.sendSuccess(() -> Component.literal("Waystones adapter: " + CompatibilityProbe.status("waystones", CompatibilityProbe.waystonesSupported())), false);
        source.sendSuccess(() -> Component.literal("Vinery shared clock: " + integrationStatus("vinery", JustCompatibleConfig.VINERY_SHARED_CLOCK.get(), CompatibilityProbe.vinerySupported())), false);
        source.sendSuccess(() -> Component.literal("Starcatcher dimension detection: " + integrationStatus("starcatcher", JustCompatibleConfig.STARCATCHER_DIMENSION_EFFECTS.get(), CompatibilityProbe.starcatcherSupported())), false);
        source.sendSuccess(() -> Component.literal("Bountiful Baubles reconciliation: " + integrationStatus("bountifulbaubles", JustCompatibleConfig.BOUNTIFUL_RECONCILE.get(), CompatibilityProbe.bountifulSupported())), false);
        source.sendSuccess(() -> Component.literal("Vanilla Vault timer migration: " + (JustCompatibleConfig.VAULT_TIMERS_ENABLED.get() ? "active" : "disabled")), false);
        return 1;
    }

    private static int scanAll(CommandSourceStack source) {
        info(source);
        int waystones = CompatibilityProbe.waystonesSupported() && JustCompatibleConfig.WAYSTONES_ENABLED.get() ? scan(source) : 0;
        int vaults = scanVaults(source);
        return waystones + vaults;
    }

    private static int repairAll(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Global repair excludes Waystones because their migration requires an explicit command."), false);
        return repairVaults(source);
    }

    private static int scanVaults(CommandSourceStack source) {
        if (!JustCompatibleConfig.VAULT_TIMERS_ENABLED.get()) {
            source.sendFailure(Component.literal("The Vault timer adapter is disabled in the server config."));
            return 0;
        }
        VaultTimerMigration.ScanResult result = VaultTimerMigration.scan(source.getServer());
        source.sendSuccess(() -> Component.literal("Found " + result.observations().size()
                + " loaded Vaults with migrated future timers."), false);
        result.observations().stream().limit(50).forEach(observation -> source.sendSuccess(() -> Component.literal(
                observation.key().dimension().location() + " " + observation.key().pos().toShortString()
                        + ": deadline=" + observation.deadline() + ", gameTime=" + observation.observedGameTime()
                        + ", excess=" + observation.excessTicks() + " ticks"), false));
        source.sendSuccess(() -> Component.literal("Dry run only. The scan never loads chunks. Visit a Vault first if it is not listed."), false);
        return result.observations().size();
    }

    private static int repairVaults(CommandSourceStack source) {
        if (!JustCompatibleConfig.VAULT_TIMERS_ENABLED.get()) {
            source.sendFailure(Component.literal("The Vault timer adapter is disabled in the server config."));
            return 0;
        }
        VaultTimerMigration.ScanResult result = VaultTimerMigration.scan(source.getServer());
        if (result.observations().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No loaded Vault timers require repair."), false);
            return 0;
        }
        try {
            VaultTimerMigration.RepairResult repaired = VaultTimerMigration.repair(source.getServer(), result);
            source.sendSuccess(() -> Component.literal("Repaired " + repaired.repaired()
                    + " Vault timers without changing keys, loot tables, or rewarded players. Backup: "
                    + repaired.backup()), true);
            return repaired.repaired();
        } catch (IOException exception) {
            JustCompatible.LOGGER.error("Vault timer repair failed", exception);
            source.sendFailure(Component.literal("Vault repair aborted: " + exception.getMessage()));
            return 0;
        }
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
