package com.smallaswater.littlemonster.entity.vanilla;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDeathEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.utils.TextFormat;
import com.smallaswater.littlemonster.LittleMonsterMainClass;
import com.smallaswater.littlemonster.config.MonsterConfig;
import com.smallaswater.littlemonster.entity.EntityCommandSender;
import com.smallaswater.littlemonster.entity.IEntity;
import com.smallaswater.littlemonster.events.entity.LittleMonsterEntityDeathDropExpEvent;
import com.smallaswater.littlemonster.handle.DamageHandle;
import com.smallaswater.littlemonster.items.BaseItem;
import com.smallaswater.littlemonster.items.DeathCommand;
import com.smallaswater.littlemonster.items.DropItem;
import com.smallaswater.littlemonster.utils.Utils;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;

public class VanillaEntity extends BaseEntity implements IEntity {
    public final int NETWORK_ID;

    public IEntity vanillaNPC;

    @Setter
    @Getter
    protected MonsterConfig config;

    /**
     * 配置文件中指定的存活时间
     * 若为 -1 则无限
     */
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
     * 伤害处理
     */
    public DamageHandle handle = new DamageHandle();

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

    public VanillaEntity(FullChunk chunk, CompoundTag nbt, MonsterConfig config) {
        super(chunk, nbt);
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
    public void spawnTo(Player player) {
        super.spawnTo(player);

        // 发送盔甲
        if (!this.armor[0].isNull() || !this.armor[1].isNull() || !this.armor[2].isNull() || !this.armor[3].isNull()) {
            MobArmorEquipmentPacket pk = new MobArmorEquipmentPacket();
            pk.eid = this.getId();
            pk.slots = this.armor;

            player.dataPacket(pk);
        }

        // 发送武器
        if (this.tool != null) {
            MobEquipmentPacket pk = new MobEquipmentPacket();
            pk.eid = this.getId();
            pk.hotbarSlot = 0;
            pk.item = this.tool;
            player.dataPacket(pk);
        }
    }

    protected ArrayList<Player> getDamagePlayerList() {
        ArrayList<Player> players = new ArrayList<>();
        Player player;
        for (String name : handle.playerDamageList.keySet()) {
            player = Server.getInstance().getPlayer(name);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    protected Player getDamageMaxPlayer() {
        double max = 0;
        Player p = null;
        for (Map.Entry<String, Double> player : handle.playerDamageList.entrySet()) {
            if (player.getValue() > max) {
                if (Server.getInstance().getPlayer(player.getKey()) != null) {
                    p = Server.getInstance().getPlayer(player.getKey());
                }
                max = player.getValue();
            }
        }
        return p;
    }

    protected void disCommand(String cmd) {
        disCommand(cmd, null, null);
    }

    protected void disCommand(String cmd, String target, String name) {
        if (target != null) {
            Server.getInstance().getCommandMap().dispatch(
                    new EntityCommandSender(getName()), cmd.replace(target, name));
        } else {
            Server.getInstance().getCommandMap().dispatch(new EntityCommandSender(getName()), cmd);
        }
    }


    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public Vector3 updateMove(int i) {
        return null;
    }

    /**
     * 获取死亡掉落经验值
     *
     * @return 经验值
     */
    @Override
    public int getKillExperience() {
        if (config.getDropExp().size() > 1) {
            return Utils.rand(config.getDropExp().get(0), config.getDropExp().get(1));
        } else if (!config.getDropExp().isEmpty()) {
            return config.getDropExp().get(0);
        }
        return 0;
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
        Entity damager = null;
        EntityDamageEvent d = event.getEntity().getLastDamageCause();
        if (d instanceof EntityDamageByEntityEvent) {
            damager = ((EntityDamageByEntityEvent) d).getDamager();
        }
        LinkedList<Item> items = new LinkedList<>();
        // 死亡执行命令
        for (DeathCommand command : getConfig().getDeathCommand()) {
            if (command.getRound() >= Utils.rand(1, 100)) {
                String cmd = command.getCmd();
                cmd = cmd.replace("{x}", String.format("%.2f", getX()))
                        .replace("{y}", String.format("%.2f", getY()))
                        .replace("{z}", String.format("%.2f", getZ()))
                        .replace("{level}", getLevel().getFolderName());
                if (cmd.contains("@" + BaseItem.TARGETALL)) {
                    for (Player player : getDamagePlayerList()) {
                        disCommand(cmd, "@" + BaseItem.TARGETALL, player.getName());
                    }

                } else {
                    if (cmd.contains("@" + BaseItem.TARGET)) {
                        if (damager instanceof Player) {
                            cmd = cmd.replace("@" + BaseItem.TARGET, damager.getName());
                        }
                    }
                    if (cmd.contains("@" + BaseItem.DAMAGE)) {
                        Player player = getDamageMaxPlayer();
                        if (player != null) {
                            cmd = cmd.replace("@" + BaseItem.DAMAGE, player.getName());
                        }
                    }
                    disCommand(cmd);
                }
            }
        }

        // 死亡掉落物品
        for (DropItem key : getConfig().getDeathItem()) {
            if (key.getRound() >= Utils.rand(1, 100)) {
                items.add(key.getItem());
            }
        }
        event.setDrops(items.toArray(new Item[0]));

        String deathMessage = getConfig().getConfig().getString("公告.死亡.信息", "&e[ &bBOSS &e] {name} 在坐标: x: {x} y: {y} z: {z} 处死亡");
        if (getConfig().getConfig().getBoolean("公告.死亡.是否提示", true)) {
            Server.getInstance().broadcastMessage(TextFormat.colorize('&', deathMessage.replace("{name}", getConfig().getName())
                    .replace("{x}", getFloorX() + "")
                    .replace("{y}", getFloorY() + "")
                    .replace("{z}", getFloorZ() + "")
                    .replace("{level}", getLevel().getFolderName())));
        }
        if (damager != null) {
            if (damager instanceof Player) {
                String killMessage = getConfig().getConfig().getString("公告.击杀.信息", "&e[ &bBOSS提醒 &e] &d{name} 被 {player} 击杀");
                if (getConfig().getConfig().getBoolean("公告.击杀.是否提示", true)) {
                    Server.getInstance().broadcastMessage(TextFormat.colorize('&', killMessage
                            .replace("{name}", getConfig().getName())
                            .replace("{player}", damager.getName()))
                    );
                }

                LittleMonsterEntityDeathDropExpEvent expEvent = new LittleMonsterEntityDeathDropExpEvent(this, this.getKillExperience());
                Server.getInstance().getPluginManager().callEvent(expEvent);
                if (!expEvent.isCancelled()) {
                    int dropExp = expEvent.getDropExp();
                    int addition = 0;
                    // TODO 事件完成后移除这个兼容
                    if (LittleMonsterMainClass.hasRcRPG) {// 经验加成
                        try {
                            Method getPlayerAttr = Class.forName("RcRPG.AttrManager.PlayerAttr").getMethod("getPlayerAttr", Player.class);
                            Object manager = getPlayerAttr.invoke(null, damager);
                            float experienceGainMultiplier = manager.getClass().getField("experienceGainMultiplier").getFloat(manager);
                            if (experienceGainMultiplier > 0) {
                                addition = (int) (experienceGainMultiplier * dropExp);
                            }
                        } catch (Exception e) {
                            LittleMonsterMainClass.getInstance().getLogger().error("RcRPG经验加成获取失败", e);
                        }
                    }
                    String tipText = "经验 +" + dropExp;
                    if (addition > 0) {
                        tipText = "经验 +" + dropExp + "§a(" + addition + ")";
                        dropExp += addition;
                    }
                    if (dropExp > 0) {
                        ((Player) damager).addExperience(dropExp);// TODO:升级音效
                        ((Player) damager).sendActionBar(tipText);
                    }
                }
            }
        }
    }
}
