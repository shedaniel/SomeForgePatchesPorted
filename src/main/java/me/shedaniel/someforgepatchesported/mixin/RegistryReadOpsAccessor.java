package me.shedaniel.someforgepatchesported.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryReadOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryReadOps.class)
public interface RegistryReadOpsAccessor {
    @Accessor
    RegistryAccess getRegistryAccess();
}
