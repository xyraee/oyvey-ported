package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class ESPModule extends Module {

    private final Setting<Boolean> box2D     = bool("2DBox", true);
    private final Setting<Boolean> box3D     = bool("3DBox", false);
    private final Setting<Boolean> skeleton  = bool("Skeleton", false);
    private final Setting<Boolean> tracers   = bool("Tracers", false);
    private final Setting<Boolean> seeThru   = bool("SeeThru", true);

    // Color settings
    private final Setting<Integer> red   = intSetting("Red",   255, 0, 255);
    private final Setting<Integer> green = intSetting("Green", 0,   0, 255);
    private final Setting<Integer> blue  = intSetting("Blue",  0,   0, 255);
    private final Setting<Integer> alpha = intSetting("Alpha", 255, 0, 255);

    public ESPModule() {
        super("ESP", "Renders players through walls.", Category.RENDER);
    }

    // ─── 2D Box (screen-space overlay) ────────────────────────────────────────

    @Override
    public void onRender2D(float partialTicks) {
        if (!box2D.getValue()) return;
        if (nullCheck()) return;

        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) continue;
            if (entity == mc.player) continue;

            float[] box = get2DBox(entity, partialTicks);
            if (box == null) continue;

            float x = box[0], y = box[1], w = box[2], h = box[3];
            Color c = getColor();

            // Outer box
            drawRect(x,     y,     w,  1, c);   // top
            drawRect(x,     y + h, w,  1, c);   // bottom
            drawRect(x,     y,     1,  h, c);   // left
            drawRect(x + w, y,     1,  h, c);   // right

            // Health bar on the left
            EntityLivingBase living = (EntityLivingBase) entity;
            float hp    = living.getHealth();
            float maxHp = living.getMaxHealth();
            float pct   = hp / maxHp;
            float barH  = h * pct;

            drawRect(x - 4, y,           2, h,    new Color(0, 0, 0, 180));
            drawRect(x - 4, y + h - barH, 2, barH, healthColor(pct));
        }
    }

    // ─── 3D Box + Skeleton (world-space) ──────────────────────────────────────

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (nullCheck()) return;

        float pt = event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();

        if (seeThru.getValue()) {
            GL11.glDepthFunc(GL11.GL_ALWAYS); // render through walls
        }

        double cx = mc.getRenderManager().viewerPosX;
        double cy = mc.getRenderManager().viewerPosY;
        double cz = mc.getRenderManager().viewerPosZ;

        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityPlayer)) continue;
            if (entity == mc.player) continue;

            EntityPlayer player = (EntityPlayer) entity;
            Color c = getColor();

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * pt - cx;
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * pt - cy;
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * pt - cz;

            if (box3D.getValue()) {
                draw3DBox(x, y, z, player, c, pt);
            }

            if (skeleton.getValue()) {
                drawSkeleton(player, x, y, z, c, pt);
            }

            if (tracers.getValue()) {
                drawTracer(x, y, z, c);
            }
        }

        if (seeThru.getValue()) {
            GL11.glDepthFunc(GL11.GL_LEQUAL); // restore
        }

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ─── 3D Box ───────────────────────────────────────────────────────────────

    private void draw3DBox(double x, double y, double z,
                           EntityPlayer p, Color c, float pt) {
        AxisAlignedBB bb = p.getEntityBoundingBox()
                .offset(-mc.getRenderManager().viewerPosX,
                        -mc.getRenderManager().viewerPosY,
                        -mc.getRenderManager().viewerPosZ);

        GL11.glLineWidth(1.5f);
        setColor(c, 255);
        GL11.glBegin(GL11.GL_LINE_STRIP);

        // Bottom face
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ);

        // Top face
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);

        GL11.glEnd();

        // Vertical pillars
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);
        GL11.glEnd();

        // Filled transparent face
        setColor(c, 30);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);

        GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ);

        GL11.glVertex3d(bb.minX, bb.minY, bb.minZ); GL11.glVertex3d(bb.minX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.minX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.minX, bb.maxY, bb.minZ);

        GL11.glVertex3d(bb.maxX, bb.minY, bb.minZ); GL11.glVertex3d(bb.maxX, bb.minY, bb.maxZ);
        GL11.glVertex3d(bb.maxX, bb.maxY, bb.maxZ); GL11.glVertex3d(bb.maxX, bb.maxY, bb.minZ);
        GL11.glEnd();
    }

    // ─── Skeleton ─────────────────────────────────────────────────────────────

    private void drawSkeleton(EntityPlayer p, double x, double y,
                              double z, Color c, float pt) {
        // Bone endpoints derived from entity height (1.8 blocks)
        double h   = p.height;          // 1.8
        double mid = h * 0.5;           // waist
        double sho = h * 0.75;          // shoulders
        double hip = h * 0.35;          // hips
        double aw  = 0.3;               // arm width
        double lw  = 0.2;               // leg width

        GL11.glLineWidth(1.5f);
        setColor(c, 255);
        GL11.glBegin(GL11.GL_LINES);

        // Spine
        line(x, y + hip, z,   x, y + sho, z);

        // Head
        line(x, y + sho, z,   x, y + h,   z);

        // Left arm
        line(x, y + sho, z,   x - aw, y + mid, z);

        // Right arm
        line(x, y + sho, z,   x + aw, y + mid, z);

        // Left leg
        line(x, y + hip, z,   x - lw, y, z);

        // Right leg
        line(x, y + hip, z,   x + lw, y, z);

        GL11.glEnd();
    }

    private void drawTracer(double x, double y, double z, Color c) {
        GL11.glLineWidth(1.0f);
        setColor(c, 180);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(0, 0, 0); // camera origin (already offset)
        GL11.glVertex3d(x, y, z);
        GL11.glEnd();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private float[] get2DBox(Entity e, float pt) {
        // Project entity AABB corners to screen and find bounding rect
        AxisAlignedBB bb = e.getEntityBoundingBox();
        double[] xs = { bb.minX, bb.maxX };
        double[] ys = { bb.minY, bb.maxY };
        double[] zs = { bb.minZ, bb.maxZ };

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;

        for (double ex : xs) for (double ey : ys) for (double ez : zs) {
            float[] screen = worldToScreen(ex, ey, ez);
            if (screen == null) return null;
            if (screen[0] < minX) minX = screen[0];
            if (screen[1] < minY) minY = screen[1];
            if (screen[0] > maxX) maxX = screen[0];
            if (screen[1] > maxY) maxY = screen[1];
        }

        return new float[]{ minX, minY, maxX - minX, maxY - minY };
    }

    private float[] worldToScreen(double x, double y, double z) {
        // Uses Minecraft's projection — returns null if behind camera
        return RenderUtil.worldToScreen(x, y, z);
    }

    private void line(double x1, double y1, double z1,
                      double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    private void drawRect(float x, float y, float w, float h, Color c) {
        // Your client's existing 2D rect helper
        Render2DUtil.drawRect(x, y, x + w, y + h, c.getRGB());
    }

    private void setColor(Color c, int overrideAlpha) {
        GL11.glColor4f(
            c.getRed()   / 255f,
            c.getGreen() / 255f,
            c.getBlue()  / 255f,
            overrideAlpha / 255f
        );
    }

    private Color getColor() {
        return new Color(
            red.getValue(),
            green.getValue(),
            blue.getValue(),
            alpha.getValue()
        );
    }

    private Color healthColor(float pct) {
        // Green -> Yellow -> Red based on hp %
        if (pct > 0.5f) return new Color(0, 255, 0, 255);
        if (pct > 0.25f) return new Color(255, 165, 0, 255);
        return new Color(255, 0, 0, 255);
    }
}
