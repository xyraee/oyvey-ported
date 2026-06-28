package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoCrystalModule extends Module {

    public enum TargetMode  { CLOSEST, LOWEST_HP, MOST_DAMAGE }
    public enum PlaceMode   { SAFE, FAST, SEMI_SAFE }
    public enum BreakMode   { INSTANT, DELAY, SEQUENTIAL }

    // ── Target ────────────────────────────────────────────────────────────────
    private final Setting<TargetMode> targetMode    = enumSetting("TargetMode",  TargetMode.MOST_DAMAGE);
    private final Setting<Double>     targetRange   = number("TargetRange",  6.0,  1.0, 10.0);
    private final Setting<Double>     placeRange    = number("PlaceRange",   5.0,  1.0, 10.0);
    private final Setting<Double>     breakRange    = number("BreakRange",   5.0,  1.0, 10.0);

    // ── Place ─────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    doPlace       = bool("Place",          true);
    private final Setting<PlaceMode>  placeMode     = enumSetting("PlaceMode",   PlaceMode.SAFE);
    private final Setting<Integer>    placeDelay    = intSetting("PlaceDelay",   3, 0, 20);  // ticks
    private final Setting<Integer>    placePerTick  = intSetting("PlacePerTick", 1, 1,  5);
    private final Setting<Double>     minPlaceDmg   = number("MinPlaceDmg",  4.0,  0.0, 20.0);
    private final Setting<Double>     maxSelfDmg    = number("MaxSelfDmg",   8.0,  0.0, 20.0);

    // ── Break ─────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    doBreak       = bool("Break",          true);
    private final Setting<BreakMode>  breakMode     = enumSetting("BreakMode",   BreakMode.INSTANT);
    private final Setting<Integer>    breakDelay    = intSetting("BreakDelay",   1, 0, 20);  // ticks
    private final Setting<Integer>    breakPerTick  = intSetting("BreakPerTick", 2, 1,  8);
    private final Setting<Double>     minBreakDmg   = number("MinBreakDmg",  2.0,  0.0, 20.0);

    // ── Misc ──────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    rotate        = bool("Rotate",         true);
    private final Setting<Boolean>    swing         = bool("Swing",          true);
    private final Setting<Boolean>    autoSwitch    = bool("AutoSwitch",     true);
    private final Setting<Boolean>    switchBack    = bool("SwitchBack",     true);
    private final Setting<Boolean>    antiSuicide   = bool("AntiSuicide",    true);
    private final Setting<Boolean>    renderTarget  = bool("RenderTarget",   true);

    private int   placeTimer    = 0;
    private int   breakTimer    = 0;
    private int   savedSlot     = -1;
    private final List<BlockPos> placedPositions = new ArrayList<>();

    public AutoCrystalModule() {
        super("AutoCrystal", "Automatically places and breaks end crystals.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        placeTimer = 0;
        breakTimer = 0;
        savedSlot  = -1;
        placedPositions.clear();
    }

    @Override
    public void onDisable() {
        if (savedSlot != -1 && switchBack.getValue()) {
            mc.player.inventory.currentItem = savedSlot;
            savedSlot = -1;
        }
        placedPositions.clear();
    }

    // ─── Main loop ────────────────────────────────────────────────────────────

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        EntityPlayer target = getTarget();
        if (target == null) return;

        // Switch to crystal
        int crystalSlot = getCrystalSlot();
        if (crystalSlot == -1) return;

        if (autoSwitch.getValue() && crystalSlot != mc.player.inventory.currentItem) {
            if (savedSlot == -1) savedSlot = mc.player.inventory.currentItem;
            mc.player.inventory.currentItem = crystalSlot;
        }

        // ── Break ─────────────────────────────────────────────────────────────
        breakTimer++;
        if (doBreak.getValue() && breakTimer >= breakDelay.getValue()) {
            breakTimer = 0;
            breakCrystals(target);
        }

        // ── Place ─────────────────────────────────────────────────────────────
        placeTimer++;
        if (doPlace.getValue() && placeTimer >= placeDelay.getValue()) {
            placeTimer = 0;
            placeCrystals(target);
        }
    }

    // ─── Place logic ──────────────────────────────────────────────────────────

    private void placeCrystals(EntityPlayer target) {
        List<BlockPos> candidates = getPlacePositions(target);
        if (candidates.isEmpty()) return;

        // Sort by damage dealt to target
        candidates.sort((a, b) -> Double.compare(
            calcDamage(b, target),
            calcDamage(a, target)
        ));

        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= placePerTick.getValue()) break;

            double dmgToTarget = calcDamage(pos, target);
            double dmgToSelf   = calcDamage(pos, mc.player);

            if (dmgToTarget < minPlaceDmg.getValue()) continue;
            if (antiSuicide.getValue() && dmgToSelf > maxSelfDmg.getValue()) continue;
            if (antiSuicide.getValue() && dmgToSelf >= mc.player.getHealth()) continue;

            placeAt(pos);
            placedPositions.add(pos);
            placed++;
        }
    }

    private void placeAt(BlockPos pos) {
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

        if (rotate.getValue()) faceVec(hitVec);

        mc.player.connection.sendPacket(
            new CPacketPlayerTryUseItemOnBlock(
                pos,
                EnumFacing.UP,
                EnumHand.MAIN_HAND,
                0.5f, 1.0f, 0.5f
            )
        );

        if (swing.getValue()) {
            mc.player.connection.sendPacket(
                new CPacketAnimation(EnumHand.MAIN_HAND)
            );
        }
    }

    // ─── Break logic ──────────────────────────────────────────────────────────

    private void breakCrystals(EntityPlayer target) {
        List<EntityEnderCrystal> crystals = getCrystalsToBreak(target);
        if (crystals.isEmpty()) return;

        switch (breakMode.getValue()) {
            case INSTANT:
                // Break as many as allowed per tick instantly
                int broken = 0;
                for (EntityEnderCrystal crystal : crystals) {
                    if (broken >= breakPerTick.getValue()) break;
                    attackCrystal(crystal);
                    broken++;
                }
                break;

            case DELAY:
                // One crystal per tick respecting delay
                attackCrystal(crystals.get(0));
                break;

            case SEQUENTIAL:
                // Only break crystals we placed
                for (EntityEnderCrystal crystal : crystals) {
                    BlockPos below = new BlockPos(crystal.posX, crystal.posY - 1, crystal.posZ);
                    if (placedPositions.contains(below)) {
                        attackCrystal(crystal);
                        placedPositions.remove(below);
                        break;
                    }
                }
                break;
        }
    }

    private void attackCrystal(EntityEnderCrystal crystal) {
        if (rotate.getValue()) {
            faceVec(new Vec3d(crystal.posX, crystal.posY, crystal.posZ));
        }

        mc.player.connection.sendPacket(
            new CPacketUseEntity(crystal, EnumHand.MAIN_HAND)
        );

        if (swing.getValue()) {
            mc.player.connection.sendPacket(
                new CPacketAnimation(EnumHand.MAIN_HAND)
            );
        }
    }

    // ─── Valid place positions ─────────────────────────────────────────────────

    private List<BlockPos> getPlacePositions(EntityPlayer target) {
        List<BlockPos> valid = new ArrayList<>();

        BlockPos targetPos = new BlockPos(target.posX, target.posY, target.posZ);
        int radius = (int) Math.ceil(placeRange.getValue());

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos base = targetPos.add(x, -1, z);

                // Must be obsidian or bedrock under the crystal
                if (!isValidBase(base)) continue;

                BlockPos above = base.up();

                // Must be air at crystal level and above
                if (!mc.world.getBlockState(above).getBlock().equals(Blocks.AIR)) continue;
                if (!mc.world.getBlockState(above.up()).getBlock().equals(Blocks.AIR)) continue;

                // Must not have entities in the way
                if (!mc.world.getEntitiesWithinAABBExcludingEntity(
                        null,
                        new AxisAlignedBB(above)
                ).isEmpty()) continue;

                // Range check
                double dist = mc.player.getDistance(
                    above.getX() + 0.5, above.getY(), above.getZ() + 0.5
                );
                if (dist > placeRange.getValue()) continue;

                switch (placeMode.getValue()) {
                    case SAFE:
                        // Must be near target
                        if (target.getDistance(
                            above.getX() + 0.5,
                            above.getY(),
                            above.getZ() + 0.5) > 3.0) continue;
                        break;

                    case SEMI_SAFE:
                        if (target.getDistance(
                            above.getX() + 0.5,
                            above.getY(),
                            above.getZ() + 0.5) > 5.0) continue;
                        break;

                    case FAST:
                        // No extra proximity check
                        break;
                }

                valid.add(base);
            }
        }

        return valid;
    }

    private boolean isValidBase(BlockPos pos) {
        net.minecraft.block.Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    // ─── Crystals to break ────────────────────────────────────────────────────

    private List<EntityEnderCrystal> getCrystalsToBreak(EntityPlayer target) {
        List<EntityEnderCrystal> list = new ArrayList<>();

        for (Object entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityEnderCrystal)) continue;
            EntityEnderCrystal crystal = (EntityEnderCrystal) entity;

            double distToPlayer = mc.player.getDistanceTo(crystal);
            if (distToPlayer > breakRange.getValue()) continue;

            double dmg = calcCrystalDamage(crystal.getPosition().down(), target);
            if (dmg < minBreakDmg.getValue()) continue;

            list.add(crystal);
        }

        // Sort by damage to target descending
        list.sort((a, b) -> Double.compare(
            calcCrystalDamage(b.getPosition().down(), target),
            calcCrystalDamage(a.getPosition().down(), target)
        ));

        return list;
    }

    // ─── Damage calculation ───────────────────────────────────────────────────

    // Estimate crystal explosion damage at a given position against an entity
    private double calcDamage(BlockPos crystalBase, EntityPlayer entity) {
        Vec3d crystalPos = new Vec3d(
            crystalBase.getX() + 0.5,
            crystalBase.getY() + 1.0,
            crystalBase.getZ() + 0.5
        );

        double dist = entity.getDistance(crystalPos.x, crystalPos.y, crystalPos.z);
        if (dist > 12.0) return 0;

        // Simplified explosion damage formula
        double exposure = 1.0 - (dist / 12.0);
        double damage   = ((exposure * exposure) * 7.0 * 12.0 + exposure) * 10.0;

        // Armor reduction (rough estimate — full prot IV netherite ≈ 80% reduction)
        double armorPct = entity.getTotalArmorValue() / 40.0;
        damage *= (1.0 - armorPct * 0.75);

        return damage;
    }

    private double calcCrystalDamage(BlockPos crystalBase, EntityPlayer entity) {
        return calcDamage(crystalBase, entity);
    }

    // ─── Target ───────────────────────────────────────────────────────────────

    private EntityPlayer getTarget() {
        EntityPlayer best = null;

        for (Object obj : mc.world.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.player) continue;

            double dist = mc.player.getDistanceTo(p);
            if (dist > targetRange.getValue()) continue;

            if (best == null) { best = p; continue; }

            switch (targetMode.getValue()) {
                case CLOSEST:
                    if (dist < mc.player.getDistanceTo(best)) best = p;
                    break;

                case LOWEST_HP:
                    if (p.getHealth() < best.getHealth()) best = p;
                    break;

                case MOST_DAMAGE:
                    // Pick the target we can deal most damage to
                    BlockPos bPos = new BlockPos(p.posX, p.posY - 1, p.posZ);
                    BlockPos bestPos = new BlockPos(best.posX, best.posY - 1, best.posZ);
                    if (calcDamage(bPos, p) > calcDamage(bestPos, best)) best = p;
                    break;
            }
        }

        return best;
    }

    // ─── Crystal slot ─────────────────────────────────────────────────────────

    private int getCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL) return i;
        }
        return -1;
    }

    // ─── Rotation ─────────────────────────────────────────────────────────────

    private void faceVec(Vec3d vec) {
        double dx = vec.x - mc.player.posX;
        double dy = vec.y - (mc.player.posY + mc.player.getEyeHeight());
        double dz = vec.z - mc.player.posZ;

        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        mc.player.rotationYaw     = yaw;
        mc.player.rotationPitch   = pitch;
        mc.player.rotationYawHead = yaw;
        mc.player.renderYawOffset = yaw;
    }

    // ─── 2D render (target highlight) ────────────────────────────────────────

    @Override
    public void onRender2D(float partialTicks) {
        if (!renderTarget.getValue()) return;
        if (nullCheck()) return;

        EntityPlayer target = getTarget();
        if (target == null) return;

        // Use your client's existing ESP util to draw a box around target
        // RenderUtil.drawESPBox(target, new Color(255, 50, 50, 180), partialTicks);
    }
}
