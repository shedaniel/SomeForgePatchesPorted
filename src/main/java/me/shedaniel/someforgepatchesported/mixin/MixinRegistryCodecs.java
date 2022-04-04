package me.shedaniel.someforgepatchesported.mixin;

import com.mojang.serialization.Codec;
import me.shedaniel.someforgepatchesported.LenientUnboundedMapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * https://github.com/MinecraftForge/MinecraftForge/pull/7527/
 */
@Mixin(RegistryCodecs.class)
public class MixinRegistryCodecs {
    @Inject(method = "directCodec", at = @At("HEAD"), cancellable = true, require = 0)
    private static <T> void directCodec(ResourceKey<? extends Registry<T>> resourceKey, Codec<T> codec, CallbackInfoReturnable<Codec<Map<ResourceKey<T>, T>>> cir) {
        cir.setReturnValue(new LenientUnboundedMapCodec<>(ResourceLocation.CODEC.xmap(ResourceKey.elementKey(resourceKey), ResourceKey::location), codec));
    }
}
