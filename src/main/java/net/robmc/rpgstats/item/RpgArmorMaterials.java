package net.robmc.rpgstats.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.function.Supplier;

/**
 * Armour tiers for the two RPG sets. Dragon mirrors Netherite, Infernal mirrors
 * Diamond - only the look and name change. The names carry the {@code rpgstats:}
 * namespace so Forge looks for the worn layers under
 * {@code assets/rpgstats/textures/models/armor/}.
 */
public enum RpgArmorMaterials implements ArmorMaterial {

    DRAGON("rpgstats:dragon", 40, 3, 6, 8, 3, 18,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.15F, () -> Ingredient.of(Items.NETHERITE_INGOT)),
    INFERNAL("rpgstats:infernal", 37, 3, 6, 8, 3, 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, () -> Ingredient.of(Items.NETHERITE_INGOT)),
    FULL_PLATE("rpgstats:full_plate", 30, 3, 6, 8, 3, 12,
            SoundEvents.ARMOR_EQUIP_IRON, 2.0F, 0.05F, () -> Ingredient.of(Items.IRON_INGOT));

    private static final EnumMap<ArmorItem.Type, Integer> BASE_DURABILITY = new EnumMap<>(ArmorItem.Type.class);

    static {
        BASE_DURABILITY.put(ArmorItem.Type.BOOTS, 13);
        BASE_DURABILITY.put(ArmorItem.Type.LEGGINGS, 15);
        BASE_DURABILITY.put(ArmorItem.Type.CHESTPLATE, 16);
        BASE_DURABILITY.put(ArmorItem.Type.HELMET, 11);
    }

    private final String name;
    private final int durabilityMultiplier;
    private final EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repair;

    RpgArmorMaterials(String name, int durabilityMultiplier, int boots, int legs, int chest, int helmet,
                      int enchantmentValue, SoundEvent equipSound, float toughness, float knockbackResistance,
                      Supplier<Ingredient> repair) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.defense.put(ArmorItem.Type.BOOTS, boots);
        this.defense.put(ArmorItem.Type.LEGGINGS, legs);
        this.defense.put(ArmorItem.Type.CHESTPLATE, chest);
        this.defense.put(ArmorItem.Type.HELMET, helmet);
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repair = repair;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY.get(type) * durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defense.get(type);
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repair.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
