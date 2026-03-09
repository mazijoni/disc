package com.customdiscs.network;

import com.customdiscs.DiscMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DiscMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, DiscCraftPacket.class,
                DiscCraftPacket::encode, DiscCraftPacket::decode, DiscCraftPacket::handle);
        CHANNEL.registerMessage(id++, DiscResponsePacket.class,
                DiscResponsePacket::encode, DiscResponsePacket::decode, DiscResponsePacket::handle);
        CHANNEL.registerMessage(id++, DiscPlayPacket.class,
                DiscPlayPacket::encode, DiscPlayPacket::decode, DiscPlayPacket::handle);
        CHANNEL.registerMessage(id++, OggUploadPacket.class,
                OggUploadPacket::encode, OggUploadPacket::decode, OggUploadPacket::handle);
    }
}
