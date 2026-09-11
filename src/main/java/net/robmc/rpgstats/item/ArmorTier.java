package net.robmc.rpgstats.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * The armour progression: Leather -&gt; Iron -&gt; Gold/Chain -&gt; Diamond -&gt; Netherite -&gt;
 * Full Plate -&gt; Infernal -&gt; Dragon. Vanilla's own defense/toughness numbers stop
 * mattering for damage the moment a piece is worn ({@code RpgEvents.onArmorAttributes}
 * strips them) - everything here is what actually reduces a hit, split into just
 * two condensed numbers per piece: Physical Protection and Magic Protection, plus
 * an Encumbrance cost that eats into a mage's cast speed and spell damage
 * (see {@code StatFormulas#encumbranceCastTimeMult}/{@code encumbranceSpellDamageMult}).
 *
 * <p>Percentages are per-piece and simply add up across a full 4-piece set (then get
 * capped - see {@code PHYSICAL_PROTECTION_CAP}/{@code MAGIC_PROTECTION_CAP} in StatFormulas).
 */
public enum ArmorTier {

    LEATHER(new String[]{"leather"}, stats()
            .put(EquipmentSlot.HEAD, 1.2, 1.2, 1.2)
            .put(EquipmentSlot.CHEST, 3.2, 3.2, 3.2)
            .put(EquipmentSlot.LEGS, 2.4, 2.4, 2.4)
            .put(EquipmentSlot.FEET, 1.2, 1.2, 1.2)),

    IRON(new String[]{"iron"}, stats()
            .put(EquipmentSlot.HEAD, 3.0, 2.4, 1.8)
            .put(EquipmentSlot.CHEST, 8.0, 6.4, 4.8)
            .put(EquipmentSlot.LEGS, 6.0, 4.8, 3.6)
            .put(EquipmentSlot.FEET, 3.0, 2.4, 1.8)),

    // vanilla's gold AND chainmail land in the same power bracket
    GOLD_CHAIN(new String[]{"gold", "chainmail"}, stats()
            .put(EquipmentSlot.HEAD, 3.0, 1.95, 1.95)
            .put(EquipmentSlot.CHEST, 8.0, 5.2, 5.2)
            .put(EquipmentSlot.LEGS, 6.0, 3.9, 3.9)
            .put(EquipmentSlot.FEET, 3.0, 1.95, 1.95)),

    DIAMOND(new String[]{"diamond"}, stats()
            .put(EquipmentSlot.HEAD, 6.75, 4.2, 3.3)
            .put(EquipmentSlot.CHEST, 18.0, 11.2, 8.8)
            .put(EquipmentSlot.LEGS, 13.5, 8.4, 6.6)
            .put(EquipmentSlot.FEET, 6.75, 4.2, 3.3)),

    NETHERITE(new String[]{"netherite"}, stats()
            .put(EquipmentSlot.HEAD, 8.25, 5.1, 3.9)
            .put(EquipmentSlot.CHEST, 22.0, 13.6, 10.4)
            .put(EquipmentSlot.LEGS, 16.5, 10.2, 7.8)
            .put(EquipmentSlot.FEET, 8.25, 5.1, 3.9)),

    FULL_PLATE(new String[]{"rpgstats:full_plate"}, stats()
            .put(EquipmentSlot.HEAD, 13.0, 6.0, 4.5)
            .put(EquipmentSlot.CHEST, 35.0, 16.0, 12.0)
            .put(EquipmentSlot.LEGS, 26.0, 12.0, 9.0)
            .put(EquipmentSlot.FEET, 13.0, 6.0, 4.5)),

    INFERNAL(new String[]{"rpgstats:infernal"}, stats()
            .put(EquipmentSlot.HEAD, 15.0, 6.9, 5.7)
            .put(EquipmentSlot.CHEST, 39.0, 18.4, 15.2)
            .put(EquipmentSlot.LEGS, 29.0, 13.8, 11.4)
            .put(EquipmentSlot.FEET, 15.0, 6.9, 5.7)),

    DRAGON(new String[]{"rpgstats:dragon"}, stats()
            .put(EquipmentSlot.HEAD, 16.0, 7.8, 6.6)
            .put(EquipmentSlot.CHEST, 42.0, 20.8, 17.6)
            .put(EquipmentSlot.LEGS, 32.0, 15.6, 13.2)
            .put(EquipmentSlot.FEET, 16.0, 7.8, 6.6));

    /** {encumbrance, physicalProtectionPercent, magicProtectionPercent} per slot. */
    private record Piece(double encumbrance, double physical, double magic) {
    }

    private static final class Builder {
        private final Map<EquipmentSlot, Piece> map = new EnumMap<>(EquipmentSlot.class);

        Builder put(EquipmentSlot slot, double encumbrance, double physical, double magic) {
            map.put(slot, new Piece(encumbrance, physical, magic));
            return this;
        }
    }

    private static Builder stats() {
        return new Builder();
    }

    private final String[] materialNames;
    private final Map<EquipmentSlot, Piece> pieces;

    ArmorTier(String[] materialNames, Builder builder) {
        this.materialNames = materialNames;
        this.pieces = builder.map;
    }

    public double encumbrance(EquipmentSlot slot) {
        Piece p = pieces.get(slot);
        return p == null ? 0.0 : p.encumbrance();
    }

    public double physicalProtection(EquipmentSlot slot) {
        Piece p = pieces.get(slot);
        return p == null ? 0.0 : p.physical();
    }

    public double magicProtection(EquipmentSlot slot) {
        Piece p = pieces.get(slot);
        return p == null ? 0.0 : p.magic();
    }

    /** Which tier a worn piece belongs to, matched by its {@link net.minecraft.world.item.ArmorMaterial} name. */
    public static ArmorTier of(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return null;
        }
        String name = armor.getMaterial().getName();
        for (ArmorTier tier : values()) {
            for (String candidate : tier.materialNames) {
                if (candidate.equals(name)) {
                    return tier;
                }
            }
        }
        return null;
    }

    private static double sum(ServerPlayer player, java.util.function.ToDoubleBiFunction<ArmorTier, EquipmentSlot> fn) {
        double total = 0.0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ArmorTier tier = of(player.getItemBySlot(slot));
            if (tier != null) {
                total += fn.applyAsDouble(tier, slot);
            }
        }
        return total;
    }

    public static double totalEncumbrance(ServerPlayer player) {
        return sum(player, ArmorTier::encumbrance);
    }

    /** 0-1 fraction of physical damage the worn set shaves off, before any cap. */
    public static double totalPhysicalProtectionPercent(ServerPlayer player) {
        return sum(player, ArmorTier::physicalProtection);
    }

    /** 0-1 fraction of magic damage the worn set shaves off, before any cap. */
    public static double totalMagicProtectionPercent(ServerPlayer player) {
        return sum(player, ArmorTier::magicProtection);
    }
}
