package com.customdiscs.compat.create;

import com.customdiscs.block.TrainSpeakerBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the Train Speaker block a valid Display Link target.
 * When a Display Link connects a Train Station (or any other source) to this block,
 * the received text lines are parsed and used as the stop schedule.
 *
 * <p>The first line received is treated as the station name.
 * Subsequent lines become the stop list.
 * Every time the Display Link refreshes, if the text has changed, an announcement is triggered.</p>
 */
public class TrainSpeakerDisplayTarget extends DisplayTarget {

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        BlockEntity be = context.getTargetBlockEntity();
        if (!(be instanceof TrainSpeakerBlockEntity speaker)) return;

        // Collect all non-empty lines
        List<String> lines = new ArrayList<>();
        for (MutableComponent comp : text) {
            String s = comp.getString().trim();
            if (!s.isEmpty()) lines.add(s);
        }

        if (lines.isEmpty()) return;

        // First line → station name, rest → upcoming stops
        String stationName = lines.get(0);
        List<String> stops = lines.size() > 1 ? lines.subList(1, lines.size()) : List.of();

        // Check if anything actually changed before triggering
        boolean changed = !stationName.equals(speaker.getStationName())
                || !stops.equals(speaker.getStopList());

        speaker.setData(stationName, new ArrayList<>(stops));

        // If the schedule data changed (e.g. a new train docked), announce
        if (changed && context.level() != null && !context.level().isClientSide()) {
            speaker.triggerAnnounce(context.level(), context.getTargetPos());
        }
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        // Accept up to 8 lines of text, max 128 chars wide
        return new DisplayTargetStats(8, 128, this);
    }
}
