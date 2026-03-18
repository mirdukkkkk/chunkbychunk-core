package uk.mirdukkkkk.chunkbychunk.server.world;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.mirdukkkkk.chunkbychunk.common.ChunkByChunkConstants;
import uk.mirdukkkkk.chunkbychunk.config.ChunkByChunkConfig;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ChunkSpawnController extends SavedData {

    private final MinecraftServer server;
    private final Deque<SpawnRequest> requests = new ArrayDeque<>();

    @Nullable private SpawnRequest currentSpawnRequest = null;
    @Nullable private SpawnPhase phase;
    private boolean forcedTargetChunk;
    private int currentLayer;

    @Nullable private transient ServerLevel sourceLevel;
    @Nullable private transient ServerLevel targetLevel;
    // В 1.21.1 используем ChunkResult
    private transient CompletableFuture<ChunkResult<ChunkAccess>> sourceChunkFuture;

    public static ChunkSpawnController get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getChunkSource().getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new ChunkSpawnController(server),
                        (tag, provider) -> ChunkSpawnController.load(server, tag, provider),
                        DataFixTypes.LEVEL
                ),
                "chunkspawncontroller"
        );
    }

    private static ChunkSpawnController load(MinecraftServer server, CompoundTag tag, HolderLookup.Provider provider) {
        ChunkSpawnController chunkSpawnController = new ChunkSpawnController(server);
        chunkSpawnController.loadInternal(tag);
        return chunkSpawnController;
    }

    private void loadInternal(CompoundTag tag) {
        ListTag requestsTag = tag.getList("requests", ListTag.TAG_COMPOUND);
        for (int i = 0; i < requestsTag.size(); i++) {
            requests.add(SpawnRequest.load(requestsTag.getCompound(i)));
        }
        if (tag.contains("currentRequest")) {
            currentSpawnRequest = SpawnRequest.load(tag.getCompound("currentRequest"));
            phase = SpawnPhase.valueOf(tag.getString("phase"));
            forcedTargetChunk = tag.getBoolean("forcedTargetChunk");
            currentLayer = tag.getInt("currentLayer");

            sourceLevel = server.getLevel(currentSpawnRequest.sourceLevel);
            targetLevel = server.getLevel(currentSpawnRequest.targetLevel);

            sourceChunkFuture = sourceLevel.getChunkSource().getChunkFuture(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, ChunkStatus.FULL, true);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag requestsTag = new ListTag();
        for (SpawnRequest request : requests) {
            requestsTag.add(request.save());
        }
        tag.put("requests", requestsTag);
        if (currentSpawnRequest != null) {
            tag.put("currentRequest", currentSpawnRequest.save());
            tag.putString("phase", phase.name());
            tag.putBoolean("forcedTargetChunk", forcedTargetChunk);
            tag.putInt("currentLayer", currentLayer);
        }
        return tag;
    }

    private ChunkSpawnController(MinecraftServer server) {
        this.server = server;
    }

    public void tick() {
        if (currentSpawnRequest != null) {
            if (!sourceChunkFuture.isDone()) {
                return;
            }
            switch (phase) {
                case COPY_BIOMES -> {
                    ChunkAccess sourceChunk = sourceChunkFuture.getNow(ChunkResult.error(() -> "Chunk unloaded")).orElse(null);
                    if (sourceChunk != null) {
                        updateBiomes(sourceLevel,
                                sourceChunk,
                                targetLevel,
                                targetLevel.getChunk(currentSpawnRequest.targetChunkPos.x, currentSpawnRequest.targetChunkPos.z),
                                currentSpawnRequest.targetChunkPos);
                        phase = SpawnPhase.SPAWN_BLOCKS;
                        currentLayer = targetLevel.getMinBuildHeight();
                        setDirty();
                    }
                }
                case SPAWN_BLOCKS -> {
                    int minLayer = currentLayer;
                    int maxLayer = Math.min(currentLayer + ChunkByChunkConfig.get().getGeneration().getChunkLayerSpawnRate(), targetLevel.getMaxBuildHeight() + 1);
                    copyBlocks(sourceLevel, currentSpawnRequest.sourceChunkPos, targetLevel, currentSpawnRequest.targetChunkPos, minLayer, maxLayer);
                    if (maxLayer > targetLevel.getMaxBuildHeight()) {
                        if (ChunkByChunkConfig.get().getGeneration().spawnNewChunkChest() && !ChunkByChunkConfig.get().getGeneration().spawnChestInInitialChunkOnly()) {
                            SpawnChunkHelper.createNextSpawner(targetLevel, currentSpawnRequest.targetChunkPos);
                        }
                        phase = SpawnPhase.SYNCH_CHUNKS;
                    } else {
                        currentLayer = maxLayer;
                    }
                    setDirty();
                }
                case SYNCH_CHUNKS -> {
                    synchChunks();
                    phase = SpawnPhase.SPAWN_ENTITIES;
                    setDirty();
                }
                case SPAWN_ENTITIES -> {
                    if (sourceLevel.areEntitiesLoaded(currentSpawnRequest.sourceChunkPos.toLong())) {
                        spawnChunkEntities();
                        completeSpawnRequest();
                        setDirty();
                    }
                }
            }
        } else if (!requests.isEmpty()) {
            currentSpawnRequest = requests.removeFirst();
            targetLevel = server.getLevel(currentSpawnRequest.targetLevel());
            sourceLevel = server.getLevel(currentSpawnRequest.sourceLevel());
            forcedTargetChunk = targetLevel.setChunkForced(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z, true);
            sourceLevel.setChunkForced(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, true);
            sourceChunkFuture = sourceLevel.getChunkSource().getChunkFuture(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, ChunkStatus.FULL, true);

            phase = currentSpawnRequest.immediate ? SpawnPhase.SYNCH_CHUNKS : SpawnPhase.COPY_BIOMES;
            ChunkByChunkConstants.LOGGER.info("Spawning chunk {} in {}", currentSpawnRequest.targetChunkPos, targetLevel.dimension().location());
            setDirty();
        }
    }

    private void spawnChunkEntities() {
        List<Entity> entities = sourceLevel.getEntities((Entity) null, new AABB(currentSpawnRequest.sourceChunkPos().getMinBlockX(), sourceLevel.getMinBuildHeight(), currentSpawnRequest.sourceChunkPos().getMinBlockZ(), currentSpawnRequest.sourceChunkPos().getMaxBlockX(), sourceLevel.getMaxBuildHeight(), currentSpawnRequest.sourceChunkPos().getMaxBlockZ()), (x) -> true);
        for (Entity e : entities) {
            Vec3 pos = new Vec3(e.getX() + (currentSpawnRequest.targetChunkPos().x - currentSpawnRequest.sourceChunkPos().x) * 16, e.getY(), e.getZ() + (currentSpawnRequest.targetChunkPos().z - currentSpawnRequest.sourceChunkPos().z) * 16);

            DimensionTransition transition = new DimensionTransition(
                    targetLevel,
                    pos,
                    e.getDeltaMovement(),
                    e.getYRot(),
                    e.getXRot(),
                    DimensionTransition.DO_NOTHING
            );

            Entity movedEntity = e.changeDimension(transition);
            if (movedEntity != null) {
                movedEntity.setPos(pos);
            }
        }
    }

    private void completeSpawnRequest() {
        if (forcedTargetChunk) {
            targetLevel.setChunkForced(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z, false);
            sourceLevel.setChunkForced(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, false);
            currentSpawnRequest = null;
        }
    }

    private static void copyBlocks(ServerLevel sourceLevel, ChunkPos sourceChunkPos, ServerLevel targetLevel, ChunkPos targetChunkPos, int fromLayer, int toLayer) {
        int xOffset = targetChunkPos.getMinBlockX() - sourceChunkPos.getMinBlockX();
        int zOffset = targetChunkPos.getMinBlockZ() - sourceChunkPos.getMinBlockZ();

        Block sealedBlock = Blocks.BEDROCK;
        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator skyChunkGenerator && skyChunkGenerator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed) {
            sealedBlock = skyChunkGenerator.getSealBlock();
        }

        BlockPos.MutableBlockPos sourceBlock = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos targetBlock = new BlockPos.MutableBlockPos();
        for (int y = fromLayer; y < toLayer; y++) {
            for (int z = sourceChunkPos.getMinBlockZ(); z <= sourceChunkPos.getMaxBlockZ(); z++) {
                for (int x = sourceChunkPos.getMinBlockX(); x <= sourceChunkPos.getMaxBlockX(); x++) {
                    sourceBlock.set(x, y, z);
                    targetBlock.set(x + xOffset, y, z + zOffset);
                    BlockState existingState = targetLevel.getBlockState(targetBlock);
                    Block existingBlock = existingState.getBlock();
                    if (existingBlock instanceof AirBlock || existingBlock instanceof LiquidBlock || existingBlock == Blocks.BEDROCK || existingBlock == sealedBlock || existingBlock == Blocks.SNOW) {
                        BlockState newBlock = sourceLevel.getBlockState(sourceBlock);
                        if (ChunkByChunkConfig.get().getGameplayConfig().isChunkSpawnLeafDecayDisabled() && newBlock.getBlock() instanceof LeavesBlock) {
                            newBlock = newBlock.setValue(LeavesBlock.PERSISTENT, true);
                        }
                        targetLevel.setBlock(targetBlock, newBlock, Block.UPDATE_ALL);
                        BlockEntity fromBlockEntity = sourceLevel.getBlockEntity(sourceBlock);
                        BlockEntity toBlockEntity = targetLevel.getChunkAt(targetBlock).getBlockEntity(targetBlock);
                        if (fromBlockEntity != null && toBlockEntity != null) {
                            toBlockEntity.loadWithComponents(fromBlockEntity.saveWithFullMetadata(targetLevel.registryAccess()), targetLevel.registryAccess());
                        }
                    }
                }
            }
        }
    }

    private static void updateBiomes(ServerLevel sourceLevel, ChunkAccess sourceChunk, ServerLevel targetLevel, ChunkAccess targetChunk, ChunkPos targetChunkPos) {
        if (sourceChunk.getSections().length != targetChunk.getSections().length) {
            ChunkByChunkConstants.LOGGER.warn("Section count mismatch between {} and {} - {} vs {}", sourceLevel.dimension(), targetLevel.dimension(), sourceChunk.getSections().length, targetChunk.getSections().length);
        }

        boolean biomesUpdated = false;
        for (int targetIndex = 0; targetIndex < targetChunk.getSections().length; targetIndex++) {
            int sourceIndex = (targetIndex < sourceChunk.getSections().length) ? targetIndex : sourceChunk.getSections().length - 1;
            if (sourceChunk.getSections()[sourceIndex].getBiomes() instanceof PalettedContainer<Holder<Biome>> sourceBiomes && targetChunk.getSections()[targetIndex].getBiomes() instanceof PalettedContainer<Holder<Biome>> targetBiomes) {

                FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                sourceBiomes.write(friendlyByteBuf);
                byte[] buffer = friendlyByteBuf.array();

                FriendlyByteBuf targetFriendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());
                targetBiomes.write(targetFriendlyByteBuf);
                byte[] targetBuffer = targetFriendlyByteBuf.array();

                if (!Arrays.equals(buffer, targetBuffer)) {
                    friendlyByteBuf.readerIndex(0);
                    targetBiomes.read(friendlyByteBuf);
                    targetChunk.setUnsaved(true);
                    biomesUpdated = true;
                }
            }
        }
        if (biomesUpdated) {
            ((ControllableChunkMap) targetLevel.getChunkSource().chunkMap).forceReloadChunk(targetChunkPos);
        }
    }

    private void synchChunks() {
        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            for (ResourceKey<Level> synchLevelId : generator.getSynchedLevels()) {
                ServerLevel synchLevel = server.getLevel(synchLevelId);
                if (synchLevel != null && synchLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator synchGenerator) {
                    double scale = DimensionType.getTeleportationScale(targetLevel.dimensionType(), synchLevel.dimensionType());
                    BlockPos pos = currentSpawnRequest.targetChunkPos().getMiddleBlockPosition(0);
                    ChunkPos synchChunk = new ChunkPos(BlockPos.containing(pos.getX() * scale, 0, pos.getZ() * scale));
                    request(synchChunk, synchLevelId, synchChunk, synchGenerator.getGenerationLevel(), false);
                }
            }
        }
    }

    public boolean isValidForLevel(ServerLevel level, String biomeTheme, boolean random) {
        if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            if (!biomeTheme.isEmpty()) {
                return generator.getBiomeDimension(biomeTheme) != null;
            } else if (random) {
                return generator.isRandomChunkSpawnerAllowed();
            } else {
                return generator.isChunkSpawnerAllowed();
            }
        }
        return false;
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos blockPos) {
        return request(level, biomeTheme, random, blockPos, false);
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos blockPos, boolean immediate) {
        ChunkPos targetChunkPos = new ChunkPos(blockPos);
        if (isValidForLevel(level, biomeTheme, random) && SpawnChunkHelper.isEmptyChunk(level, targetChunkPos) && level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            ChunkPos sourceChunkPos;
            if (random) {
                Random rng = new Random(blockPos.asLong());
                sourceChunkPos = new ChunkPos(rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE), rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE));
            } else {
                sourceChunkPos = new ChunkPos(targetChunkPos.x, targetChunkPos.z);
            }
            ResourceKey<Level> sourceLevel;
            if (biomeTheme.isEmpty()) {
                sourceLevel = generator.getGenerationLevel();
            } else {
                sourceLevel = generator.getBiomeDimension(biomeTheme);
            }
            return request(targetChunkPos, level.dimension(), sourceChunkPos, sourceLevel, immediate);
        }
        return false;
    }

    public boolean request(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos, ResourceKey<Level> sourceLevel, boolean immediate) {
        SpawnRequest spawnRequest = new SpawnRequest(targetChunkPos, targetLevel, sourceChunkPos, sourceLevel, immediate);
        if (!spawnRequest.equals(currentSpawnRequest) && !requests.contains(spawnRequest)) {
            if (immediate) {
                ServerLevel toLevel = server.getLevel(targetLevel);
                ServerLevel fromLevel = server.getLevel(sourceLevel);
                if (toLevel != null && fromLevel != null) {
                    LevelChunk toChunk = toLevel.getChunk(targetChunkPos.x, targetChunkPos.z);
                    LevelChunk fromChunk = fromLevel.getChunk(sourceChunkPos.x, sourceChunkPos.z);
                    updateBiomes(fromLevel, fromChunk, toLevel, toChunk, targetChunkPos);
                    copyBlocks(fromLevel, spawnRequest.sourceChunkPos, toLevel, spawnRequest.targetChunkPos, toLevel.getMinBuildHeight(), toLevel.getMaxBuildHeight() + 1);
                    requests.addFirst(spawnRequest);
                }
            } else {
                requests.add(spawnRequest);
            }
            setDirty();
            return true;
        }
        return false;
    }

    public boolean isBusy() {
        return currentSpawnRequest != null || !requests.isEmpty();
    }

    private record SpawnRequest(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos, ResourceKey<Level> sourceLevel, boolean immediate) {
        public static final String TARGET_POS = "targetPos";
        public static final String TARGET_LEVEL = "targetLevel";
        public static final String SOURCE_POS = "sourcePos";
        public static final String SOURCE_LEVEL = "sourceLevel";
        public static final String IMMEDIATE = "immediate";

        public static SpawnRequest load(CompoundTag tag) {
            ChunkPos targetPos = new ChunkPos(tag.getLong(TARGET_POS));
            ResourceKey<Level> targetLevel = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(TARGET_LEVEL)));
            ChunkPos sourcePos = new ChunkPos(tag.getLong(SOURCE_POS));
            ResourceKey<Level> sourceLevel = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(SOURCE_LEVEL)));
            boolean immediate = tag.getBoolean(IMMEDIATE);
            return new SpawnRequest(targetPos, targetLevel, sourcePos, sourceLevel, immediate);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TARGET_POS, targetChunkPos.toLong());
            tag.putString(TARGET_LEVEL, targetLevel.location().toString());
            tag.putLong(SOURCE_POS, sourceChunkPos.toLong());
            tag.putString(SOURCE_LEVEL, sourceLevel.location().toString());
            tag.putBoolean(IMMEDIATE, immediate);
            return tag;
        }
    }

    private enum SpawnPhase {
        COPY_BIOMES, SPAWN_BLOCKS, SYNCH_CHUNKS, SPAWN_ENTITIES
    }
}