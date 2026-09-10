package net.robmc.rpgstats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.robmc.rpgstats.item.SkillItem;
import net.robmc.rpgstats.item.Weapons;
import net.robmc.rpgstats.magic.SpellCasting;
import net.robmc.rpgstats.registry.RpgItems;
import net.robmc.rpgstats.skill.RecallManager;
import net.robmc.rpgstats.skill.RestManager;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wires the RPG stat system into gameplay: initialises new players, keeps their
 * attributes in sync, ticks natural regen, scales combat damage to the ~300-450
 * pool, and awards stat XP for the matching actions.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID)
public class RpgEvents {

    /** Per-player last position, to measure distance travelled for Quickness. */
    private static final Map<UUID, double[]> lastPos = new HashMap<>();
    /** Per-player swing state last tick, for edge-detecting a fresh melee swing. */
    private static final Map<UUID, Boolean> wasSwinging = new HashMap<>();
    /** Game time of a player's last landed melee hit, so a hit isn't also charged as a whiffed swing. */
    private static final Map<UUID, Long> lastMeleeHitTick = new HashMap<>();

    // vanilla's base weapon-attribute modifier UUIDs (they're protected on Item)
    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    // --- lifecycle ---

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgManager.ensureInitialised(player);
            RpgManager.applyAttributes(player);
            RpgManager.sync(player);
            RpgManager.syncSpellBar(player);
            grantSkillItems(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SpellCasting.tick(event.getServer());
            net.robmc.rpgstats.magic.BleedManager.tick(event.getServer());
            net.robmc.rpgstats.magic.BurnManager.tick(event.getServer());
            net.robmc.rpgstats.magic.FireFieldManager.tick(event.getServer());
            net.robmc.rpgstats.magic.DiseaseManager.tick(event.getServer());
            net.robmc.rpgstats.magic.Afflictions.tick(event.getServer());
            net.robmc.rpgstats.magic.HymnBuff.tick(event.getServer());
            net.robmc.rpgstats.magic.Silence.tick(event.getServer());
            net.robmc.rpgstats.magic.FearManager.tick(event.getServer());
            net.robmc.rpgstats.magic.WindChannel.tick(event.getServer());
            net.robmc.rpgstats.magic.FireMark.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerStats s = RpgManager.stats(player);
            s.setStamina(StatFormulas.maxStamina(s));
            s.setMana(StatFormulas.maxMana(s));
            RpgManager.applyAttributes(player);
            RpgManager.sync(player);
            RpgManager.syncSpellBar(player);
            grantSkillItems(player);
        }
    }

    // --- resource-pack weapons (CustomModelData iron sword / stick) get the right stats ---

    @SubscribeEvent
    public static void onWeaponAttributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND) {
            return;
        }
        // our own DaggerItem/PolearmItem already carry the right modifiers
        Weapons.Kind kind = Weapons.kind(event.getItemStack());
        int cmd = Weapons.customModelData(event.getItemStack());
        if (cmd != Weapons.CMD_DAGGER && cmd != Weapons.CMD_POLEARM) {
            return;
        }
        event.clearModifiers();
        if (kind == Weapons.Kind.DAGGER) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    ATTACK_DAMAGE_UUID, "Weapon modifier", 3.0, AttributeModifier.Operation.ADDITION));
            event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(
                    ATTACK_SPEED_UUID, "Weapon modifier", -1.6, AttributeModifier.Operation.ADDITION));
        } else if (kind == Weapons.Kind.POLEARM) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    ATTACK_DAMAGE_UUID, "Weapon modifier", 5.0, AttributeModifier.Operation.ADDITION));
            event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(
                    ATTACK_SPEED_UUID, "Weapon modifier", -3.0, AttributeModifier.Operation.ADDITION));
            event.addModifier(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                    UUID.fromString("b7d5c1a0-0003-4a00-8000-000000000003"),
                    "Polearm reach", 1.5, AttributeModifier.Operation.ADDITION));
        }
    }

    // --- two-handed weapons: never in the offhand, and clear the offhand while wielded ---

    private static void enforceTwoHanded(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        ItemStack main = player.getMainHandItem();
        boolean offIsTwoHanded = Weapons.isTwoHanded(off);
        boolean mainIsTwoHanded = Weapons.isTwoHanded(main);

        if (offIsTwoHanded) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            // put it in the main hand if that's free, else back into the pack
            if (main.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, off);
            } else if (!player.getInventory().add(off)) {
                player.drop(off, false);
            }
            player.displayClientMessage(
                    Component.literal("That weapon needs both hands.").withStyle(ChatFormatting.GRAY), true);
        } else if (mainIsTwoHanded && !off.isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            if (!player.getInventory().add(off)) {
                player.drop(off, false);
            }
        }
    }

    // --- general-skill hotbar items ---

    private static void grantSkillItems(ServerPlayer player) {
        giveIfMissing(player, RpgItems.SKILL_REST.get());
        giveIfMissing(player, RpgItems.SKILL_RECALL.get());
    }

    private static void giveIfMissing(ServerPlayer player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                return;
            }
        }
        if (!player.getInventory().add(new ItemStack(item))) {
            player.drop(new ItemStack(item), false); // inventory somehow full - drop at feet
        }
    }

    @SubscribeEvent
    public static void onSkillItemToss(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() instanceof SkillItem) {
            event.setCanceled(true);
            event.getPlayer().getInventory().add(event.getEntity().getItem());
        }
    }

    @SubscribeEvent
    public static void onSkillItemDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            event.getDrops().removeIf(e -> e.getItem().getItem() instanceof SkillItem);
        }
    }

    /** A Fire Magic kill cooks the drops, same as Fire Aspect (raw pork -> cooked, etc.). */
    @SubscribeEvent
    public static void onFireKillCooksDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        boolean fireKill = net.robmc.rpgstats.magic.FireMark.isMarked(event.getEntity().getId(), level.getGameTime())
                || event.getSource().is(DamageTypeTags.IS_FIRE);
        if (!fireKill) {
            return;
        }
        var recipes = level.getRecipeManager();
        var access = level.registryAccess();
        for (net.minecraft.world.entity.item.ItemEntity drop : event.getDrops()) {
            ItemStack in = drop.getItem();
            var smelted = recipes.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                    new net.minecraft.world.SimpleContainer(in), level);
            if (smelted.isPresent()) {
                ItemStack out = smelted.get().getResultItem(access).copy();
                if (!out.isEmpty()) {
                    out.setCount(in.getCount());
                    drop.setItem(out);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // Stats live in RpgData keyed by UUID, so nothing to copy - just refresh.
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            RpgManager.applyAttributes(player);
        }
    }

    // --- regen + movement tick ---

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        // distance travelled since last tick (for Quickness XP and movement stamina cost)
        double[] prev = lastPos.get(player.getUUID());
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean moved = false;
        if (prev != null) {
            double dist = Math.sqrt(Math.pow(x - prev[0], 2) + Math.pow(y - prev[1], 2) + Math.pow(z - prev[2], 2));
            if (dist > 0.05 && dist < 20) { // ignore teleports and jitter
                moved = true;
                boolean inWater = player.isInWater() || player.isSwimming();
                double rate = inWater ? StatFormulas.XP_SWIM_PER_METRE : StatFormulas.XP_MOVE_PER_METRE;
                RpgManager.addXp(player, Stat.QUICKNESS, dist * rate);
            }
        }
        lastPos.put(player.getUUID(), new double[]{x, y, z});

        // Hunger is out of the picture: never let it block sprinting, never let it self-heal.
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() != StatFormulas.PINNED_FOOD_LEVEL) {
            food.setFoodLevel(StatFormulas.PINNED_FOOD_LEVEL);
        }
        food.setExhaustion(0.0f);

        // Moving on foot drains stamina - a trickle for walking, much more for sprinting.
        PlayerStats s = RpgManager.stats(player);
        boolean onFoot = player.onGround() || player.isInWater();
        if (moved && onFoot && s.getStamina() > 0) {
            double cost = player.isSprinting()
                    ? StatFormulas.STAMINA_SPRINT_PER_TICK
                    : StatFormulas.STAMINA_WALK_PER_TICK;
            s.setStamina(s.getStamina() - cost);
            if (player.tickCount % 4 == 0) {
                RpgManager.sync(player);
            }
        }
        if (player.isSprinting() && s.getStamina() <= 0) {
            player.setSprinting(false); // out of gas
        }

        Exhaustion.update(player, s.getStamina(), StatFormulas.maxStamina(s));
        enforceTwoHanded(player);

        // Swinging your arm in combat costs stamina, hit or miss. Mining (crosshair on
        // a nearby block) doesn't count, and a landed hit is charged in onLivingHurt instead.
        boolean swinging = player.swinging;
        if (swinging && !wasSwinging.getOrDefault(player.getUUID(), false)) {
            long now = player.serverLevel().getGameTime();
            boolean justHit = now == lastMeleeHitTick.getOrDefault(player.getUUID(), -1L);
            if (!justHit && s.getStamina() > 0
                    && player.pick(4.5, 1.0f, false).getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                s.setStamina(s.getStamina() - StatFormulas.STAMINA_MELEE_SWING);
                RpgManager.sync(player);
            }
        }
        wasSwinging.put(player.getUUID(), swinging);

        RestManager.tick(player);
        RecallManager.tick(player);

        // Resting runs its own (faster) regen; skip the normal pass so they don't stack.
        if (!RestManager.isResting(player.getUUID())
                && player.tickCount % StatFormulas.REGEN_INTERVAL_TICKS == 0) {
            RpgManager.regenTick(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        RestManager.clear(id);
        RecallManager.clear(id);
        Exhaustion.clear(id);
        net.robmc.rpgstats.magic.HymnBuff.clear(id);
        net.robmc.rpgstats.magic.Silence.clear(id);
        net.robmc.rpgstats.magic.WindChannel.stop(id, event.getEntity().getServer());
        lastPos.remove(id);
        wasSwinging.remove(id);
        lastMeleeHitTick.remove(id);
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RestManager.stop(player, "You get up.");
        if (Exhaustion.is(player)) {
            // too exhausted to jump - kill the upward velocity the jump just applied
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x, Math.min(dm.y, 0.0), dm.z);
            player.hurtMarked = true; // force the corrected velocity to the client
            return;
        }
        PlayerStats s = RpgManager.stats(player);
        if (s.getStamina() > 0) {
            s.setStamina(s.getStamina() - StatFormulas.STAMINA_JUMP);
            RpgManager.sync(player);
        }
    }

    // --- exhausted: can't fight ---

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Exhaustion.is(player)) {
            event.setCanceled(true);
            player.displayClientMessage(exhaustedMsg(), true);
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Exhaustion.is(player)) {
            event.setCanceled(true);
            player.displayClientMessage(exhaustedMsg(), true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Exhaustion.is(player)) {
            return;
        }
        var item = event.getItem().getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof ShieldItem) {
            event.setCanceled(true);
            player.displayClientMessage(exhaustedMsg(), true);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Exhaustion.is(player)) {
            event.setCanceled(true); // no parry while exhausted - the hit lands in full
        }
    }

    private static Component exhaustedMsg() {
        return Component.literal("Too exhausted.").withStyle(ChatFormatting.RED);
    }

    // --- combat: scale damage to the pool, apply stat multipliers, award XP ---

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        float amount = event.getAmount();
        boolean magic = event.getSource().is(DamageTypes.INDIRECT_MAGIC) || event.getSource().is(DamageTypes.MAGIC);
        boolean projectile = event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                || event.getSource().getDirectEntity() instanceof Projectile;
        boolean fromAttacker = event.getSource().getEntity() != null;

        if (!magic && event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.isAlive()) {
            PlayerStats as = RpgManager.stats(attacker);
            if (projectile) {
                amount *= (float) StatFormulas.rangedDamageMultiplier(as);
                RpgManager.addXp(attacker, Stat.DEXTERITY, StatFormulas.XP_RANGED_HIT);
            } else if (event.getSource().getDirectEntity() == attacker) {
                amount *= (float) StatFormulas.meleeDamageMultiplier(as);
                RpgManager.addXp(attacker, Stat.STRENGTH, StatFormulas.XP_MELEE_HIT);
                RpgManager.addXp(attacker, Stat.VITALITY, StatFormulas.XP_MELEE_HIT * 0.6);
                // any landed melee hit (fist or weapon) costs stamina
                as.setStamina(as.getStamina() - StatFormulas.STAMINA_MELEE_SWING);
                lastMeleeHitTick.put(attacker.getUUID(), attacker.serverLevel().getGameTime());
                RpgManager.sync(attacker);
            }
        }

        // Everything hitting a player is scaled to the big pool; Dexterity shaves a little off spells.
        if (event.getEntity() instanceof ServerPlayer victim) {
            amount *= (float) StatFormulas.DAMAGE_SCALE;
            if (magic) {
                amount *= (float) (1.0 - StatFormulas.spellDamageResist(RpgManager.stats(victim)));
            }
            SpellCasting.interrupt(victim); // taking a hit breaks your cast
            RestManager.stop(victim, "Knocked out of your rest!");
            RecallManager.cancel(victim, "Recall interrupted!");

            // A slice of any hit from a mob or player also bleeds your other pools.
            if (fromAttacker && amount > 0) {
                double leech = amount * StatFormulas.DAMAGE_POOL_LEECH_FRACTION;
                double staminaShare = magic ? StatFormulas.LEECH_MAGIC_STAMINA
                        : projectile ? StatFormulas.LEECH_PROJECTILE_STAMINA
                        : StatFormulas.LEECH_PHYSICAL_STAMINA;
                PlayerStats vs = RpgManager.stats(victim);
                vs.setStamina(vs.getStamina() - leech * staminaShare);
                vs.setMana(vs.getMana() - leech * (1.0 - staminaShare));
                RpgManager.sync(victim);
            }
        }

        event.setAmount(amount);
    }

    // --- gathering XP ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        var state = event.getState();
        if (state.is(BlockTags.LOGS)) {
            boolean axe = player.getMainHandItem().getItem() instanceof AxeItem;
            RpgManager.addXp(player, axe ? Stat.VITALITY : Stat.STRENGTH, StatFormulas.XP_CHOP_LOG);
        } else if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            RpgManager.addXp(player, Stat.WISDOM, StatFormulas.XP_HARVEST_CROP);
        } else {
            RpgManager.addXp(player, Stat.STRENGTH, StatFormulas.XP_MINE_BLOCK);
        }
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgManager.addXp(player, Stat.WISDOM, StatFormulas.XP_FISH_CATCH);
        }
    }
}
