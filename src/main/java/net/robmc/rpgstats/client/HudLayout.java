package net.robmc.rpgstats.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Where the movable HUD elements sit, as an offset from their default anchor,
 * plus (for the two spell bars) a background opacity and vertical/horizontal
 * orientation. Edited in {@link HudEditorScreen} (press J) and saved to
 * {@code config/rpgstats-hud.json} so it survives restarts. Per-client only.
 */
public final class HudLayout {

    public static final String STAT_BARS = "statbars";
    public static final String HOTBAR = "hotbar";
    public static final String SPELL_BAR = "spellbar";
    public static final String SPELL_BAR_2 = "spellbar2";
    public static final String STATUS = "status";
    public static final String CAST_BAR = "castbar";
    public static final String TARGET_FRAME = "targetframe";
    public static final String POTION_ICONS = "potionicons";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("rpgstats-hud.json");

    private static Map<String, int[]> offsets = new HashMap<>();
    private static Map<String, Float> opacities = new HashMap<>();
    private static Map<String, Boolean> horizontal = new HashMap<>();
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

    /** 0-1. Defaults to fully opaque (1.0) for anything never touched in the editor. */
    public static float opacity(String element) {
        ensureLoaded();
        return opacities.getOrDefault(element, 1.0f);
    }

    public static void setOpacity(String element, float value) {
        ensureLoaded();
        opacities.put(element, Math.max(0.1f, Math.min(1.0f, value)));
    }

    /** False (vertical) unless the editor's been used to flip it. */
    public static boolean isHorizontal(String element) {
        ensureLoaded();
        return horizontal.getOrDefault(element, false);
    }

    public static void setHorizontal(String element, boolean value) {
        ensureLoaded();
        horizontal.put(element, value);
    }

    public static void reset() {
        ensureLoaded();
        offsets.clear();
        opacities.clear();
        horizontal.clear();
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
                JsonElement root = GSON.fromJson(Files.readString(FILE), JsonElement.class);
                if (root != null && root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    if (obj.has("offsets") || obj.has("opacity") || obj.has("horizontal")) {
                        readOffsets(nested(obj, "offsets"));
                        readOpacities(nested(obj, "opacity"));
                        readHorizontal(nested(obj, "horizontal"));
                    } else {
                        // old format: the whole file was just the offsets map
                        readOffsets(obj);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            offsets = new HashMap<>();
            opacities = new HashMap<>();
            horizontal = new HashMap<>();
        }
    }

    private static JsonObject nested(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static void readOffsets(JsonObject obj) {
        if (obj == null) {
            return;
        }
        for (String key : obj.keySet()) {
            JsonElement v = obj.get(key);
            if (v != null && v.isJsonArray() && v.getAsJsonArray().size() >= 2) {
                offsets.put(key, new int[]{v.getAsJsonArray().get(0).getAsInt(), v.getAsJsonArray().get(1).getAsInt()});
            }
        }
    }

    private static void readOpacities(JsonObject obj) {
        if (obj == null) {
            return;
        }
        for (String key : obj.keySet()) {
            opacities.put(key, obj.get(key).getAsFloat());
        }
    }

    private static void readHorizontal(JsonObject obj) {
        if (obj == null) {
            return;
        }
        for (String key : obj.keySet()) {
            horizontal.put(key, obj.get(key).getAsBoolean());
        }
    }

    public static void save() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        JsonObject offsetsObj = new JsonObject();
        offsets.forEach((k, v) -> {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            arr.add(v[0]);
            arr.add(v[1]);
            offsetsObj.add(k, arr);
        });
        JsonObject opacityObj = new JsonObject();
        opacities.forEach(opacityObj::addProperty);
        JsonObject horizontalObj = new JsonObject();
        horizontal.forEach(horizontalObj::addProperty);
        root.add("offsets", offsetsObj);
        root.add("opacity", opacityObj);
        root.add("horizontal", horizontalObj);
        try {
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException ignored) {
        }
    }
}
