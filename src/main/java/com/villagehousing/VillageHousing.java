package com.villagehousing;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(VillageHousing.MODID)
public class VillageHousing {
    public static final String MODID = "villagehousing";

    public VillageHousing() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new HouseEvents());
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        HouseCommands.register(event.getDispatcher());
    }
}
