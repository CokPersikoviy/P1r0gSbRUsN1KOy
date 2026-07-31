package ru.wilyfox.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessorMixin {
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelAccess.ChannelHandle> froghelper$getInstanceToChannel();

    @Accessor("tickingSounds")
    List<TickableSoundInstance> froghelper$getTickingSounds();

    @Accessor("queuedSounds")
    Map<SoundInstance, Integer> froghelper$getQueuedSounds();

    @Accessor("soundDeleteTime")
    Map<SoundInstance, Integer> froghelper$getSoundDeleteTimes();

    @Accessor("queuedTickableSounds")
    List<TickableSoundInstance> froghelper$getQueuedTickableSounds();
}
