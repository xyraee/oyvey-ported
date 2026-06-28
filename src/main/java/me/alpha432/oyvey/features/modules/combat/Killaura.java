package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;

public class KillAuraModule extends Module {

    private final Setting<Double> range = number("Range", 4.0, 1.0, 6.0);
    private final Setting<Boolean> players = bool("Players", true);
    private final Setting<Boolean> mobs = bool("Mobs", false);
    private final Setting<Boolean> rotations = bool("Rotations", true);
    private final Setting<Double> rotationSpeed = number("RotationSpeed", 8.0, 1.0, 20.0);

    private boolean jumping = false;
    private float currentYaw = 0f;
    private float currentPitch = 0f;

    public KillAuraModule() {
        super("KillAura", "Automatically crits nearby entities.", Category.COMBAT);
    }

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        Entity target = getTarget();
        if (target == null) {
            jumping = false;
            return;
        }

        if (rotations.getValue()) {
            faceEntity(target);
        }

        if (!isCooldownReady()) return;

        if (canCrit()) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
            mc.playerController.attackEntity(mc.player, target);
            jumping = false;
            return;
        }

        if (!jumping && mc.player.onGround) {
            mc.player.jump();
            jumping = true;
        }
    }

    private void faceEntity(Entity target) {
        // Target the chest/body area of the entity
        double x = target.posX - mc.player.posX;
        double y = (target.posY + target.getEyeHeight() / 2.0) - (mc.player.posY + mc.player.getEyeHeight());
        double z = target.posZ - mc.player.posZ;

        double dist = MathHelper.sqrt(x * x + z * z);

        float targetYaw = (float)(Math.toDegrees(Math.atan2(z, x))) - 90f;
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(y, dist)));

        // Smooth rotation toward target using rotationSpeed
        float speed = (float)(rotationSpeed.getValue() / 20.0);

        currentYaw = smoothRotation(currentYaw, targetYaw, speed);
        currentPitch = smoothRotation(currentPitch, targetPitch, speed);

        mc.player.rotationYaw = currentYaw;
        mc.player.rotationPitch = MathHelper.clamp(currentPitch, -90f, 90f);
        mc.player.rotationYawHead = currentYaw;
        mc.player.renderYawOffset = currentYaw;
    }

    private float smoothRotation(float current, float target, float speed) {
        float diff = MathHelper.wrapDegrees(target - current);
        // Cap how many degrees we rotate per tick
        float maxStep = speed * 180f;
        diff = MathHelper.clamp(diff, -maxStep, maxStep);
        return current + diff;
    }

    private boolean isCooldownReady() {
        return mc.player.getCooledAttackStrength(0.5f) >= 1.0f;
    }

    private boolean canCrit() {
        return !mc.player.onGround
            && mc.player.fallDistance > 0
            && !mc.player.isInWater()
            && !mc.player.isInLava()
            && !mc.player.isSprinting()
            && !mc.player.isRiding();
    }

    private Entity getTarget() {
        Entity closest = null;
        double closestDist = range.getValue();

        for (Entity entity : mc.world.loadedEntityList) {
            if (entity == mc.player) continue;
            if (!isValid(entity)) continue;

            double dist = mc.player.getDistanceTo(entity);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }

        return closest;
    }

    private boolean isValid(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) return false;
        if (((EntityLivingBase) entity).getHealth() <= 0) return false;
        if (entity instanceof EntityPlayer && !players.getValue()) return false;
        if (!(entity instanceof EntityPlayer) && !mobs.getValue()) return false;
        return true;
    }
}
