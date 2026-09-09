package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.SyncClanViewPacket;
import net.robmc.claimguard.network.TargetInfoPacket;
import net.robmc.rpgstats.RpgStats;

import java.util.List;

/**
 * DAoC / Rise-of-Agon style target frame. Look at a mob or player and a small
 * panel appears near the top of the screen: their name (green for your clan,
 * dark green for an ally, red for anyone else), clan tag, a health bar with
 * numbers, and a row of debuff icons above it counting down.
 *
 * Movable in the J editor ({@link HudLayout#TARGET_FRAME}).
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TargetFrameOverlay {

    public static final int W = 122;
    public static final int STRIP_H = 11;  // the name / debuff row that sits on top of the bar
    public static final int BAR_H = 9;
    public static final int H = STRIP_H + BAR_H;

    private static final int PICK_RANGE = 45;
    private static final int GRACE_TICKS = 25; // keep the frame ~1.25 s after you look away

    private static final int COL_SELF = 0xFF54DD6A;
    private static final int COL_ALLY = 0xFF2F9A44;
    private static final int COL_ENEMY = 0xFFE1533E;

    private static final ResourceLocation BURN_ICON = new ResourceLocation(RpgStats.MOD_ID, "textures/gui/debuff/burn.png");
    private static final ResourceLocation BLEED_ICON = new ResourceLocation(RpgStats.MOD_ID, "textures/gui/debuff/bleed.png");

    private static LivingEntity target;
    private static long lastSeenTick;

    private TargetFrameOverlay() {
    }

    public static LivingEntity target() {
        return (target != null && target.isAlive() && !target.isRemoved()) ? target : null;
    }

    public static int defaultLeft(int screenW) {
        return (screenW - W) / 2;
    }

    public static int defaultTop(int screenH) {
        return 6;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "rpg_target_frame", FRAME);
    }

    private static final IGuiOverlay FRAME = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        LivingEntity t = target();
        if (t == null) {
            return;
        }
        int x = defaultLeft(screenW) + HudLayout.offX(HudLayout.TARGET_FRAME);
        int y = defaultTop(screenH) + HudLayout.offY(HudLayout.TARGET_FRAME);
        render(g, mc, mc.font, x, y, t);
    };

    // --- target picking ---

    @Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
    public static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                updateTarget();
            }
        }
    }

    private static void updateTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            target = null;
            return;
        }
        Entity cam = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        Vec3 eye = cam.getEyePosition(1.0f);
        Vec3 look = cam.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(PICK_RANGE));

        HitResult block = mc.level.clip(new ClipContext(eye, far,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
        Vec3 end = block.getType() != HitResult.Type.MISS ? block.getLocation() : far;
        double maxSq = eye.distanceToSqr(end);

        AABB box = cam.getBoundingBox().expandTowards(look.scale(PICK_RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(cam, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != mc.player && e != cam && !e.isSpectator(),
                maxSq);

        long now = mc.level.getGameTime();
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            target = le;
            lastSeenTick = now;
        } else if (target != null
                && (!target.isAlive() || target.isRemoved() || now - lastSeenTick > GRACE_TICKS)) {
            target = null;
        }
    }

    // --- rendering ---

    private static final int ICON = 10; // debuff icon size in the strip

    private static void render(GuiGraphics g, Minecraft mc, Font font, int x, int y, LivingEntity t) {
        int colour;
        String name;
        if (t instanceof Player p) {
            colour = switch (ClientClanView.relation(p.getUUID())) {
                case SyncClanViewPacket.SELF -> COL_SELF;
                case SyncClanViewPacket.ALLY -> COL_ALLY;
                default -> COL_ENEMY;
            };
            String tag = ClientClanView.tag(p.getUUID());
            name = (tag.isEmpty() ? "" : "[" + tag + "] ") + p.getGameProfile().getName();
        } else {
            colour = COL_ENEMY;
            name = t.getDisplayName().getString();
        }

        float hp = Math.max(0f, t.getHealth());
        float max = Math.max(1f, t.getMaxHealth());
        float absorb = t.getAbsorptionAmount();

        int barY = y + STRIP_H;

        // the name / debuff strip, sitting directly on top of the bar
        g.fill(x - 1, y, x + W + 1, barY, 0xC8000000);

        // debuffs: right-aligned in the strip, flowing left
        java.util.List<TargetInfoPacket.Debuff> debuffs = ClientTargetInfo.forEntity(t.getId());
        int dx = x + W - 1;
        int shown = 0;
        for (TargetInfoPacket.Debuff d : debuffs) {
            if (shown >= 6 || dx - (ICON + 1) < x + 1) {
                break;
            }
            dx -= ICON + 1;
            drawDebuff(g, mc, font, dx, y + (STRIP_H - ICON) / 2, d);
            shown++;
        }

        // name, clipped so it never runs under the icons
        int nameMaxW = Math.max(24, (dx - 2) - (x + 2));
        g.drawString(font, clip(font, name, nameMaxW), x + 2, y + 2, colour, true);

        // health bar
        g.fill(x - 1, barY - 1, x + W + 1, barY + BAR_H + 1, 0xFF000000);
        g.fill(x, barY, x + W, barY + BAR_H, 0xC0301010);
        int hpFill = Math.round(W * Math.min(1f, hp / max));
        g.fill(x, barY, x + hpFill, barY + BAR_H, 0xFFC0392B);
        if (absorb > 0f) {
            int aFill = Math.min(W - hpFill, Math.round(W * Math.min(1f, absorb / max)));
            g.fill(x + hpFill, barY, x + hpFill + aFill, barY + BAR_H, 0xFFE8C349);
        }
        String hpText = Mth.ceil(hp) + " / " + Mth.ceil(max) + (absorb > 0f ? " (+" + Mth.ceil(absorb) + ")" : "");
        g.drawString(font, hpText, x + (W - font.width(hpText)) / 2, barY + 1, 0xFFFFFFFF, true);
    }

    private static void drawDebuff(GuiGraphics g, Minecraft mc, Font font, int x, int y, TargetInfoPacket.Debuff d) {
        if (d.kind == TargetInfoPacket.KIND_VANILLA) {
            MobEffect eff = BuiltInRegistries.MOB_EFFECT.byId(d.id);
            if (eff != null) {
                TextureAtlasSprite sprite = mc.getMobEffectTextures().get(eff);
                g.blit(x, y, 0, ICON, ICON, sprite);
            } else {
                g.fill(x, y, x + ICON, y + ICON, 0xFF503050);
            }
        } else if (d.kind == TargetInfoPacket.KIND_BURN) {
            g.blit(BURN_ICON, x, y, ICON, ICON, 0f, 0f, 16, 16, 16, 16);
            if (d.stacks > 1) {
                tiny(g, font, Integer.toString(d.stacks), x - 0.5f, y - 1.0f, 0xFFFFE0A0);
            }
        } else {
            g.blit(BLEED_ICON, x, y, ICON, ICON, 0f, 0f, 16, 16, 16, 16);
        }

        int secs = ClientTargetInfo.secondsLeft(d);
        if (secs > 0) {
            String s = Integer.toString(secs);
            tiny(g, font, s, x + ICON - font.width(s) * 0.6f, y + ICON - 4f, 0xFFFFFFFF);
        }
    }

    private static void tiny(GuiGraphics g, Font font, String s, float x, float y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(font, s, 0, 0, colour, true);
        g.pose().popPose();
    }

    private static String clip(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        while (s.length() > 1 && font.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
