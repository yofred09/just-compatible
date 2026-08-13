package yofred.dev.justcompatible.compat.waystones;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneIndexManager;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import yofred.dev.justcompatible.JustCompatible;
import yofred.dev.justcompatible.JustCompatibleConfig;

public final class WaystoneMigration {
    public record Proposal(Waystone waystone, ResourceKey<Level> from, ResourceKey<Level> to, String evidence) {}
    public record ScanResult(int inspected, List<Proposal> proposals, List<String> unresolved) {}

    public static ScanResult scan(MinecraftServer server) {
        int limit = JustCompatibleConfig.MAX_WAYSTONES_PER_RUN.get();
        Map<ResourceKey<Level>, ResourceKey<Level>> explicit = configuredMappings();
        List<Proposal> proposals = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<Waystone> all = WaystonesAPI.getAllWaystones(server).limit(limit).toList();

        for (Waystone waystone : all) {
            ResourceKey<Level> from = waystone.getDimension();
            ResourceKey<Level> mapped = explicit.get(from);
            if (mapped != null && server.getLevel(mapped) != null) {
                if (matchesAt(server.getLevel(mapped), waystone)) {
                    proposals.add(new Proposal(waystone, from, mapped, "configured mapping and matching waystone UUID at destination"));
                } else {
                    unresolved.add(label(waystone) + ": configured destination does not contain the same waystone UUID at " + waystone.getPos());
                }
                continue;
            }

            ServerLevel recordedLevel = server.getLevel(from);
            if (recordedLevel != null && matchesAt(recordedLevel, waystone)) {
                continue;
            }

            if (!JustCompatibleConfig.WAYSTONES_AUTO_DETECT.get()) {
                unresolved.add(label(waystone) + ": recorded dimension is unavailable or no longer contains the waystone");
                continue;
            }

            List<ServerLevel> matches = new ArrayList<>();
            for (ServerLevel candidate : server.getAllLevels()) {
                if (!candidate.dimension().equals(from) && matchesAt(candidate, waystone)) {
                    matches.add(candidate);
                }
            }
            if (matches.size() == 1) {
                proposals.add(new Proposal(waystone, from, matches.getFirst().dimension(), "matching waystone UUID and block position"));
            } else if (matches.isEmpty()) {
                unresolved.add(label(waystone) + ": no matching block entity found in an active dimension");
            } else {
                unresolved.add(label(waystone) + ": ambiguous match in " + matches.size() + " dimensions");
            }
        }
        return new ScanResult(all.size(), List.copyOf(proposals), List.copyOf(unresolved));
    }

    public static Path repair(MinecraftServer server, ScanResult result) throws IOException {
        Path backup = writeBackup(server, result.proposals());
        WaystoneManagerImpl manager = WaystoneManagerImpl.get(server);
        List<Proposal> changed = new ArrayList<>();
        try {
            for (Proposal proposal : result.proposals()) {
                if (!(proposal.waystone() instanceof MutableWaystone mutable)) {
                    throw new IllegalStateException("Waystone is not mutable: " + proposal.waystone().getWaystoneUid());
                }
                mutable.setDimension(proposal.to());
                changed.add(proposal);
            }
            manager.setDirty();
            WaystoneIndexManager.rebuildIndex(server);
        } catch (RuntimeException failure) {
            // Transaction-like rollback in memory: either the whole verified set succeeds or none remains changed.
            for (Proposal proposal : changed) {
                if (proposal.waystone() instanceof MutableWaystone mutable) mutable.setDimension(proposal.from());
            }
            manager.setDirty();
            WaystoneIndexManager.rebuildIndex(server);
            throw failure;
        }
        for (Proposal proposal : result.proposals()) {
            WaystoneSyncManager.sendWaystoneUpdateToAll(server, proposal.waystone());
        }
        JustCompatible.LOGGER.info("Migrated {} Waystones entries; backup written to {}", result.proposals().size(), backup);
        return backup;
    }

    private static boolean matchesAt(ServerLevel level, Waystone expected) {
        if (level == null) return false;
        if (!level.hasChunkAt(expected.getPos())) return false;
        try {
            return WaystonesAPI.getWaystoneAt(level, expected.getPos())
                    .map(actual -> actual.getWaystoneUid().equals(expected.getWaystoneUid()))
                    .orElse(false);
        } catch (RuntimeException exception) {
            JustCompatible.LOGGER.warn("Could not inspect Waystone {} in {}", expected.getWaystoneUid(), level.dimension().location(), exception);
            return false;
        }
    }

    private static Map<ResourceKey<Level>, ResourceKey<Level>> configuredMappings() {
        Map<ResourceKey<Level>, ResourceKey<Level>> result = new HashMap<>();
        for (String mapping : JustCompatibleConfig.DIMENSION_MAPPINGS.get()) {
            String[] parts = mapping.split("=", 2);
            ResourceLocation from = ResourceLocation.tryParse(parts[0].trim());
            ResourceLocation to = ResourceLocation.tryParse(parts[1].trim());
            if (from != null && to != null) {
                result.put(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, from),
                        ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, to));
            }
        }
        return result;
    }

    private static Path writeBackup(MinecraftServer server, List<Proposal> proposals) throws IOException {
        Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path directory = worldRoot.resolve("justcompatible").resolve("backups");
        Files.createDirectories(directory);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path target = directory.resolve("waystones-" + stamp + ".json");
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Proposal proposal : proposals) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", proposal.waystone().getWaystoneUid().toString());
            entry.put("name", proposal.waystone().getName().getString());
            entry.put("position", List.of(proposal.waystone().getPos().getX(), proposal.waystone().getPos().getY(), proposal.waystone().getPos().getZ()));
            entry.put("from", proposal.from().location().toString());
            entry.put("to", proposal.to().location().toString());
            entry.put("evidence", proposal.evidence());
            entries.add(entry);
        }
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(entries), StandardCharsets.UTF_8);
        return target;
    }

    private static String label(Waystone waystone) {
        return waystone.getName().getString() + " (" + waystone.getWaystoneUid() + ")";
    }

    private WaystoneMigration() {}
}
