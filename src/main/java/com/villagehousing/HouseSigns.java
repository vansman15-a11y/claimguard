package com.villagehousing;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

public class HouseSigns {
    public static void update(ServerLevel level, HouseClaim claim) {
        if (claim.signPos == null) return;
        BlockEntity be = level.getBlockEntity(claim.signPos);
        if (!(be instanceof SignBlockEntity sign)) return;

        String line0;
        String line1 = "";
        String line2 = "";
        String line3 = "";

        if (claim.type == HouseClaim.Type.NPC) {
            line0 = "<Villager>";
            line1 = "Home";
        } else if (claim.owner == null) {
            line0 = "<For Sale>";
            line1 = claim.defaultPrice + " Diamonds";
        } else if (claim.isListed()) {
            line0 = "<For Sale>";
            line1 = claim.listPrice + " Diamonds";
            line2 = "by " + name(level, claim.owner);
        } else {
            line0 = "<Owned By";
            line1 = name(level, claim.owner) + ">";
        }

        SignText text = sign.getText(true);
        text = text.setMessage(0, Component.literal(trim(line0)));
        text = text.setMessage(1, Component.literal(trim(line1)));
        text = text.setMessage(2, Component.literal(trim(line2)));
        text = text.setMessage(3, Component.literal(trim(line3)));
        sign.setText(text, true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(claim.signPos, sign.getBlockState(), sign.getBlockState(), 3);
    }

    private static String name(ServerLevel level, java.util.UUID uuid) {
        var profile = level.getServer().getProfileCache().get(uuid);
        return profile.map(p -> p.getName()).orElse(uuid.toString().substring(0, 8));
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > 15 ? s.substring(0, 15) : s;
    }
}
