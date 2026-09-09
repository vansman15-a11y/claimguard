package net.robmc.rpgstats.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Where the movable HUD elements sit, as an offset from their default anchor.
 * Edited in {@link HudEditorScreen} (press J) and saved to
 * {@code config/rpgstats-hud.json} so it survives restarts. Per-client only.
 */
public final class HudLayout {

    public static final String STAT_BARS = "statbars";
    public static final String HOTBAR = "hotbar";
    public static final String SPELL_BAR = "spellbar";
    public static final String SPELL_BAR_2 = "spellbar2";
    public static final String STATUS = "status";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("rpgstats-hud.json");

    private static Map<String, int[]> offsets = new HashMap<>();
    private static boolean loaded = false;

    private HudLayout() {
    }

    public static int offX(String element) {
        return get(element)[0];
    }

    public static int offY(String element) {
        return get(element)[1];
    }

    public static void set(String element, int x, int y) {
        ensureLoaded();
        offsets.put(element, new int[]{x, y});
    }

    public static void reset() {
        ensureLoaded();
        offsets.clear();
        save();
    }

    private static int[] get(String element) {
        ensureLoaded();
        return offsets.getOrDefault(element, new int[]{0, 0});
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                Map<?, ?> raw = GSON.fromJson(Files.readString(FILE), Map.class);
                if (raw != null) {
                    raw.forEach((k, v) -> {
                        if (k instanceof String key && v instanceof java.util.List<?> list && list.size() >= 2) {
                            offsets.put(key, new int[]{
                                    ((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue()});
                        }
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            offsets = new HashMap<>();
        }
    }

    public static void save() {
        ensureLoaded();
        try {
            Files.writeString(FILE, GSON.toJson(offsets));
        } catch (IOException ignored) {
        }
    }
}
