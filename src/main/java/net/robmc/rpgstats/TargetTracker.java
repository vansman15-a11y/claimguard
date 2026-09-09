package net.robmc.rpgstats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.TargetInfoPacket;
import net.robmc.rpgstats.magic.Afflictions;
import net.robmc.rpgstats.magic.BleedManager;
import net.robmc.rpgstats.magic.BurnManager;
import net.robmc.rpgstats.magic.DiseaseManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A few times a second, works out what each player is looking at and sends them
 * the harmful effects on it, so the client target frame can show a debuff row
 * with live countdowns. (Name and health the client already has from entity
 * tracking; only the effect list needs pushing.)
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID)
public final class TargetTracker {

    private static final int INTERVAL = 5;      // ~4 updates / second
    private static final double PICK_RANGE = 45.0;

    private TargetTracker() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            LivingEntity target = pick(player);
            int id = target == null ? -1 : target.getId();
            List<TargetInfoPacket.Debuff> debuffs = target == null ? List.of() : gather(player, target);
            ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new TargetInfoPacket(id, debuffs));
        }
    }

    /** The living entity under the player's crosshair, walls blocking line of sight. */
    private static LivingEntity pick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 far = eye.add(look.scale(PICK_RANGE));

        HitResult block = level.clip(new ClipContext(eye, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : far;
        double maxSq = eye.distanceToSqr(end);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end,
                new AABB(eye, end).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e != player && !e.isSpectator(),
                maxSq);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static List<TargetInfoPacket.Debuff> gather(ServerPlayer viewer, LivingEntity target) {
        List<TargetInfoPacket.Debuff> out = new ArrayList<>();
        long now = viewer.serverLevel().getGameTime();

        int burn = BurnManager.remainingTicks(now, target.getId());
        if (burn > 0) {
            out.add(new TargetInfoPacket.Debuff(TargetInfoPacket.KIND_BURN, 0, burn,
                    (byte) BurnManager.stacks(target.getId())));
        }
        int bleed = BleedManager.remainingTicks(now, target.getId());
        if (bleed > 0) {
            out.add(new TargetInfoPacket.Debuff(TargetInfoPacket.KIND_BLEED, 0, bleed, (byte) 0));
        }
        int disease = DiseaseManager.remainingTicks(now, target.getId());
        if (disease > 0) {
            out.add(new TargetInfoPacket.Debuff(TargetInfoPacket.KIND_DISEASE, 0, disease, (byte) 0));
        }
        int wither = Afflictions.remainingTicks(now, target.getId(), Afflictions.Kind.WITHER);
        if (wither > 0) {
            out.add(new TargetInfoPacket.Debuff(TargetInfoPacket.KIND_WITHER, 0, wither, (byte) 0));
        }
        int slump = Afflictions.remainingTicks(now, target.getId(), Afflictions.Kind.SLUMP);
        if (slump > 0) {
            out.add(new TargetInfoPacket.Debuff(TargetInfoPacket.KIND_SLUMP, 0, slump, (byte) 0));
        }
        for (MobEffectInstance mi : target.getActiveEffects()) {
            if (mi.getEffect().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            int id = BuiltInRegistries.MOB_EFFECT.getId(mi.getEffect());
            out.add(new TargetInfoPacket.Debuff(TargetInfoPacket.KIND_VANILLA, id,
                    mi.getDuration(), (byte) mi.getAmplifier()));
            if (out.size() >= 16) {
                break;
            }
        }
        return out;
    }
}
