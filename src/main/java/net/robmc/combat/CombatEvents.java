package net.robmc.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Server-side glue for Reforged Melee: slower swings, the cleave arc, and the combo crit. */
@Mod.EventBusSubscriber(modid = CombatMod.MODID)
public final class CombatEvents {

    private static final UUID SWING_SLOWDOWN_ID = UUID.fromString("c0dea7e5-0001-4a00-8000-0000000000a1");

    private CombatEvents() {
    }

    public static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem;
    }

    // --- slower swings ---

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        applySlowdown(event.getEntity() instanceof ServerPlayer p ? p : null);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        applySlowdown(event.getEntity() instanceof ServerPlayer p ? p : null);
    }

    private static void applySlowdown(ServerPlayer player) {
        if (player == null) {
            return;
        }
        AttributeInstance atk = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atk == null) {
            return;
        }
        atk.removeModifier(SWING_SLOWDOWN_ID);
        atk.addPermanentModifier(new AttributeModifier(SWING_SLOWDOWN_ID, "combat_swing_slowdown",
                -CombatConfig.SWING_SLOWDOWN, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // --- cleave + combo ---

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isMeleeWeapon(player.getMainHandItem())) {
            return;
        }
        LivingEntity primary = event.getTarget() instanceof LivingEntity le ? le : null;
        MeleeArc.sweep(player, primary, true); // the vanilla primary hit is about to land
    }

    /** Runs after every other damage modifier so the crit multiplies the final number. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (MeleeArc.isApplyingArcHit()) {
            return; // the cleave already baked in the crit
        }
        var source = event.getSource();
        if (source.getEntity() != source.getDirectEntity() || !(source.getEntity() instanceof ServerPlayer player)) {
            return; // not a direct melee hit
        }
        if (!ComboTracker.isCritThisSwing(player)) {
            return;
        }
        event.setAmount(event.getAmount() * (float) CombatConfig.COMBO_CRIT_MULT);
        MeleeArc.critFx(player, event.getEntity());
    }

    // --- lifecycle ---

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ComboTracker.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ComboTracker.clear(event.getEntity().getUUID());
    }
}
