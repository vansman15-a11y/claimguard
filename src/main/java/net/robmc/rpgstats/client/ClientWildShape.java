package net.robmc.rpgstats.client;

import java.util.HashMap;
import java.util.Map;

/** Which players are currently in a druid form, keyed by entity id (0 none / 1 wolf / 2 dolphin). */
public final class ClientWildShape {

    private static final Map<Integer, Integer> forms = new HashMap<>();

    private ClientWildShape() {
    }

    public static void set(int entityId, int form) {
        if (form <= 0) {
            forms.remove(entityId);
        } else {
            forms.put(entityId, form);
        }
    }

    public static int form(int entityId) {
        return forms.getOrDefault(entityId, 0);
    }

    public static boolean isTransformed(int entityId) {
        return forms.containsKey(entityId);
    }
}
