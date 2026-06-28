package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoTrapModule extends Module {

    public enum TrapShape { BASIC, FULL, SURROUND, FEET_ONLY, CROWN }

    // ── Target ────────────────────────────────────────────────────────────────
    private final Setting<Double>    range      = number("Range",     5.0, 1.0, 10.0);
    private final Setting<Boolean>   closest    = bool("Closest",     true);

    // ── Trap shape ────────────────────────────────────────────────────────────
    private final Setting<TrapShape> shape      = enumSetting("Shape", TrapShape.BASIC);
    private final Setting<Boolean>   antiSufo   = bool("AntiSufo",    true); // don't place in head
    private final Setting<Boolean>   onlyAir    = bool("OnlyAir",     true); // skip filled spots

    // ── Placement ─────────────────────────────────────────────────────────────
    private final Setting<Integer>   delay      = intSetting("Delay",  2, 0, 10); // ticks between placements
    private final Setting<Integer>   perTick    = intSetting("PerTick", 2, 1, 8);  // blocks per tick
    private final Setting<Boolean>   rotate     = bool("Rotate",       true);
    private final Setting<Boolean>   swing      = bool("Swing",        true);
    private final Setting<Boolean>   strict     = bool("Strict",       false); // only place on solid faces

    // ── Block choice ──────────────────────────────────────────────────────────
    private final Setting<Boolean>   autoSwitch = bool("AutoSwitch",   false); // switch to best block
    private final Setting<Boolean>   switchBack = bool("SwitchBack",   true);  // switch back after

    private int      tickTimer    = 0;
    private int      savedSlot    = -1;
    private BlockPos lastTarget   = null;

    public AutoTrapModule() {
        super("AutoTrap", "Automatically traps nearby players.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        tickTimer  = 0;
        savedSlot  = -1;
        lastTarget = null;
    }

    @Override
    public void onDisable() {
        // Switch back to original slot
        if (savedSlot != -1 && switchBack.getValue()) {
            mc.player.inventory.currentItem = savedSlot;
            savedSlot = -1;
        }
    }

    // ─── Main loop ────────────────────────────────────────────────────────────

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        tickTimer++;
        if (tickTimer < delay.getValue()) return;
        tickTimer = 0;

        // Find target
        EntityPlayer target = getTarget();
        if (target == null) return;

        // Find a valid block in hotbar
        int blockSlot = getBlockSlot();
        if (blockSlot == -1) return;

        // Switch to block slot if needed
        if (autoSwitch.getValue() && blockSlot != mc.player.inventory.currentItem) {
            if (savedSlot == -1) savedSlot = mc.player.inventory.currentItem;
            mc.player.inventory.currentItem = blockSlot;
        }

        // Verify we're holding a block
        ItemStack held = mc.player.getHeldItemMainhand();
        if (!(held.getItem() instanceof ItemBlock)) return;

        BlockPos targetPos = new BlockPos(target.posX, target.posY, target.posZ);
        List<BlockPos> positions = getTrapPositions(targetPos);

        int placed = 0;
        for (BlockPos pos : positions) {
            if (placed >= perTick.getValue()) break;

            if (onlyAir.getValue() && !mc.world.getBlockState(pos).getBlock().equals(Blocks.AIR)) {
                continue;
            }

            // Anti-suffocation — skip the head block
            if (antiSufo.getValue() && pos.equals(targetPos.up())) continue;

            if (placeBlock(pos)) placed++;
        }

        // Switch back if done
        if (autoSwitch.getValue() && savedSlot != -1 && positions.isEmpty()) {
            mc.player.inventory.currentItem = savedSlot;
            savedSlot = -1;
        }
    }

    // ─── Trap shapes ──────────────────────────────────────────────────────────

    private List<BlockPos> getTrapPositions(BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();

        switch (shape.getValue()) {

            case BASIC:
                // 4 sides + 2 above head — classic box trap
                positions.add(origin.north());
                positions.add(origin.south());
                positions.add(origin.east());
                positions.add(origin.west());
                positions.add(origin.up(2));
                positions.add(origin.up(3));
                break;

            case FULL:
                // Full 3x3 cage — floor, walls and ceiling
                // Bottom ring
                positions.add(origin.north());
                positions.add(origin.south());
                positions.add(origin.east());
                positions.add(origin.west());
                // Middle ring
                positions.add(origin.up().north());
                positions.add(origin.up().south());
                positions.add(origin.up().east());
                positions.add(origin.up().west());
                // Roof
                positions.add(origin.up(2));
                positions.add(origin.up(3));
                // Corners
                positions.add(origin.north().east());
                positions.add(origin.north().west());
                positions.add(origin.south().east());
                positions.add(origin.south().west());
                break;

            case SURROUND:
                // Full ring around feet only — common crystal pvp pattern
                positions.add(origin.north());
                positions.add(origin.south());
                positions.add(origin.east());
                positions.add(origin.west());
                positions.add(origin.north().east());
                positions.add(origin.north().west());
                positions.add(origin.south().east());
                positions.add(origin.south().west());
                break;

            case FEET_ONLY:
                // Just the four cardinal feet blocks
                positions.add(origin.north());
                positions.add(origin.south());
                positions.add(origin.east());
                positions.add(origin.west());
                break;

            case CROWN:
                // Roof + 4 diagonal pillars coming down — traps from above
                positions.add(origin.up(3));
                positions.add(origin.up(3).north());
                positions.add(origin.up(3).south());
                positions.add(origin.up(3).east());
                positions.add(origin.up(3).west());
                positions.add(origin.up(2).north());
                positions.add(origin.up(2).south());
                positions.add(origin.up(2).east());
                positions.add(origin.up(2).west());
                positions.add(origin.north());
                positions.add(origin.south());
                positions.add(origin.east());
                positions.add(origin.west());
                break;
        }

        // Sort by distance to player so we place closest blocks first (more reliable)
        positions.sort(Comparator.comparingDouble(pos ->
            mc.player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
        ));

        return positions;
    }

    // ─── Block placement ──────────────────────────────────────────────────────

    private boolean placeBlock(BlockPos pos) {
        // Find an adjacent solid face to place against
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(face);

            if (!mc.world.getBlockState(neighbor).isSideSolid(
                    mc.world, neighbor, face.getOpposite())) {
                if (strict.getValue()) continue;
            }

            // Check the neighbor isn't also air (need something to place against)
            if (mc.world.getBlockState(neighbor).getBlock() == Blocks.AIR) continue;

            // Check reach
            Vec3d hitVec = new Vec3d(
                pos.getX() + 0.5 + face.getXOffset() * 0.5,
                pos.getY() + 0.5 + face.getYOffset() * 0.5,
                pos.getZ() + 0.5 + face.getZOffset() * 0.5
            );

            if (mc.player.getDistance(hitVec.x, hitVec.y, hitVec.z) > range.getValue()) {
                continue;
            }

            if (rotate.getValue()) {
                faceVec(hitVec);
            }

            // Send placement packet
            mc.player.connection.sendPacket(
                new CPacketPlayerTryUseItemOnBlock(
                    neighbor,
                    face.getOpposite(),
                    EnumHand.MAIN_HAND,
                    (float)(hitVec.x - neighbor.getX()),
                    (float)(hitVec.y - neighbor.getY()),
                    (float)(hitVec.z - neighbor.getZ())
                )
            );

            if (swing.getValue()) {
                mc.player.connection.sendPacket(
                    new CPacketAnimation(EnumHand.MAIN_HAND)
                );
            }

            return true;
        }

        return false;
    }

    // ─── Target finding ───────────────────────────────────────────────────────

    private EntityPlayer getTarget() {
        EntityPlayer best = null;
        double bestDist   = range.getValue();

        for (Object obj : mc.world.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.player) continue;

            double dist = mc.player.getDistanceTo(p);
            if (dist > range.getValue()) continue;

            if (closest.getValue()) {
                if (dist < bestDist) {
                    bestDist = dist;
                    best     = p;
                }
            } else {
                // Lowest health target
                if (best == null || p.getHealth() < best.getHealth()) {
                    best = p;
                }
            }
        }

        return best;
    }

    // ─── Hotbar block finder ──────────────────────────────────────────────────

    private int getBlockSlot() {
        // Prefer obsidian, then any solid block
        int fallback = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ItemBlock)) continue;

            Block block = ((ItemBlock) stack.getItem()).getBlock();

            // Prioritise hard blocks
            if (block == Blocks.OBSIDIAN
             || block == Blocks.ENDER_CHEST
             || block == Blocks.ANVIL) {
                return i;
            }

            if (fallback == -1 && block.getDefaultState().isFullBlock()) {
                fallback = i;
            }
        }

        return fallback;
    }

    // ─── Rotation helper ─────────────────────────────────────────────────────

    private void faceVec(Vec3d vec) {
        double dx = vec.x - mc.player.posX;
        double dy = vec.y - (mc.player.posY + mc.player.getEyeHeight());
        double dz = vec.z - mc.player.posZ;

        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        mc.player.rotationYaw      = yaw;
        mc.player.rotationPitch    = pitch;
        mc.player.rotationYawHead  = yaw;
        mc.player.renderYawOffset  = yaw;
    }
}
