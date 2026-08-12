package yofred.dev.justcompatible.compat.vault;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.storage.LevelResource;
import yofred.dev.justcompatible.JustCompatibleConfig;
import yofred.dev.justcompatible.mixin.VaultServerDataAccessor;

/** Tracks ticking vanilla Vault block entities without scanning or loading chunks. */
public final class VaultTimerMigration {
    private static final Map<Key, Observation> OBSERVATIONS = new ConcurrentHashMap<>();

    public static void observe(ServerLevel level, BlockPos pos, VaultServerData data) {
        if (!JustCompatibleConfig.VAULT_TIMERS_ENABLED.get()) return;
        long now = level.getGameTime();
        long deadline = timer(data);
        Key key = new Key(level.dimension(), pos.immutable());
        if (deadline > now + JustCompatibleConfig.VAULT_MAX_FUTURE_TICKS.get()) {
            OBSERVATIONS.put(key, new Observation(key, now, deadline));
        } else {
            OBSERVATIONS.remove(key);
        }
    }

    public static ScanResult scan(MinecraftServer server) {
        List<Observation> found = new ArrayList<>();
        OBSERVATIONS.forEach((key, observation) -> {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || !level.hasChunkAt(key.pos())) return;
            BlockEntity entity = level.getBlockEntity(key.pos());
            if (!(entity instanceof VaultBlockEntity vault)) return;
            long now = level.getGameTime();
            long deadline = timer(vault.getServerData());
            if (deadline > now + JustCompatibleConfig.VAULT_MAX_FUTURE_TICKS.get()) {
                found.add(new Observation(key, now, deadline));
            } else {
                OBSERVATIONS.remove(key);
            }
        });
        found.sort(Comparator.comparing((Observation o) -> o.key().dimension().location().toString())
                .thenComparingLong(o -> o.key().pos().asLong()));
        return new ScanResult(List.copyOf(found));
    }

    public static RepairResult repair(MinecraftServer server, ScanResult scan) throws IOException {
        if (scan.observations().isEmpty()) return new RepairResult(0, null);
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("justcompatible-backups");
        Files.createDirectories(directory);
        String stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path backup = directory.resolve("vault-timers-" + stamp + ".json");
        List<BackupEntry> backupEntries = scan.observations().stream()
                .map(BackupEntry::from)
                .toList();
        Files.writeString(backup, new GsonBuilder().setPrettyPrinting().create().toJson(backupEntries),
                StandardCharsets.UTF_8);

        int repaired = 0;
        for (Observation observation : scan.observations()) {
            ServerLevel level = server.getLevel(observation.key().dimension());
            if (level == null || !level.hasChunkAt(observation.key().pos())) continue;
            BlockEntity entity = level.getBlockEntity(observation.key().pos());
            if (!(entity instanceof VaultBlockEntity vault)) continue;
            long now = level.getGameTime();
            VaultServerData data = vault.getServerData();
            long deadline = timer(data);
            if (deadline <= now + JustCompatibleConfig.VAULT_MAX_FUTURE_TICKS.get()) continue;
            if (deadline != observation.deadline()) continue; // changed since the dry run
            ((VaultServerDataAccessor) (Object) data).justcompatible$pauseStateUpdatingUntil(now);
            vault.setChanged();
            OBSERVATIONS.remove(observation.key());
            repaired++;
        }
        return new RepairResult(repaired, backup);
    }

    public record Key(ResourceKey<Level> dimension, BlockPos pos) {}
    public record Observation(Key key, long observedGameTime, long deadline) {
        public long excessTicks() { return deadline - observedGameTime; }
    }
    public record ScanResult(List<Observation> observations) {}
    public record RepairResult(int repaired, Path backup) {}
    private record BackupEntry(String dimension, int x, int y, int z, long observedGameTime, long originalDeadline) {
        private static BackupEntry from(Observation observation) {
            BlockPos pos = observation.key().pos();
            return new BackupEntry(observation.key().dimension().location().toString(), pos.getX(), pos.getY(),
                    pos.getZ(), observation.observedGameTime(), observation.deadline());
        }
    }

    private static long timer(VaultServerData data) {
        return ((VaultServerDataAccessor) (Object) data).justcompatible$getStateUpdatingResumesAt();
    }

    private VaultTimerMigration() {}
}
