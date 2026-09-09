package net.robmc.rpgstats.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * A heavy two-handed reach weapon. Hits hard and from farther away, but swings
 * slowly. Can't be held in the offhand and forces the offhand empty.
 */
public class PolearmItem extends SwordItem implements TwoHandedWeapon {

    private static final UUID REACH_MODIFIER = UUID.fromString("b7d5c1a0-0003-4a00-8000-000000000003");
    private final Multimap<Attribute, AttributeModifier> attributes;

    public PolearmItem(Properties properties) {
        // iron tier, big damage bonus, slow recovery
        super(Tiers.IRON, 3, -3.0f, properties);

        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(super.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND));
        b.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                REACH_MODIFIER, "Polearm reach", 1.5, AttributeModifier.Operation.ADDITION));
        this.attributes = b.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? attributes : super.getDefaultAttributeModifiers(slot);
    }
}
