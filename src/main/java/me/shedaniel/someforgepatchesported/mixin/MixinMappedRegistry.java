package me.shedaniel.someforgepatchesported.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import me.shedaniel.someforgepatchesported.LenientUnboundedMapCodec;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * https://github.com/MinecraftForge/MinecraftForge/pull/7527/
 */
@Mixin(MappedRegistry.class)
public class MixinMappedRegistry {
    @Inject(method = "directCodec", at = @At("HEAD"), cancellable = true, require = 0)
    private static <T> void directCodec(ResourceKey<? extends Registry<T>> resourceKey, Lifecycle lifecycle, Codec<T> codec, CallbackInfoReturnable<Codec<MappedRegistry<T>>> cir) {
        cir.setReturnValue(new LenientUnboundedMapCodec<>(ResourceLocation.CODEC.xmap(ResourceKey.elementKey(resourceKey), ResourceKey::location), codec).xmap((map) -> {
            MappedRegistry<T> mappedRegistry = new MappedRegistry<>(resourceKey, lifecycle);
            map.forEach((key, value) -> {
                mappedRegistry.register(key, value, lifecycle);
            });
            return mappedRegistry;
        }, (mappedRegistry) -> {
            return ImmutableMap.copyOf(mappedRegistry.keyStorage);
        }));
    }
}
