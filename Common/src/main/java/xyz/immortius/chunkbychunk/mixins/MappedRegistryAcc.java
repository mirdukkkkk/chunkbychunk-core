package xyz.immortius.chunkbychunk.mixins;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAcc<T> {
    @Accessor("registrationInfos")
    Map<ResourceKey<T>, RegistrationInfo> registrationInfos();

    @Accessor("toId")
    Reference2IntMap<T> toId();

    @Accessor("byId")
    ObjectList<Holder.Reference<T>> byId();

    @Accessor("byKey")
    Map<ResourceKey<T>, Holder.Reference<T>> byKey();

    @Accessor("byLocation")
    Map<ResourceLocation, Holder.Reference<T>> byLocation();

    @Accessor("byValue")
    Map<T, Holder.Reference<T>> byValue();
}
