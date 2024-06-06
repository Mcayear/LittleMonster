package com.smallaswater.littlemonster.entity.vanilla;

import cn.nukkit.entity.BaseEntity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import com.smallaswater.littlemonster.config.MonsterConfig;

public class VanillaEntity extends BaseEntity {
    public VanillaEntity(FullChunk chunk, CompoundTag nbt, MonsterConfig config) {
        super(chunk, nbt);
    }

    @Override
    public Vector3 updateMove(int i) {
        return null;
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public int getNetworkId() {
        return 0;
    }
}
