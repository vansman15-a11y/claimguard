package net.robmc.rpgstats.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.robmc.claimguard.network.ActivateSkillPacket;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.rpgstats.skill.Skill;

import java.util.List;

/**
 * A general skill you hold in the hotbar. Right-click to activate it (Rest toggles;
 * Recall asks first, then channels). Can't be dropped or destroyed - it comes back
 * on relog if lost.
 */
public class SkillItem extends Item {

    private final Skill skill;

    public SkillItem(Skill skill, Properties properties) {
        super(properties.stacksTo(1).fireResistant());
        this.skill = skill;
    }

    public Skill skill() {
        return skill;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            activateClient();
        }
        return InteractionResultHolder.success(held);
    }

    @OnlyIn(Dist.CLIENT)
    private void activateClient() {
        net.robmc.rpgstats.client.item.SkillItemUse.activate(skill);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int lvl = net.robmc.rpgstats.client.magic.ClientSpells.skillLevel(skill);
        tooltip.add(Component.literal("General skill  -  Lv " + lvl).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(skill.blurb()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Right-click to use").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Server-safe helper: sends the activate packet (client only). */
    public static void sendActivate(Skill skill) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new ActivateSkillPacket(skill.name()));
    }
}
