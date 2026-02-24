package com.smallaswater.littlemonster.entity.vanilla.ai.entity;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import com.smallaswater.littlemonster.entity.vanilla.ai.route.AdvancedRouteFinder;
import com.smallaswater.littlemonster.entity.vanilla.ai.route.Node;
import com.smallaswater.littlemonster.entity.vanilla.ai.route.RouteFinder;
import com.smallaswater.littlemonster.utils.Utils;

import java.awt.*;

abstract public class MovingVanillaEntity extends EntityCreature {
    protected boolean isKnockback = false;
    public RouteFinder route = null;
    private Vector3 target = null;
    public boolean autoSeeFont = true;
    private int jammingTick = 0;
    private long passedTick = 1;

    public MovingVanillaEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.route = new AdvancedRouteFinder(this);
    }

    public void jump() {
        if (this.onGround) {
            this.motionY += 0.35;
        }
    }


    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.closed) {
            return false;
        }

        passedTick++;

        boolean hasUpdate = super.entityBaseTick(tickDiff);

        if (this.isKnockback) {// 如果击退，则在击中目标后立即发生
            this.isKnockback = false;
        } else if (this.onGround) {
            this.motionX = this.motionZ = 0;
        }
        this.motionY = 0;

        this.motionX *= (1 - this.getDrag());
        this.motionZ *= (1 - this.getDrag());
        if (this.motionX < 0.001 && this.motionX > -0.001) this.motionX = 0;
        if (this.motionZ < 0.001 && this.motionZ > -0.001) this.motionZ = 0;

        if (this.onGround && this.hasSetTarget() && (this.route.getDestination() == null || this.route.getDestination().distance(this.getTarget()) > 2)) { //目标移动
            this.route.setPositions(this.level, this, this.getTarget(), this.boundingBox);

            if (this.route.isSearching()) this.route.research();
            else this.route.search();

            hasUpdate = true;
        }

        if (this.route != null && !this.route.isSearching() && this.route.isSuccess() && this.route.hasRoute()) { // entity has route to go
            hasUpdate = true;
            Node node = this.route.get();
            if (node != null) {
                Vector3 vec = node.getVector3();
                double diffX = Math.pow(vec.x - this.x, 2);
                double diffZ = Math.pow(vec.z - this.z, 2);
                if (diffX + diffZ == 0) {
                    jammingTick = 0;
                    if (this.route.next() == null) {
                        this.route.arrived();
                        this.motionX += 0.5;
                        this.motionZ += 0.5;
                        this.updateMovement();
                    }
                } else {
                    jammingTick++;
                    int negX = vec.x - this.x < 0 ? -1 : 1;
                    int negZ = vec.z - this.z < 0 ? -1 : 1;

                    this.motionX = Math.min(Math.abs(vec.x - this.x), diffX / (diffX + diffZ) * this.getMovementSpeed()) * negX;
                    this.motionZ = Math.min(Math.abs(vec.z - this.z), diffZ / (diffX + diffZ) * this.getMovementSpeed()) * negZ;

                    if (vec.y > this.y) this.motionY += this.getGravity();

                    if (this.autoSeeFont) {
                        double angle = Math.atan2(vec.z - this.z, vec.x - this.x);
                        this.yaw = (float) ((angle * 180) / Math.PI) - 90;
                    }
                }
            }
        }

        // 处理重力 (放在 move 之前)
        if (!this.onGround) {
            if (this.isInsideOfWater()) {
                this.motionY += movementSpeed * 0.05D * ((this.target.y - this.y) / tickDiff);
                this.level.addParticle(new BubbleParticle(this.add(Utils.rand(-2.0D, 2.0D), Utils.rand(-0.5D, 0.0D), Utils.rand(-2.0D, 2.0D))));
            } else {
                this.motionY -= this.getGravity();
            }
            hasUpdate = true;
        }

        // move 方法会自动处理碰撞箱计算，防止穿过半砖和树叶
        this.move(this.motionX, this.motionY, this.motionZ);

        // 处理自动跳跃 (模仿原版行为)
        if ((this.motionX != 0 || this.motionZ != 0) && this.isCollidedHorizontally && this.onGround) {
            this.jump();
        }

        return hasUpdate;
    }

    private double toRound(double i) {
        long p = Math.round(i);
        if (Math.abs(i - p) < 0.2) {
            return p;
        } else {
            return i;
        }
    }

    public double getRange() {
        return 30.0;
    }

    public void setTarget(Vector3 vec) {
        this.setTarget(vec, false);
    }

    public void setTarget(Vector3 vec, boolean forceSearch) {

        if (forceSearch || !this.hasSetTarget()) {
            this.target = vec;
        }

        if (this.hasSetTarget() && (forceSearch || !this.route.hasRoute())) {
            this.route.setPositions(this.level, this, this.target, this.boundingBox.clone());
            if (this.route.isSearching()) this.route.research();
            else this.route.search();
        }
    }

    public Vector3 getTarget() {
        return new Vector3(this.target.x, this.target.y, this.target.z);
    }

    /**
     * Returns whether the entity has following target
     * Entity will try to move to position where target exists
     */
    public boolean hasFollowingTarget() {
        return this.route.getDestination() != null && this.target != null && this.distance(this.target) < this.getRange();
    }

    /**
     * Returns whether the entity has set its target
     * The entity may not follow the target if there is following target and set target is different
     * If following distance of target is too far to follow or cannot reach, set target will be the next following target
     */
    public boolean hasSetTarget() {
        return this.target != null && this.distance(this.target) < this.getRange();
    }

    @Override
    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        this.isCollidedVertically = movY != dy;
        this.isCollidedHorizontally = (movX != dx || movZ != dz);
        this.isCollided = (this.isCollidedHorizontally || this.isCollidedVertically);

        // 让物理引擎自动判断是否落地
        this.onGround = (movY != dy && movY < 0);
    }

    @Override
    protected void initEntity() {
        super.initEntity();
    }

    @Override
    public void knockBack(Entity attacker, double damage, double x, double z, double base) {
        this.isKnockback = true;
        super.knockBack(attacker, damage, x, z, base);
    }

    @Override
    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        this.level.addEntityMovement(this, x, y, z, yaw, pitch, headYaw);
    }
}
