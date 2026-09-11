package com.villagehousing;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HouseCommands {
    private static final Map<UUID, BlockPos> POS1 = new HashMap<>();
    private static final Map<UUID, BlockPos> POS2 = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("house")
            .then(Commands.literal("pos1").executes(HouseCommands::pos1))
            .then(Commands.literal("pos2").executes(HouseCommands::pos2))
            .then(Commands.literal("create")
                .requires(s -> s.hasPermission(2))
                .executes(c -> create(c, false))
                .then(Commands.literal("npc").executes(c -> create(c, true))))
            .then(Commands.literal("list")
                .then(Commands.argument("price", IntegerArgumentType.integer(1, 64 * 9))
                    .executes(HouseCommands::list)))
            .then(Commands.literal("unlist").executes(HouseCommands::unlist))
            .then(Commands.literal("sell").executes(HouseCommands::sell))
            .then(Commands.literal("invite")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(HouseCommands::invite)))
            .then(Commands.literal("kick").executes(HouseCommands::kick))
            .then(Commands.literal("leave").executes(HouseCommands::leave))
            .then(Commands.literal("info").executes(HouseCommands::info))
            .then(Commands.literal("scan")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 256))
                    .executes(HouseCommands::scan)))
        );
    }

    private static int pos1(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        POS1.put(p.getUUID(), p.blockPosition());
        c.getSource().sendSuccess(() -> Component.literal("House pos1 set."), false);
        return 1;
    }

    private static int pos2(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        POS2.put(p.getUUID(), p.blockPosition());
        c.getSource().sendSuccess(() -> Component.literal("House pos2 set."), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> c, boolean npc) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        BlockPos a = POS1.get(p.getUUID());
        BlockPos b = POS2.get(p.getUUID());
        if (a == null || b == null) {
            c.getSource().sendFailure(Component.literal("Set /house pos1 and /house pos2 first."));
            return 0;
        }
        HouseClaim claim = new HouseClaim();
        claim.dimension = level.dimension().location();
        claim.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        claim.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        claim.type = npc ? HouseClaim.Type.NPC : HouseClaim.Type.BUYABLE;
        claim.signPos = findNearbySign(level, p.blockPosition());
        claim.doorPos = findNearbyDoor(level, p.blockPosition());
        if (claim.signPos == null) {
            c.getSource().sendFailure(Component.literal("Stand next to the housing sign."));
            return 0;
        }
        HouseManager.get(level.getServer()).add(claim);
        HouseSigns.update(level, claim);
        POS1.remove(p.getUUID());
        POS2.remove(p.getUUID());
        c.getSource().sendSuccess(() -> Component.literal("House created (" + claim.type + ")."), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        HouseManager manager = HouseManager.get(level.getServer());
        HouseClaim claim = manager.byOwner(p.getUUID());
        if (claim == null) {
            c.getSource().sendFailure(Component.literal("You do not own a house."));
            return 0;
        }
        int price = IntegerArgumentType.getInteger(c, "price");
        claim.listPrice = price;
        manager.setDirty();
        HouseSigns.update(level, claim);
        c.getSource().sendSuccess(() -> Component.literal("House listed for " + price + " diamonds. Other players can right-click the sign to buy."), false);
        return 1;
    }

    private static int sell(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        HouseManager manager = HouseManager.get(level.getServer());
        HouseClaim claim = manager.byOwner(p.getUUID());
        if (claim == null) {
            c.getSource().sendFailure(Component.literal("You do not own a house."));
            return 0;
        }
        int refund = claim.purchasePrice > 0 ? claim.purchasePrice : claim.defaultPrice;
        p.addItem(new ItemStack(Items.DIAMOND, refund));
        claim.owner = null;
        claim.guest = null;
        claim.listPrice = 0;
        claim.purchasePrice = 0;
        manager.setDirty();
        HouseSigns.update(level, claim);
        int paid = refund;
        c.getSource().sendSuccess(() -> Component.literal("Sold the house back for " + paid + " diamonds (original buy price). It is For Sale again."), false);
        return 1;
    }

    private static int unlist(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        HouseManager manager = HouseManager.get(level.getServer());
        HouseClaim claim = manager.byOwner(p.getUUID());
        if (claim == null) {
            c.getSource().sendFailure(Component.literal("You do not own a house."));
            return 0;
        }
        claim.listPrice = 0;
        manager.setDirty();
        HouseSigns.update(level, claim);
        c.getSource().sendSuccess(() -> Component.literal("Listing removed."), false);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        if (p == null) return 0;
        HouseManager manager = HouseManager.get(level.getServer());
        HouseClaim claim = manager.byOwner(p.getUUID());
        if (claim == null) {
            c.getSource().sendFailure(Component.literal("You do not own a house."));
            return 0;
        }
        if (target.getUUID().equals(p.getUUID())) {
            c.getSource().sendFailure(Component.literal("You already live here."));
            return 0;
        }
        if (manager.livesSomewhere(target.getUUID())) {
            c.getSource().sendFailure(Component.literal("That player already lives in a house."));
            return 0;
        }
        if (claim.guest != null) {
            c.getSource().sendFailure(Component.literal("You already have a guest. /house kick first."));
            return 0;
        }
        claim.guest = target.getUUID();
        manager.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Invited " + target.getGameProfile().getName() + "."), false);
        target.sendSystemMessage(Component.literal("You were invited to " + p.getGameProfile().getName() + "'s house."));
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        HouseManager manager = HouseManager.get(level.getServer());
        HouseClaim claim = manager.byOwner(p.getUUID());
        if (claim == null || claim.guest == null) {
            c.getSource().sendFailure(Component.literal("No guest to kick."));
            return 0;
        }
        ServerPlayer guest = level.getServer().getPlayerList().getPlayer(claim.guest);
        claim.guest = null;
        manager.setDirty();
        if (guest != null) {
            guest.sendSystemMessage(Component.literal("You were removed from " + p.getGameProfile().getName() + "'s house."));
        }
        c.getSource().sendSuccess(() -> Component.literal("Guest removed."), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        HouseManager manager = HouseManager.get(level.getServer());
        HouseClaim claim = manager.byResident(p.getUUID());
        if (claim == null) {
            c.getSource().sendFailure(Component.literal("You do not live in a house."));
            return 0;
        }
        if (claim.isOwner(p.getUUID())) {
            c.getSource().sendFailure(Component.literal("Owners must /house list and sell, they cannot just leave."));
            return 0;
        }
        claim.guest = null;
        manager.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("You left the house."), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        HouseClaim claim = HouseManager.get(level.getServer()).at(level.dimension().location(), p.blockPosition());
        if (claim == null) {
            c.getSource().sendSuccess(() -> Component.literal("Not inside a house claim."), false);
            return 1;
        }
        String owner = claim.owner == null ? "none" : claim.owner.toString().substring(0, 8);
        String guest = claim.guest == null ? "none" : claim.guest.toString().substring(0, 8);
        c.getSource().sendSuccess(() -> Component.literal("House " + claim.type + " owner=" + owner + " guest=" + guest
                + " listed=" + claim.listPrice + " purchasePrice=" + claim.purchasePrice), false);
        return 1;
    }

    private static int scan(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        ServerLevel level = c.getSource().getLevel();
        if (p == null) return 0;
        int radius = IntegerArgumentType.getInteger(c, "radius");
        HouseManager manager = HouseManager.get(level.getServer());
        int created = 0;
        int skipped = 0;
        BlockPos origin = p.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
            origin.offset(-radius, -8, -radius),
            origin.offset(radius, 8, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)) continue;
            if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;

            // skip if already claimed
            if (manager.at(level.dimension().location(), pos) != null) continue;

            BlockPos signSpot = placeSignBesideDoor(level, pos, state);
            if (signSpot == null) {
                skipped++;
                continue; // no room for a sign - don't create a claim nobody can interact with
            }

            HouseClaim claim = new HouseClaim();
            claim.dimension = level.dimension().location();
            claim.doorPos = pos.immutable();
            claim.min = pos.offset(-4, -1, -4);
            claim.max = pos.offset(4, 6, 4);
            claim.type = (created % 3 == 0) ? HouseClaim.Type.NPC : HouseClaim.Type.BUYABLE;
            claim.signPos = signSpot;
            manager.add(claim);
            HouseSigns.update(level, claim);
            created++;
        }
        int total = created;
        int totalSkipped = skipped;
        c.getSource().sendSuccess(() -> Component.literal("Created " + total + " house claims near you (every 3rd is NPC)."
                + (totalSkipped > 0 ? " " + totalSkipped + " doors skipped - no room for a sign." : "")), true);
        return created;
    }

    private static BlockPos findNearbySign(ServerLevel level, BlockPos around) {
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-3, -2, -3), around.offset(3, 2, 3))) {
            if (level.getBlockEntity(p) instanceof SignBlockEntity) {
                return p.immutable();
            }
        }
        return null;
    }

    private static BlockPos findNearbyDoor(ServerLevel level, BlockPos around) {
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-4, -2, -4), around.offset(4, 3, 4))) {
            if (level.getBlockState(p).getBlock() instanceof DoorBlock) return p.immutable();
        }
        return null;
    }

    /** Tries the 4 spots immediately beside the door (both sides, one block out) for a sign; places an oak sign and returns its position, or null if none fit. */
    private static BlockPos placeSignBesideDoor(ServerLevel level, BlockPos door, BlockState doorState) {
        Direction facing = doorState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos[] candidates = new BlockPos[]{
                door.relative(facing).relative(facing.getClockWise()),
                door.relative(facing).relative(facing.getCounterClockWise()),
                door.relative(facing.getOpposite()).relative(facing.getClockWise()),
                door.relative(facing.getOpposite()).relative(facing.getCounterClockWise())
        };
        for (BlockPos spot : candidates) {
            if (level.getBlockState(spot).isAir() && !level.getBlockState(spot.below()).isAir()) {
                level.setBlock(spot, Blocks.OAK_SIGN.defaultBlockState(), 3);
                return spot.immutable();
            }
        }
        return null;
    }
}
