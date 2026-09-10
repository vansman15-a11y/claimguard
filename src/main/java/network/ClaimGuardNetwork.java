package net.robmc.claimguard.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.robmc.claimguard.ClaimGuard;

public class ClaimGuardNetwork {

    /**
     * Bump this whenever a packet's fields change. A client and server on different
     * versions then fail the connection with a clear "incompatible" message instead
     * of mis-parsing a packet and crashing mid-game.
     */
    private static final String PROTOCOL_VERSION = "27";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ClaimGuard.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextPacketId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                nextPacketId++,
                TerritoryTitlePacket.class,
                TerritoryTitlePacket::encode,
                TerritoryTitlePacket::decode,
                TerritoryTitlePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ShowClaimBorderPacket.class,
                ShowClaimBorderPacket::encode,
                ShowClaimBorderPacket::decode,
                ShowClaimBorderPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClaimMenuPacket.class,
                OpenClaimMenuPacket::encode,
                OpenClaimMenuPacket::decode,
                OpenClaimMenuPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClaimActionPacket.class,
                ClaimActionPacket::encode,
                ClaimActionPacket::decode,
                ClaimActionPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenCreateClanScreenPacket.class,
                OpenCreateClanScreenPacket::encode,
                OpenCreateClanScreenPacket::decode,
                OpenCreateClanScreenPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                CreateClanPacket.class,
                CreateClanPacket::encode,
                CreateClanPacket::decode,
                CreateClanPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClanRosterPacket.class,
                OpenClanRosterPacket::encode,
                OpenClanRosterPacket::decode,
                OpenClanRosterPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClanMemberActionPacket.class,
                ClanMemberActionPacket::encode,
                ClanMemberActionPacket::decode,
                ClanMemberActionPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClanInvitePacket.class,
                ClanInvitePacket::encode,
                ClanInvitePacket::decode,
                ClanInvitePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClanBanListRequestPacket.class,
                OpenClanBanListRequestPacket::encode,
                OpenClanBanListRequestPacket::decode,
                OpenClanBanListRequestPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClanBanListPacket.class,
                OpenClanBanListPacket::encode,
                OpenClanBanListPacket::decode,
                OpenClanBanListPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClanUnbanPacket.class,
                ClanUnbanPacket::encode,
                ClanUnbanPacket::decode,
                ClanUnbanPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SetClanMotdPacket.class,
                SetClanMotdPacket::encode,
                SetClanMotdPacket::decode,
                SetClanMotdPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenRespawnChoicePacket.class,
                OpenRespawnChoicePacket::encode,
                OpenRespawnChoicePacket::decode,
                OpenRespawnChoicePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                RespawnChoicePacket.class,
                RespawnChoicePacket::encode,
                RespawnChoicePacket::decode,
                RespawnChoicePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                ClanLeavePacket.class,
                ClanLeavePacket::encode,
                ClanLeavePacket::decode,
                ClanLeavePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClanBrowseRequestPacket.class,
                OpenClanBrowseRequestPacket::encode,
                OpenClanBrowseRequestPacket::decode,
                OpenClanBrowseRequestPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                OpenClanBrowsePacket.class,
                OpenClanBrowsePacket::encode,
                OpenClanBrowsePacket::decode,
                OpenClanBrowsePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SetClanRelationPacket.class,
                SetClanRelationPacket::encode,
                SetClanRelationPacket::decode,
                SetClanRelationPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                StartSiegePacket.class,
                StartSiegePacket::encode,
                StartSiegePacket::decode,
                StartSiegePacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncAlliesPacket.class,
                SyncAlliesPacket::encode,
                SyncAlliesPacket::decode,
                SyncAlliesPacket::handle
        );
        CHANNEL.registerMessage(
                nextPacketId++,
                SyncRpgStatsPacket.class,
                SyncRpgStatsPacket::encode,
                SyncRpgStatsPacket::decode,
                SyncRpgStatsPacket::handle
        );
        CHANNEL.registerMessage(nextPacketId++, CastSpellPacket.class,
                CastSpellPacket::encode, CastSpellPacket::decode, CastSpellPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, SetSpellSlotPacket.class,
                SetSpellSlotPacket::encode, SetSpellSlotPacket::decode, SetSpellSlotPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, CastStatePacket.class,
                CastStatePacket::encode, CastStatePacket::decode, CastStatePacket::handle);
        CHANNEL.registerMessage(nextPacketId++, SyncSpellBarPacket.class,
                SyncSpellBarPacket::encode, SyncSpellBarPacket::decode, SyncSpellBarPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, SpellCooldownPacket.class,
                SpellCooldownPacket::encode, SpellCooldownPacket::decode, SpellCooldownPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, RecallStatePacket.class,
                RecallStatePacket::encode, RecallStatePacket::decode, RecallStatePacket::handle);
        CHANNEL.registerMessage(nextPacketId++, ActivateSkillPacket.class,
                ActivateSkillPacket::encode, ActivateSkillPacket::decode, ActivateSkillPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, StaffGlowPacket.class,
                StaffGlowPacket::encode, StaffGlowPacket::decode, StaffGlowPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, ScreenFlashPacket.class,
                ScreenFlashPacket::encode, ScreenFlashPacket::decode, ScreenFlashPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, SyncClanViewPacket.class,
                SyncClanViewPacket::encode, SyncClanViewPacket::decode, SyncClanViewPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, TargetInfoPacket.class,
                TargetInfoPacket::encode, TargetInfoPacket::decode, TargetInfoPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, BloodBlindPacket.class,
                BloodBlindPacket::encode, BloodBlindPacket::decode, BloodBlindPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, WildShapeSyncPacket.class,
                WildShapeSyncPacket::encode, WildShapeSyncPacket::decode, WildShapeSyncPacket::handle);
        CHANNEL.registerMessage(nextPacketId++, WolfLeapPacket.class,
                WolfLeapPacket::encode, WolfLeapPacket::decode, WolfLeapPacket::handle);
    }
}