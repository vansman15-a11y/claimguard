package net.robmc.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.robmc.combat.network.CombatNetwork;
import net.robmc.combat.network.SwingCooldownPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side glue for Reforged Melee: slower swings, the cleave arc, and the combo crit. */
@Mod.EventBusSubscriber(modid = CombatMod.MODID)
public final class CombatEvents {

    private static final UUID SWING_SLOWDOWN_ID = UUID.fromString("c0dea7e5-0001-4a00-8000-0000000000a1");

    /** The tick each player last swung, for the hard global cooldown - independent of vanilla's attack-speed charge. */
    private static final Map<UUID, Long> lastSwingTick = new HashMap<>();

    private CombatEvents() {
    }

    /**
     * Gate: only lets a swing through once every {@link CombatConfig#SWING_COOLDOWN_TICKS}, no
     * matter how fast the attack button is spammed. On success, tells the client to start
     * showing the cooldown (the readout under rpgstats' Bar 1 reads this). Call this before
     * doing anything else for a swing - a click that fails this check should have zero effect.
     */
    public static boolean tryStartSwing(ServerPlayer player) {
        if (ParryManager.isParrying(player.getUUID())) {
            return false; // shield/weapon's up - can't swing while parrying
        }
        long now = player.serverLevel().getGameTime();
        // 0, not Long.MIN_VALUE - "now - Long.MIN_VALUE" overflows and wraps to a huge negative
        // number, which read as "still on cooldown" and blocked every player's very first swing
        // forever (the early return meant lastSwingTick never got set, so every later swing hit
        // the same overflow again)
        long last = lastSwingTick.getOrDefault(player.getUUID(), 0L);
        if (now - last < CombatConfig.SWING_COOLDOWN_TICKS) {
            return false;
        }
        lastSwingTick.put(player.getUUID(), now);
        var id = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());
        CombatNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SwingCooldownPacket(id != null ? id.toString() : "", CombatConfig.SWING_COOLDOWN_TICKS));
        return true;
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
        if (!tryStartSwing(player)) {
            event.setCanceled(true); // still on the hard cooldown - no damage, no knockback, nothing
            return;
        }
        LivingEntity primary = event.getTarget() instanceof LivingEntity le ? le : null;
        MeleeArc.sweep(player, primary, true); // the vanilla primary hit is about to land
    }

    /** Cancel vanilla's own shield-block entirely - ParryManager's flat percentage is the only mitigation. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem() instanceof ShieldItem) {
            event.setCanceled(true);
        }
    }

    /** Runs after every other damage modifier so the crit multiplies the final number. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer defender) {
            float reduction = ParryManager.damageReduction(defender);
            if (reduction > 0f) {
                event.setAmount(event.getAmount() * (1f - reduction));
            }
        }
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer sp) {
            ParryManager.tick(sp);
            ShieldSwapManager.tick(sp);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ComboTracker.clear(id);
        lastSwingTick.remove(id);
        ParryManager.clear(id);
        ShieldSwapManager.clear(id);
    }
}
