package local.superopt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for SuperOpt. Stored as a plain properties file so it survives restarts and can
 * be edited by hand while the server is offline.
 *
 * <p>Keys: {@code default-cap} (0 = no override), {@code async-player-save} (true/false) and one
 * entry per player UUID holding that player's view-distance cap.
 */
public final class ViewCapConfig {
    private static final Path PATH = Paths.get("config", "superopt.properties");
    private static final Map<UUID, Integer> CAPS = new ConcurrentHashMap<>();
    private static volatile int defaultCap = 0;
    private static volatile boolean asyncPlayerSave = true;

    private ViewCapConfig() {
    }

    public static void load() {
        CAPS.clear();
        defaultCap = 0;
        asyncPlayerSave = true;
        if (!Files.isRegularFile(PATH)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(PATH)) {
            properties.load(in);
        } catch (IOException e) {
            SuperOpt.LOGGER.warn("Failed to read {}", PATH, e);
            return;
        }
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key, "").trim();
            if ("default-cap".equalsIgnoreCase(key)) {
                defaultCap = parse(value, 0, 0, 32);
                continue;
            }
            if ("async-player-save".equalsIgnoreCase(key)) {
                asyncPlayerSave = Boolean.parseBoolean(value);
                continue;
            }
            int parsed = parse(value, -1, 0, 32);
            if (parsed < 0) {
                continue;
            }
            try {
                CAPS.put(UUID.fromString(key), parsed);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static int parse(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static int getCap(UUID player) {
        Integer cap = CAPS.get(player);
        return cap != null ? cap : defaultCap;
    }

    public static int getDefaultCap() {
        return defaultCap;
    }

    public static boolean isAsyncPlayerSaveEnabled() {
        return asyncPlayerSave;
    }

    public static void setAsyncPlayerSaveEnabled(boolean enabled) {
        asyncPlayerSave = enabled;
        save();
    }

    public static void setCap(UUID player, int cap) {
        if (cap <= 0) {
            CAPS.remove(player);
        } else {
            CAPS.put(player, cap);
        }
        save();
    }

    public static void setDefaultCap(int cap) {
        defaultCap = Math.max(0, cap);
        save();
    }

    public static Map<UUID, Integer> snapshot() {
        return Map.copyOf(CAPS);
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("default-cap", Integer.toString(defaultCap));
        properties.setProperty("async-player-save", Boolean.toString(asyncPlayerSave));
        CAPS.forEach((uuid, cap) -> properties.setProperty(uuid.toString(), Integer.toString(cap)));
        try {
            Files.createDirectories(PATH.getParent());
            try (OutputStream out = Files.newOutputStream(PATH)) {
                properties.store(out, "SuperOpt settings (default-cap 0 = no override)");
            }
        } catch (IOException e) {
            SuperOpt.LOGGER.warn("Failed to write {}", PATH, e);
        }
    }
}
