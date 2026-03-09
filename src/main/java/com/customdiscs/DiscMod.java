package com.customdiscs;

import com.customdiscs.network.PacketHandler;
import com.customdiscs.registry.*;
import com.customdiscs.util.SoundRegistryHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DiscMod.MOD_ID)
public class DiscMod {
    public static final String MOD_ID = "customdiscs";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public DiscMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(SoundRegistryHelper::onRegisterSounds);

        // Client-only listeners: never register on a dedicated server.
        // registerDynamicPack touches CLIENT_RESOURCES packs; clientSetup
        // registers screens and key-bindings that do not exist server-side.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modBus.addListener(this::clientSetup);
            modBus.addListener(this::addPackFinders);
        });

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            SoundRegistryHelper.ensureFolderExists();
        });
    }

    private void clientSetup(FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientSetup::register);
    }

    private void addPackFinders(AddPackFindersEvent event) {
        SoundRegistryHelper.registerDynamicPack(event);
    }
}
