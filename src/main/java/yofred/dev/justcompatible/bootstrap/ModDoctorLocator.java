package yofred.dev.justcompatible.bootstrap;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Environment;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;

/**
 * Early, dedicated-server-only mod folder doctor.
 *
 * <p>The locator approach is based on BOs Mods Optimizer by Markus Bordihn (MIT). The parser and
 * quarantine implementation are original and intentionally more conservative: files are never
 * deleted, and duplicate cleanup is attempted only when both jars expose the exact same mod-id set.
 */
public final class ModDoctorLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern MOD_BLOCK = Pattern.compile("(?ms)\\[\\[mods]](.*?)(?=\\[\\[|\\z)");
    private static final Pattern MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*version\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern DEP_BLOCK = Pattern.compile("(?ms)\\[\\[dependencies\\.[^]]+]](.*?)(?=\\[\\[|\\z)");
    private static final Pattern SIDE = Pattern.compile("(?m)^\\s*side\\s*=\\s*[\"']CLIENT[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEP_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"'](minecraft|neoforge|forge)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PROTECTED_IDS = Set.of("minecraft", "neoforge", "javafml", "lowcodefml", "justcompatible", "justcore");
    public static final String REPORT_FILE = "justcompatible-mod-doctor-report.txt";
    public static final String MANIFEST_FILE = "justcompatible-mod-doctor-manifest.jsonl";

    public ModDoctorLocator() {
        if (!isDedicatedServer()) return;
        try {
            scanAndQuarantine(FMLPaths.MODSDIR.get(), FMLPaths.GAMEDIR.get().resolve("config"));
        } catch (Throwable error) {
            // A doctor must never become the reason the server cannot boot.
            LOGGER.error("[Just Compatible/Mod Doctor] Scan failed; no files were changed.", error);
        }
    }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        // Work is intentionally completed in the constructor, before normal mod discovery.
    }

    private static boolean isDedicatedServer() {
        Environment environment = Launcher.INSTANCE.environment();
        Optional<String> target = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get());
        return target.map(value -> value.toLowerCase(Locale.ROOT).contains("server")).orElse(false);
    }

    private static void scanAndQuarantine(Path modsDir, Path configDir) throws IOException {
        Files.createDirectories(configDir);
        boolean autoFix = readAutoFix(configDir.resolve("justcompatible-mod-doctor.properties"));
        List<JarMetadata> jars = new ArrayList<>();
        try (var paths = Files.list(modsDir)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".jar")).toList()) {
                try {
                    jars.add(readMetadata(path));
                } catch (Exception error) {
                    LOGGER.warn("[Just Compatible/Mod Doctor] Could not inspect {}: {}", path.getFileName(), error.getMessage());
                }
            }
        }

        List<Action> actions = new ArrayList<>();
        for (JarMetadata jar : jars) {
            if (jar.explicitClientOnly()) actions.add(new Action(jar, "explicit-client-only metadata"));
        }

        Map<Set<String>, List<JarMetadata>> byExactIdSet = new HashMap<>();
        for (JarMetadata jar : jars) {
            if (!jar.modIds().isEmpty()) byExactIdSet.computeIfAbsent(jar.modIds(), ignored -> new ArrayList<>()).add(jar);
        }
        for (List<JarMetadata> group : byExactIdSet.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparing(JarMetadata::version, ModDoctorLocator::compareVersions).reversed()
                    .thenComparing(jar -> jar.path().getFileName().toString().length()));
            JarMetadata keep = group.getFirst();
            for (int i = 1; i < group.size(); i++) {
                JarMetadata duplicate = group.get(i);
                if (!duplicate.path().equals(keep.path())) {
                    actions.add(new Action(duplicate, "duplicate of " + keep.path().getFileName() + " (same mod-id set; older/lower version)"));
                }
            }
        }

        // One action per file, with client-only taking precedence in the report.
        Map<Path, Action> unique = new HashMap<>();
        for (Action action : actions) unique.putIfAbsent(action.jar().path(), action);
        Path report = configDir.resolve(REPORT_FILE);
        List<String> reportLines = new ArrayList<>();
        reportLines.add("Just Compatible Mod Doctor - " + Instant.now());
        reportLines.add("Scanned jars: " + jars.size());
        reportLines.add("Safe candidates: " + unique.size());
        reportLines.add("Automatic quarantine: " + autoFix);

        unique.entrySet().removeIf(entry -> entry.getValue().jar().modIds().stream().anyMatch(PROTECTED_IDS::contains));
        if (unique.isEmpty()) {
            LOGGER.info("[Just Compatible/Mod Doctor] Scanned {} jars; no safe cleanup candidates found.", jars.size());
        } else if (!autoFix) {
            unique.values().forEach(action -> reportLines.add("REPORT " + action.jar().path().getFileName() + " :: " + action.reason()));
            LOGGER.warn("[Just Compatible/Mod Doctor] Found {} candidates. Automatic quarantine is disabled; see {}.", unique.size(), report);
        } else {
            Path quarantine = modsDir.resolve("justcompatible-quarantine");
            Files.createDirectories(quarantine);
            for (Action action : unique.values()) {
                Path source = action.jar().path();
                Path destination = uniqueDestination(quarantine, source.getFileName().toString() + ".disabled");
                try {
                    Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicFailure) {
                    Files.move(source, destination);
                }
                reportLines.add("QUARANTINED " + source.getFileName() + " -> " + destination.getFileName() + " :: " + action.reason());
                appendManifest(configDir.resolve(MANIFEST_FILE), source, destination, action);
                LOGGER.warn("[Just Compatible/Mod Doctor] Quarantined {}: {}", source.getFileName(), action.reason());
            }
        }
        Files.write(report, reportLines, StandardCharsets.UTF_8);
    }

    private static void appendManifest(Path manifest, Path source, Path destination, Action action) throws IOException {
        String json = "{\"timestamp\":\"" + Instant.now() + "\",\"original\":\"" + escape(source.toAbsolutePath().toString())
                + "\",\"quarantined\":\"" + escape(destination.toAbsolutePath().toString()) + "\",\"sha256\":\""
                + sha256(destination) + "\",\"version\":\"" + escape(action.jar().version()) + "\",\"reason\":\""
                + escape(action.reason()) + "\"}" + System.lineSeparator();
        Files.writeString(manifest, json, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    public static List<String> readReport() throws IOException {
        Path report = FMLPaths.GAMEDIR.get().resolve("config").resolve(REPORT_FILE);
        return Files.exists(report) ? Files.readAllLines(report, StandardCharsets.UTF_8) : List.of("No Mod Doctor report exists yet.");
    }

    public static int restoreAll() throws IOException {
        Path manifest = FMLPaths.GAMEDIR.get().resolve("config").resolve(MANIFEST_FILE);
        if (!Files.exists(manifest)) return 0;
        Pattern original = Pattern.compile("\\\"original\\\":\\\"([^\\\"]+)\\\"");
        Pattern quarantined = Pattern.compile("\\\"quarantined\\\":\\\"([^\\\"]+)\\\"");
        int restored = 0;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            Matcher sourceMatch = original.matcher(line);
            Matcher quarantineMatch = quarantined.matcher(line);
            if (!sourceMatch.find() || !quarantineMatch.find()) continue;
            Path source = Path.of(unescape(sourceMatch.group(1)));
            Path quarantine = Path.of(unescape(quarantineMatch.group(1)));
            if (Files.exists(quarantine) && !Files.exists(source)) {
                Files.move(quarantine, source);
                restored++;
            }
        }
        if (restored > 0) Files.move(manifest, manifest.resolveSibling(MANIFEST_FILE + ".restored-" + Instant.now().toEpochMilli()));
        return restored;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String unescape(String value) { return value.replace("\\\"", "\"").replace("\\\\", "\\"); }

    private static boolean readAutoFix(Path config) throws IOException {
        if (!Files.exists(config)) {
            Files.writeString(config,
                    "# Just Compatible early server mod-folder doctor. Files are moved, never deleted.\n" +
                    "automaticSafeFixes=false\n", StandardCharsets.UTF_8);
            return false;
        }
        return Files.readAllLines(config, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .anyMatch(line -> line.equalsIgnoreCase("automaticSafeFixes=true"));
    }

    private static JarMetadata readMetadata(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            var entry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = jar.getJarEntry("META-INF/mods.toml");
            if (entry == null) return new JarMetadata(path, Set.of(), "0", false);
            String toml;
            try (InputStream input = jar.getInputStream(entry)) {
                toml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            Set<String> ids = new HashSet<>();
            String version = "0";
            Matcher blocks = MOD_BLOCK.matcher(toml);
            while (blocks.find()) {
                String block = blocks.group(1);
                Matcher id = MOD_ID.matcher(block);
                if (id.find()) ids.add(id.group(1));
                Matcher foundVersion = VERSION.matcher(block);
                if (foundVersion.find() && version.equals("0")) version = foundVersion.group(1);
            }
            boolean clientOnly = false;
            Matcher dependencies = DEP_BLOCK.matcher(toml);
            while (dependencies.find()) {
                String block = dependencies.group(1);
                if (DEP_ID.matcher(block).find() && SIDE.matcher(block).find()) {
                    clientOnly = true;
                    break;
                }
            }
            return new JarMetadata(path, Set.copyOf(ids), version, clientOnly);
        }
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.replaceAll("^[^0-9]*", "").split("[^0-9]+");
        String[] b = right.replaceAll("^[^0-9]*", "").split("[^0-9]+");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            long av = i < a.length && !a[i].isEmpty() ? parseLong(a[i]) : 0;
            long bv = i < b.length && !b[i].isEmpty() ? parseLong(b[i]) : 0;
            int result = Long.compare(av, bv);
            if (result != 0) return result;
        }
        return left.compareToIgnoreCase(right);
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) { return 0; }
    }

    private static Path uniqueDestination(Path directory, String name) {
        Path candidate = directory.resolve(name);
        int suffix = 1;
        while (Files.exists(candidate)) candidate = directory.resolve(name + "." + suffix++);
        return candidate;
    }

    private record JarMetadata(Path path, Set<String> modIds, String version, boolean explicitClientOnly) {}
    private record Action(JarMetadata jar, String reason) {}
}
