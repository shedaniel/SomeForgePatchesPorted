package me.shedaniel.someforgepatchesported.mixin;

import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import me.shedaniel.someforgepatchesported.LenientUnboundedMapCodec;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryReadOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * https://github.com/MinecraftForge/MinecraftForge/pull/7599
 */
@Mixin(LevelStorageSource.class)
public class MixinLevelStorageSource {
    @Redirect(method = "readWorldGenSettings", at = @At(value = "INVOKE",
                                                        target = "Lcom/mojang/datafixers/DataFixer;update(Lcom/mojang/datafixers/DSL$TypeReference;Lcom/mojang/serialization/Dynamic;II)Lcom/mojang/serialization/Dynamic;"))
    private static <T> Dynamic<T> updateDfu(DataFixer dataFixer, DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
        return fixUpDimensionsData(dataFixer.update(References.WORLD_GEN_SETTINGS, input, version, newVersion));
    }

    @Unique
    private static final Logger LOGGER = LogManager.getLogger("Some Forge Patches Ported");

    @Unique
    private static final Set<String> VANILLA_DIMS = Sets.newHashSet("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    @Unique
    private static final String DIMENSIONS_KEY = "dimensions";
    @Unique
    private static final String SEED_KEY = "seed";
    //No to static init!
    @Unique
    private static final Supplier<Codec<MappedRegistry<LevelStem>>> CODEC = Suppliers.memoize(() ->
            MappedRegistry.dataPackCodec(Registry.LEVEL_STEM_REGISTRY, Lifecycle.stable(), LevelStem.CODEC)
                    .xmap(LevelStem::sortMap, Function.identity())
    );

    /**
     * Restores previously "deleted" dimensions to the world.
     * The {@link LenientUnboundedMapCodec} prevents this from happening, this is to fix any world from before the fix.
     */
    @Unique
    private static <T> Dynamic<T> fixUpDimensionsData(Dynamic<T> data) {
        if (!(data.getOps() instanceof RegistryReadOps<T> ops))
            return data;

        Dynamic<T> dymData = data.get(DIMENSIONS_KEY).orElseEmptyMap();
        Dynamic<T> withInjected = dymData.asMapOpt().map(current ->
        {
            List<Pair<String, T>> currentList = current.map(p -> p.mapFirst(dyn -> dyn.asString().result().orElse("")).mapSecond(Dynamic::getValue)).collect(Collectors.toList());
            Set<String> currentDimNames = currentList.stream().map(Pair::getFirst).collect(Collectors.toSet());

            // FixUp deleted vanilla dims.
            if (!currentDimNames.containsAll(VANILLA_DIMS)) {
                LOGGER.warn("Detected missing vanilla dimensions from the world!");
                RegistryAccess regs = ((RegistryReadOpsAccessor) ops).getRegistryAccess();

                long seed = data.get(SEED_KEY).get().result().map(d -> d.asLong(0L)).orElse(0L);
                Registry<Biome> biomeReg = regs.registryOrThrow(Registry.BIOME_REGISTRY);
                Registry<DimensionType> typeReg = regs.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY);
                Registry<NoiseGeneratorSettings> noiseReg = regs.registryOrThrow(Registry.NOISE_GENERATOR_SETTINGS_REGISTRY);

                //Loads the default nether and end
                MappedRegistry<LevelStem> dimReg = DimensionType.defaultDimensions(typeReg, biomeReg, noiseReg, seed);
                //Loads the default overworld
                dimReg = WorldGenSettings.withOverworld(typeReg, dimReg, WorldGenSettings.makeDefaultOverworld(biomeReg, noiseReg, seed));

                // Encode and decode the registry. This adds any dimensions from datapacks (see SimpleRegistryCodec#decode), but only the vanilla overrides are needed.
                // This assumes that the datapacks for the vanilla dimensions have not changed since they were "deleted"
                // If they did, this will be seen in newly generated chunks.
                // Since this is to fix an older world, from before the fixes by forge, there is no way to know the state of the dimension when it was "deleted".
                dimReg = CODEC.get().encodeStart(ops, dimReg).flatMap(t -> CODEC.get().parse(ops, t)).result().orElse(dimReg);
                for (String name : VANILLA_DIMS) {
                    if (currentDimNames.contains(name))
                        continue;
                    LevelStem dim = dimReg.get(new ResourceLocation(name));
                    if (dim == null) {
                        LOGGER.error("The world is missing dimension: " + name + ", but the attempt to re-inject it failed.");
                        continue;
                    }
                    LOGGER.info("Fixing world: re-injected dimension: " + name);
                    currentList.add(Pair.of(name, LevelStem.CODEC.encodeStart(ops, dim).resultOrPartial(s -> {}).orElse(ops.empty())));
                }
            } else
                return dymData;

            return new Dynamic<>(ops, ops.createMap(currentList.stream().map(p -> p.mapFirst(ops::createString))));
        }).result().orElse(dymData);
        return data.set(DIMENSIONS_KEY, withInjected);
    }
}
