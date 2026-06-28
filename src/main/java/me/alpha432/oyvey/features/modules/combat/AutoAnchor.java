package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoAnchorModule extends Module {

    public enum TargetMode { CLOSEST, LOWEST_HP, MOST_DAMAGE }
    public enum PlaceMode  { SAFE, SEMI_SAFE, FAST }
    public enum ChargeMode { AUTO, MANUAL, HOLD }

    // ── Target ────────────────────────────────────────────────────────────────
    private final Setting<TargetMode> targetMode   = enumSetting("TargetMode",  TargetMode.MOST_DAMAGE);
    private final Setting<Double>     targetRange  = number("TargetRange",  6.0,  1.0, 10.0);
    private final Setting<Double>     placeRange   = number("PlaceRange",   5.0,  1.0, 10.0);
    private final Setting<Double>     chargeRange  = number("ChargeRange",  5.0,  1.0, 10.0);
    private final Setting<Double>     explodeRange = number("ExplodeRange", 5.0,  1.0, 10.0);

    // ── Place ─────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    doPlace      = bool("Place",          true);
    private final Setting<PlaceMode>  placeMode    = enumSetting("PlaceMode",   PlaceMode.SAFE);
    private final Setting<Integer>    placeDelay   = intSetting("PlaceDelay",   3, 0, 20);
    private final Setting<Integer>    placePerTick = intSetting("PlacePerTick", 1, 1,  5);
    private final Setting<Double>     minPlaceDmg  = number("MinPlaceDmg",  4.0,  0.0, 20.0);
    private final Setting<Double>     maxSelfDmg   = number("MaxSelfDmg",   8.0,  0.0, 20.0);

    // ── Charge ────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    doCharge     = bool("Charge",         true);
    private final Setting<ChargeMode> chargeMode   = enumSetting("ChargeMode",  ChargeMode.AUTO);
    private final Setting<Integer>    chargeDelay  = intSetting("ChargeDelay",  2, 0, 20);

    // ── Explode ───────────────────────────────────────────────────────────────
    private final Setting<Boolean>    doExplode    = bool("Explode",        true);
    private final Setting<Integer>    explodeDelay = intSetting("ExplodeDelay", 1, 0, 20);
    private final Setting<Integer>    explodePerTick = intSetting("ExplodePerTick", 2, 1, 8);
    private final Setting<Double>     minExplodeDmg  = number("MinExplodeDmg",  2.0, 0.0, 20.0);

    // ── Glowstone ─────────────────────────────────────────────────────────────
    private final Setting<Boolean>    autoGlowstone  = bool("AutoGlowstone", true);  // auto switch to glowstone
    private final Setting<Boolean>    switchBack     = bool("SwitchBack",    true);

    // ── Safety ────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    antiSuicide    = bool("AntiSuicide",   true);
    private final Setting<Boolean>    onlyOverworld  = bool("OnlyOverworld", false); // anchors only explode in overworld
    private final Setting<Boolean>    safetyCheck    = bool("SafetyCheck",   true);  // confirm we are NOT in nether

    // ── Misc ──────────────────────────────────────────────────────────────────
    private final Setting<Boolean>    rotate         = bool("Rotate",        true);
    private final Setting<Boolean>    swing          = bool("Swing",         true);
    private final Setting<Boolean>    autoSwitch     = bool("AutoSwitch",    true);
    private final Setting<Boolean>    renderTarget   = bool("RenderTarget",  true);
    private final Setting<Boolean>    renderAnchors  = bool("RenderAnchors", true);

    // ─────────────────────────────────────────────────────────────────────────

    private static class AnchorEntry {
        BlockPos pos;
        int      charges; // 0-4
        AnchorEntry(BlockPos pos, int charges) {
            this.pos = pos; this.charges = charges;
        }
    }

    private int  placeTimer   = 0;
    private int  chargeTimer  = 0;
    private int  explodeTimer = 0;
    private int  savedSlot    = -1;
    private int  glowSlot     = -1;

    private final List<BlockPos>    placedAnchors = new ArrayList<>();

    public AutoAnchorModule() {
        super("AutoAnchor", "Automatically places, charges and explodes respawn anchors.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        placeTimer   = 0;
        chargeTimer  = 0;
        explodeTimer = 0;
        savedSlot    = -1;
        glowSlot     = -1;
        placedAnchors.clear();
    }

    @Override
    public void onDisable() {
        restoreSlot();
        placedAnchors.clear();
    }

    // ─── Main loop ────────────────────────────────────────────────────────────

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        // Anchors only explode in the overworld — safety check
        if (safetyCheck.getValue()) {
            int dim = mc.player.dimension;
            // dim 0 = overworld, -1 = nether, 1 = end
            // Anchors EXPLODE in overworld and end (not nether)
            // If player wants overworld-only and we're in nether, abort
            if (onlyOverworld.getValue() && dim != 0) return;
            // Never run in nether — anchors just charge there
            if (dim == -1) return;
        }

        EntityPlayer target = getTarget();
        if (target == null) return;

        // ── Place anchor ──────────────────────────────────────────────────────
        placeTimer++;
        if (doPlace.getValue() && placeTimer >= placeDelay.getValue()) {
            placeTimer = 0;
            int anchorSlot = getSlot(Blocks.RESPAWN_ANCHOR);
            if (anchorSlot != -1) {
                switchTo(anchorSlot);
                placeAnchors(target);
            }
        }

        // ── Charge anchors ────────────────────────────────────────────────────
        chargeTimer++;
        if (doCharge.getValue() && chargeTimer >= chargeDelay.getValue()) {
            chargeTimer = 0;
            glowSlot = getGlowstoneSlot();
            if (glowSlot != -1) {
                switchTo(glowSlot);
                chargeAnchors();
            }
        }

        // ── Explode anchors ───────────────────────────────────────────────────
        explodeTimer++;
        if (doExplode.getValue() && explodeTimer >= explodeDelay.getValue()) {
            explodeTimer = 0;
            // To explode, right-click a charged anchor without glowstone in hand
            int anchorSlot = getSlot(Blocks.RESPAWN_ANCHOR);
            if (anchorSlot != -1) {
                switchTo(anchorSlot);
                explodeAnchors(target);
            }
        }
    }

    // ─── Place logic ──────────────────────────────────────────────────────────

    private void placeAnchors(EntityPlayer target) {
        List<BlockPos> candidates = getPlacePositions(target);
        if (candidates.isEmpty()) return;

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

            if (placeBlockAt(pos, EnumFacing.UP)) {
                placedAnchors.add(pos);
                placed++;
            }
        }
    }

    // ─── Charge logic ─────────────────────────────────────────────────────────

    private void chargeAnchors() {
        for (BlockPos pos : new ArrayList<>(placedAnchors)) {
            // Check anchor still exists
            if (!isAnchor(pos)) {
                placedAnchors.remove(pos);
                continue;
            }

            int charges = getAnchorCharges(pos);
            if (charges >= 4) continue; // fully charged

            Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            double dist = mc.player.getDistance(hitVec.x, hitVec.y, hitVec.z);
            if (dist > chargeRange.getValue()) continue;

            if (rotate.getValue()) faceVec(hitVec);

            // Right-click anchor with glowstone to charge it
            mc.player.connection.sendPacket(
                new CPacketPlayerTryUseItemOnBlock(
                    pos,
                    EnumFacing.UP,
                    EnumHand.MAIN_HAND,
                    0.5f, 0.5f, 0.5f
                )
            );

            if (swing.getValue()) {
                mc.player.connection.sendPacket(
                    new CPacketAnimation(EnumHand.MAIN_HAND)
                );
            }
        }
    }

    // ─── Explode logic ────────────────────────────────────────────────────────

    private void explodeAnchors(EntityPlayer target) {
        List<BlockPos> charged = new ArrayList<>();

        for (BlockPos pos : new ArrayList<>(placedAnchors)) {
            if (!isAnchor(pos)) { placedAnchors.remove(pos); continue; }

            int charges = getAnchorCharges(pos);
            if (charges == 0) continue;

            double dmgToTarget = calcDamage(pos, target);
            double dmgToSelf   = calcDamage(pos, mc.player);

            if (dmgToTarget < minExplodeDmg.getValue()) continue;
            if (antiSuicide.getValue() && dmgToSelf >= mc.player.getHealth()) continue;

            double dist = mc.player.getDistance(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5
            );
            if (dist > explodeRange.getValue()) continue;

            charged.add(pos);
        }

        // Sort by damage
        charged.sort((a, b) -> Double.compare(
            calcDamage(b, target),
            calcDamage(a, target)
        ));

        int exploded = 0;
        for (BlockPos pos : charged) {
            if (exploded >= explodePerTick.getValue()) break;

            Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

            if (rotate.getValue()) faceVec(hitVec);

            // Right-click charged anchor WITHOUT glowstone = explosion
            mc.player.connection.sendPacket(
                new CPacketPlayerTryUseItemOnBlock(
                    pos,
                    EnumFacing.UP,
                    EnumHand.MAIN_HAND,
                    0.5f, 0.5f, 0.5f
                )
            );

            if (swing.getValue()) {
                mc.player.connection.sendPacket(
                    new CPacketAnimation(EnumHand.MAIN_HAND)
                );
            }

            placedAnchors.remove(pos);
            exploded++;
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

                // Need solid block under anchor
                if (!mc.world.getBlockState(base).isFullBlock()) continue;

                BlockPos anchorPos = base.up();

                // Must be air at anchor level
                if (!mc.world.getBlockState(anchorPos).getBlock().equals(Blocks.AIR)) continue;

                // No entities blocking placement
                if (!mc.world.getEntitiesWithinAABBExcludingEntity(
                        null,
                        new AxisAlignedBB(anchorPos)
                ).isEmpty()) continue;

                // Range check from player
                double dist = mc.player.getDistance(
                    anchorPos.getX() + 0.5,
                    anchorPos.getY(),
                    anchorPos.getZ() + 0.5
                );
                if (dist > placeRange.getValue()) continue;

                // Don't place where we already have one
                if (placedAnchors.contains(anchorPos)) continue;

                switch (placeMode.getValue()) {
                    case SAFE:
                        if (target.getDistance(
                            anchorPos.getX() + 0.5,
                            anchorPos.getY(),
                            anchorPos.getZ() + 0.5) > 3.0) continue;
                        break;
                    case SEMI_SAFE:
                        if (target.getDistance(
                            anchorPos.getX() + 0.5,
                            anchorPos.getY(),
                            anchorPos.getZ() + 0.5) > 5.0) continue;
                        break;
                    case FAST:
                        break;
                }

                valid.add(anchorPos);
            }
        }

        return valid;
    }

    // ─── Block placement ──────────────────────────────────────────────────────

    private boolean placeBlockAt(BlockPos pos, EnumFacing face) {
        BlockPos neighbor = pos.down();

        if (!mc.world.getBlockState(neighbor).isFullBlock()) return false;

        Vec3d hitVec = new Vec3d(
            pos.getX() + 0.5,
            pos.getY(),
            pos.getZ() + 0.5
        );

        double dist = mc.player.getDistance(hitVec.x, hitVec.y, hitVec.z);
        if (dist > placeRange.getValue()) return false;

        if (rotate.getValue()) faceVec(hitVec);

        mc.player.connection.sendPacket(
            new CPacketPlayerTryUseItemOnBlock(
                neighbor,
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

        return true;
    }

    // ─── Anchor helpers ───────────────────────────────────────────────────────

    private boolean isAnchor(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR;
    }

    private int getAnchorCharges(BlockPos pos) {
        if (!isAnchor(pos)) return 0;
        // RESPAWN_ANCHOR has a CHARGES property 0-4
        try {
            net.minecraft.block.properties.IProperty<?> chargeProp =
                mc.world.getBlockState(pos).getBlock()
                    .getBlockState().getProperties().stream()
                    .filter(p -> p.getName().equals("charges"))
                    .findFirst().orElse(null);

            if (chargeProp == null) return 0;

            Object val = mc.world.getBlockState(pos).getValue(
                (net.minecraft.block.properties.IProperty) chargeProp
            );

            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    // ─── Damage calculation ───────────────────────────────────────────────────

    // Respawn anchor explosion power is 5 (vs crystal 6) — adjusted formula
    private double calcDamage(BlockPos anchorPos, EntityPlayer entity) {
        Vec3d explosion = new Vec3d(
            anchorPos.getX() + 0.5,
            anchorPos.getY() + 0.5,
            anchorPos.getZ() + 0.5
        );

        double dist = entity.getDistance(explosion.x, explosion.y, explosion.z);
        if (dist > 10.0) return 0;

        // Anchor power 5 vs crystal power 6 — slightly less damage
        double exposure  = 1.0 - (dist / 10.0);
        double damage    = ((exposure * exposure) * 7.0 * 10.0 + exposure) * 9.0;

        double armorPct  = entity.getTotalArmorValue() / 40.0;
        damage          *= (1.0 - armorPct * 0.75);

        return damage;
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
                    BlockPos pPos    = new BlockPos(p.posX,    p.posY - 1,    p.posZ);
                    BlockPos bestPos = new BlockPos(best.posX, best.posY - 1, best.posZ);
                    if (calcDamage(pPos, p) > calcDamage(bestPos, best)) best = p;
                    break;
            }
        }

        return best;
    }

    // ─── Hotbar helpers ───────────────────────────────────────────────────────

    private int getSlot(Block block) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                Block b = ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock();
                if (b == block) return i;
            }
        }
        return -1;
    }

    private int getGlowstoneSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Items.GLOWSTONE_DUST) return i;
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.ItemBlock) {
                Block b = ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock();
                if (b == Blocks.GLOWSTONE) return i;
            }
        }
        return -1;
    }

    private void switchTo(int slot) {
        if (autoSwitch.getValue() && slot != mc.player.inventory.currentItem) {
            if (savedSlot == -1) savedSlot = mc.player.inventory.currentItem;
            mc.player.inventory.currentItem = slot;
        }
    }

    private void restoreSlot() {
        if (savedSlot != -1 && switchBack.getValue()) {
            mc.player.inventory.currentItem = savedSlot;
            savedSlot = -1;
        }
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
}
