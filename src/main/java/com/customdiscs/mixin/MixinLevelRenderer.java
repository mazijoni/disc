package com.customdiscs.mixin;

import com.customdiscs.item.CustomDiscItem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * Intercepts jukebox level event 1010.
     *
     * In 1.20.1, LevelRenderer handles event 1010 like this:
     *   soundManager.stop(null, RECORDS);          // ALWAYS stops first
     *   if (data != 0) {
     *       item = Item.byId(data);
     *       if (item instanceof RecordItem)
     *           soundManager.play(item.getSound()); // plays MUSIC_DISC_13 for us!
     *   }
     *
     * We fire event 1010 with data=0 from useOn() (so vanilla skips sound),
     * and our DiscPlayPacket handler already started the real sound via
     * SimpleSoundInstance.forRecord() with proper 3D attenuation.
     *
     * - data == 0  → DON'T cancel. Vanilla calls stop(null, RECORDS) which also
     *                stops our custom sound — exactly what we want on eject.
     * - data != 0, item == CustomDiscItem  → CANCEL. Blocks MUSIC_DISC_13 from
     *   playing in edge cases where the jukebox ticker fires 1010 with item ID.
     */
    @Inject(
        method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onLevelEvent(Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type != 1010) return;
        if (data == 0) return; // Let vanilla handle: it stops RECORDS (= eject sound-stop)

        // If the item ID resolves to our custom disc, cancel so MUSIC_DISC_13 doesn't play.
        // The real sound was already started by DiscPlayPacket on the client.
        if (Item.byId(data) instanceof CustomDiscItem) {
            ci.cancel();
        }
    }
}
