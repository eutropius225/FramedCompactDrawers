package eutros.framedcompactdrawers.render.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.jaquadro.minecraft.storagedrawers.api.storage.IDrawerAttributes;
import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.LockAttribute;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawers;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.MissingTextureSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.data.*;
import net.minecraftforge.client.model.geometry.IModelGeometry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class FrameableModel implements IModelGeometry<FrameableModel> {

    public static final Logger LOGGER = LogManager.getLogger();

    public Multimap<MaterialSide, FramingCandidate> materials;
    public List<ResourceLocation> inherits = Collections.emptyList();

    public enum MaterialSide {
        SIDE(RenderType.getCutout()),
        FRONT(RenderType.getCutout()),
        TRIM(RenderType.getCutout()),
        OVERLAY(RenderType.getTranslucent());

        public final ModelProperty<ItemStack> property = new ModelProperty<>();
        private final RenderType type;

        MaterialSide(RenderType type) {
            this.type = type;
        }

        @Nullable
        public String getKey() {
            if(this != OVERLAY) {
                return "Mat" + name().charAt(0);
            }
            return null;
        }
    }

    public static class FramingCandidate {

        public static final FaceBakery FACE_BAKERY = new FaceBakery();

        public Vector3f start;
        public Vector3f end;
        public Direction direction;
        public BlockPartFace face;
        public Condition condition = Condition.ALWAYS;

        public String getRaw() {
            return face.texture;
        }

        public Baked baked(IModelConfiguration owner, IModelTransform transform, ResourceLocation modelLocation) {
            return new Baked(owner, transform, modelLocation);
        }

        public class Baked {

            private final Function<TextureAtlasSprite, BakedQuad> quadSupplier;
            private final Cache<TextureAtlasSprite, BakedQuad> quadCache =
                    CacheBuilder.newBuilder()
                            .expireAfterAccess(60, TimeUnit.SECONDS)
                            .build();
            public RenderMaterial rawMaterial;

            public FramingCandidate getEnclosing() {
                return FramingCandidate.this;
            }

            private Baked(IModelConfiguration owner, IModelTransform transform, ResourceLocation modelLocation) {
                rawMaterial = owner.resolveTexture(face.texture);
                face.blockFaceUV.setUvs(getFaceUvs(direction));
                quadSupplier = sprite -> FACE_BAKERY.bakeQuad(start, end, face, sprite, direction, transform, null, true, modelLocation);
            }

            /**
             * @see BlockPart#getFaceUvs(Direction)
             */
            public float[] getFaceUvs(Direction facing) {
                switch(facing) {
                    case DOWN:
                        return new float[] {start.getX(), 16.0F - end.getZ(), end.getX(), 16.0F - start.getZ()};
                    case UP:
                        return new float[] {start.getX(), start.getZ(), end.getX(), end.getZ()};
                    case NORTH:
                    default:
                        return new float[] {16.0F - end.getX(), 16.0F - end.getY(), 16.0F - start.getX(), 16.0F - start.getY()};
                    case SOUTH:
                        return new float[] {start.getX(), 16.0F - end.getY(), end.getX(), 16.0F - start.getY()};
                    case WEST:
                        return new float[] {start.getZ(), 16.0F - end.getY(), end.getZ(), 16.0F - start.getY()};
                    case EAST:
                        return new float[] {16.0F - end.getZ(), 16.0F - end.getY(), 16.0F - start.getZ(), 16.0F - start.getY()};
                }
            }

            public BakedQuad getQuad(ItemStack stack) {
                TextureAtlasSprite sprite = stack == null || stack.isEmpty() ?
                                            rawMaterial.getSprite() :
                                            getSprite(stack);
                try {
                    return quadCache.get(sprite, () -> quadSupplier.apply(sprite));
                } catch(ExecutionException e) {
                    return quadSupplier.apply(sprite);
                }
            }

            private TextureAtlasSprite getSprite(ItemStack stack) {
                return Minecraft.getInstance().getItemRenderer().getItemModelWithOverrides(stack, null, null).getParticleTexture(EmptyModelData.INSTANCE);
            }

        }

        @SuppressWarnings("unused")
        public enum Condition implements Predicate<IModelData> {
            LOCKED(data -> {
                IDrawerAttributes attr = data.getData(TileEntityDrawers.ATTRIBUTES);
                return attr != null &&
                        (attr.isItemLocked(LockAttribute.LOCK_EMPTY) ||
                                attr.isItemLocked(LockAttribute.LOCK_POPULATED));
            }),
            ALWAYS(data -> true);

            private final Predicate<IModelData> predicate;

            Condition(Predicate<IModelData> predicate) {
                this.predicate = predicate;
            }

            @Override
            public boolean test(IModelData data) {
                return predicate.test(data);
            }
        }

    }

    @Override
    public Collection<RenderMaterial> getTextures(IModelConfiguration owner, Function<ResourceLocation, IUnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors) {
        return materials.values()
                .stream()
                .map(FramingCandidate::getRaw)
                .map(owner::resolveTexture)
                .collect(Collectors.toList());
    }

    @Override
    public IBakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<RenderMaterial, TextureAtlasSprite> spriteGetter, IModelTransform modelTransform, ItemOverrideList overrides, ResourceLocation modelLocation) {
        IBakedModel parent = null;
        IUnbakedModel ownerModel = owner.getOwnerModel();
        if(ownerModel instanceof BlockModel) {
            ResourceLocation parentLoc = ((BlockModel) ownerModel).getParentLocation();
            if(parentLoc != null) {
                parent = bakery.getBakedModel(parentLoc, modelTransform, spriteGetter);
            }
        }
        HashMultimap<MaterialSide, FramingCandidate.Baked> bakedSides = materials.entries()
                .stream()
                .collect(Collector.of(HashMultimap::create,
                        (map, entry) ->
                                map.put(entry.getKey(),
                                        entry.getValue()
                                                .baked(owner, modelTransform, modelLocation)),
                        (first, second) -> {
                            first.putAll(second);
                            return first;
                        },
                        Collector.Characteristics.UNORDERED));
        for(ResourceLocation rl : inherits) {
            IBakedModel baked = bakery.getBakedModel(rl, modelTransform, spriteGetter);
            if(baked instanceof Baked) {
                for(Map.Entry<MaterialSide, FramingCandidate.Baked> entry : ((Baked) baked).bakedSides.entries()) {
                    bakedSides.put(entry.getKey(), entry.getValue());
                }
            } else {
                LOGGER.warn("Inherited model must be a frameable model! Got: " + (baked == null ?
                                                                                  "null" :
                                                                                  baked.getClass()));
            }
        }
        return new Baked(parent, bakedSides);
    }

    private static class Baked implements IDynamicBakedModel {

        final Multimap<MaterialSide, FramingCandidate.Baked> bakedSides;
        @Nullable
        private final IBakedModel parent;
        private final ItemOverrideList overrides;

        public Baked(@Nullable IBakedModel parent, Multimap<MaterialSide, FramingCandidate.Baked> bakedSides) {
            this.bakedSides = bakedSides;
            this.parent = parent;
            overrides = new ItemOverrideList() {
                @Nonnull
                @Override
                public IBakedModel func_239290_a_(IBakedModel model, ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity entity) {
                    return new Baked(parent, bakedSides) {
                        @Nonnull
                        @Override
                        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData) {
                            if(stack.hasTag()) {
                                CompoundNBT tag = Objects.requireNonNull(stack.getTag());
                                ModelDataMap.Builder builder = new ModelDataMap.Builder();
                                for(MaterialSide material : MaterialSide.values()) {
                                    String key = material.getKey();
                                    if(key != null && tag.contains(key))
                                        builder.withInitial(material.property, ItemStack.read(tag.getCompound(key)));
                                }
                                extraData = builder.build();
                            }
                            return super.getQuads(state, side, rand, extraData);
                        }

                        @Override
                        public ItemOverrideList getOverrides() {
                            return EMPTY;
                        }
                    };
                }
            };
        }

        @Nonnull
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull Random rand, @Nonnull IModelData extraData) {
            List<BakedQuad> quads = new ArrayList<>();
            RenderType layer = MinecraftForgeClient.getRenderLayer();
            for(MaterialSide material : MaterialSide.values()) {
                if(layer != null && material.type != layer) continue;
                for(FramingCandidate.Baked baked : bakedSides.get(material)) {
                    if(baked.getEnclosing().face.cullFace == side &&
                            baked.getEnclosing().condition.test(extraData))
                        quads.add(baked.getQuad(resolve(extraData, material)));
                }
            }
            return quads;
        }

        private ItemStack resolve(IModelData data, MaterialSide material) {
            if(material == MaterialSide.OVERLAY) {
                return null;
            }
            ItemStack stack = data.getData(material.property);
            if(material != MaterialSide.SIDE && (stack == null || stack.isEmpty())) {
                return resolve(data, MaterialSide.SIDE);
            }
            return stack;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return parent != null && parent.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return parent != null && parent.isGui3d();
        }

        @Override
        public boolean func_230044_c_() {
            return parent != null && parent.func_230044_c_();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return bakedSides.get(MaterialSide.SIDE)
                    .stream()
                    .findFirst()
                    .map(mat -> mat.rawMaterial.getSprite())
                    .orElseGet(() -> parent == null ?
                                     Minecraft.getInstance()
                                             .getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE)
                                             .apply(MissingTextureSprite.getLocation()) :
                                     parent.getParticleTexture(EmptyModelData.INSTANCE));
        }

        @Override
        public ItemOverrideList getOverrides() {
            return overrides;
        }

        @Override
        public IBakedModel handlePerspective(ItemCameraTransforms.TransformType cameraTransformType, MatrixStack mat) {
            if(parent != null) parent.handlePerspective(cameraTransformType, mat);
            return this;
        }

    }

}
