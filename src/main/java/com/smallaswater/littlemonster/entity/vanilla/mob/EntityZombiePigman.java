package com.smallaswater.littlemonster.entity.vanilla.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSwordGold;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.utils.Utils;
import com.smallaswater.littlemonster.config.MonsterConfig;
import com.smallaswater.littlemonster.entity.IEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EntityZombiePigman extends EntityWalkingMob implements IEntity {

    public final int NETWORK_ID;

    private int angry = 0;

    public IEntity vanillaNPC;

    @Setter
    @Getter
    protected MonsterConfig config;

    @Setter
    @Getter
    private int liveTime = -1;

    /**
     * 准则 不会伤害主人
     */
    @Setter
    @Getter
    protected EntityHuman masterHuman = null;


    /**
     * 如果主人死了 本体是否死亡。
     * 用于由技能生成的怪物
     */
    @Setter
    public boolean deathFollowMaster = false;

    public String spawnPos = null;

    /**
     * 防具
     */
    @Setter
    private Item[] armor;

    /**
     * 手持装备
     */
    @Setter
    private Item tool;

    /**
     * 移速
     */
    public double speed = 3;

    public float halfWidth = 0.3f;
    public float width = 0.6f;
    public float length = 0.6f;
    public float height = 1.8f;
    public float eyeHeight = 1.62f;

    public EntityZombiePigman(FullChunk chunk, CompoundTag nbt, MonsterConfig config) {
        super(chunk, nbt, config);
        this.NETWORK_ID = config.getNetworkId();

        Entity temp = Entity.createEntity(String.valueOf(config.getNetworkId()), chunk, nbt);
        if (temp != null) {
            width = temp.getWidth();
            length = temp.getLength();
            height = temp.getHeight();
            // length 可能为0，详见 `AxisAlignedBB bb` 计算
            if (length == 0) {
                length = width;
            }
            eyeHeight = temp.getEyeHeight();
            halfWidth = this.getWidth() / 2;
            temp.close();
        }
        this.dataProperties.putFloat(DATA_BOUNDING_BOX_HEIGHT, getHeight());
        this.dataProperties.putFloat(DATA_BOUNDING_BOX_WIDTH, getWidth());

        this.setMaxHealth(config.getHealth());
        this.setHealth(config.getHealth());
        this.setNameTagVisible(true);
        this.setNameTagAlwaysVisible(true);
        vanillaNPC = this;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 1.95f;
    }

    @Override
    public double getSpeed() {
        return this.isBaby() ? 1.6 : this.isAngry() ? 1.15 : 1.1;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(20);

        super.initEntity();

        if (this.namedTag.contains("Angry")) {
            this.angry = this.namedTag.getInt("Angry");
        }

        this.fireProof = true;
        this.setDamage(new int[] { 0, 5, 9, 13 });
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("Angry", this.angry);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (distance <= 100 && this.isAngry() && creature instanceof EntityZombiePigman && !((EntityZombiePigman) creature).isAngry()) {
            ((EntityZombiePigman) creature).setAngry(2400);
        }
        return this.isAngry() && super.targetOption(creature, distance);
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && this.distanceSquared(player) < 1.44) {
            this.attackDelay = 0;
            HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
            damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

            if (player instanceof Player) {
                float points = 0;
                for (Item i : ((Player) player).getInventory().getArmorContents()) {
                    points += this.getArmorPoints(i.getId());
                }

                damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                        (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f) - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * points * 0.04)));
            }
            player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
            this.playAttack();
        }
    }

    public boolean isAngry() {
        return this.angry > 0;
    }

    public void setAngry(int val) {
        this.angry = val;
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        super.attack(ev);

        if (!ev.isCancelled() && ev instanceof EntityDamageByEntityEvent) {
            if (((EntityDamageByEntityEvent) ev).getDamager() instanceof Player) {
                this.setAngry(2400);
            }
        }

        return true;
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);

        MobEquipmentPacket pk = new MobEquipmentPacket();
        pk.eid = this.getId();
        pk.item = new ItemSwordGold();
        pk.inventorySlot = 0;
        player.dataPacket(pk);
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (!this.isBaby()) {
            drops.add(Item.get(Item.ROTTEN_FLESH, 0, Utils.rand(0, 1)));
            drops.add(Item.get(Item.GOLD_NUGGET, 0, Utils.rand(0, 1)));
            drops.add(Item.get(Item.GOLD_SWORD, Utils.rand(20, 30), Utils.rand(0, 101) <= 9 ? 1 : 0));
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return this.isBaby() ? 0 : 5;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Zombie Pigman";
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        if (this.angry > 0) {
            this.angry--;
        }

        return super.entityBaseTick(tickDiff);
    }

    @Override
    public Entity getEntity() {
        return this;
    }

    @Override
    public void setSpawnPos(String name) {
        spawnPos = name;
    }

    @Override
    public String getSpawnPos() {
        return spawnPos;
    }

    @Override
    public boolean isVanillaEntity() {
        return true;
    }

    @Override
    public void onDeath(EntityDeathEvent event) {

    }
}
