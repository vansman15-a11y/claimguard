package net.robmc.claimguard.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.robmc.claimguard.clan.Clan;
import net.robmc.claimguard.clan.ClanActions;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A "Clan Charter" - a book-and-quill lookalike used to found a clan. The first
 * time the owner right-clicks it, it records them as the owner and adds their own
 * signature. They then gather more signatures via /signature; once it has
 * {@link Clan#REQUIRED_SIGNATURES}, right-clicking opens the Create Clan screen.
 */
public class ClanCharterItem extends Item {

    private static final String TAG_OWNER = "CharterOwner";
    private static final String TAG_OWNER_NAME = "CharterOwnerName";
    private static final String TAG_SIGNATURES = "Signatures";
    private static final String SIG_ID = "Id";
    private static final String SIG_NAME = "Name";

    public ClanCharterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        ClanActions.useCharter(serverPlayer, stack);
        return InteractionResultHolder.success(stack);
    }

    // --- charter NBT helpers ---

    @Nullable
    public static UUID getOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getString(TAG_OWNER_NAME) : "";
    }

    public static void setOwner(ItemStack stack, UUID id, String name) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_OWNER, id);
        tag.putString(TAG_OWNER_NAME, name);
    }

    /** Ordered map of signer id -> signer name. */
    public static Map<UUID, String> getSignatures(ItemStack stack) {
        Map<UUID, String> out = new LinkedHashMap<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        ListTag list = tag.getList(TAG_SIGNATURES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            out.put(entry.getUUID(SIG_ID), entry.getString(SIG_NAME));
        }
        return out;
    }

    public static boolean hasSigned(ItemStack stack, UUID id) {
        return getSignatures(stack).containsKey(id);
    }

    public static int signatureCount(ItemStack stack) {
        return getSignatures(stack).size();
    }

    public static void addSignature(ItemStack stack, UUID id, String name) {
        if (hasSigned(stack, id)) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = tag.getList(TAG_SIGNATURES, Tag.TAG_COMPOUND);
        CompoundTag entry = new CompoundTag();
        entry.putUUID(SIG_ID, id);
        entry.putString(SIG_NAME, name);
        list.add(entry);
        tag.put(TAG_SIGNATURES, list);
    }

    public static boolean isReady(ItemStack stack) {
        return signatureCount(stack) >= Clan.REQUIRED_SIGNATURES;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        UUID owner = getOwner(stack);
        if (owner == null) {
            tooltip.add(Component.literal("Right-click to begin a charter").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.literal("Charter of " + getOwnerName(stack)).withStyle(ChatFormatting.GRAY));
        Map<UUID, String> sigs = getSignatures(stack);
        tooltip.add(Component.literal("Signatures: " + sigs.size() + " / " + Clan.REQUIRED_SIGNATURES)
                .withStyle(isReady(stack) ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        for (String name : sigs.values()) {
            tooltip.add(Component.literal("  - " + name).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (!isReady(stack)) {
            tooltip.add(Component.literal("Use /signature <player> to gather more").withStyle(ChatFormatting.GRAY));
        }
    }
}
