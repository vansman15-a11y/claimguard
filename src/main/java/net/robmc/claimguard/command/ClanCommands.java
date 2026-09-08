package net.robmc.claimguard.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.ClaimGuard;
import net.robmc.claimguard.clan.ClanActions;

/**
 * Clan commands:
 *   /clan                - open your clan's roster screen
 *   /signature <player>  - (charter owner) ask a player to sign
 *   /accept /deny /block - (target) respond to a pending request; /block also
 *                          stops that requester from asking again
 */
@Mod.EventBusSubscriber(modid = ClaimGuard.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClanCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("clan")
                .executes(ctx -> {
                    ClanActions.openRoster(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ClanActions.invitePlayer(
                                            ctx.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(ctx, "player"));
                                    return 1;
                                })))
                .then(Commands.literal("accept").executes(ctx -> {
                    ClanActions.acceptInvite(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("decline").executes(ctx -> {
                    ClanActions.declineInvite(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("bans").executes(ctx -> {
                    ClanActions.openBanList(ctx.getSource().getPlayerOrException());
                    return 1;
                })));

        dispatcher.register(Commands.literal("signature")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer requester = ctx.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            ClanActions.requestSignature(requester, target);
                            return 1;
                        })));

        dispatcher.register(Commands.literal("accept").executes(ctx -> {
            ClanActions.acceptSignature(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        dispatcher.register(Commands.literal("deny").executes(ctx -> {
            ClanActions.denySignature(ctx.getSource().getPlayerOrException());
            return 1;
        }));

        dispatcher.register(Commands.literal("block").executes(ctx -> {
            ClanActions.blockSignature(ctx.getSource().getPlayerOrException());
            return 1;
        }));
    }
}
