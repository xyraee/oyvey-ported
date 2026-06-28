package me.alpha432.oyvey.features.modules.addon;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TrailModule extends Module {

    // ── Texture type ──────────────────────────────────────────────────────────
    public enum TrailType { LINE, RIBBON, STARS, CIRCLES, SMOKE, HEARTS }
    private final Setting<TrailType> type = enumSetting("Type", TrailType.RIBBON);

    // ── Color ─────────────────────────────────────────────────────────────────
    private final Setting<Boolean> rainbow    = bool("Rainbow",    false);
    private final Setting<Integer> red        = intSetting("Red",   255, 0, 255);
    private final Setting<Integer> green      = intSetting("Green", 100, 0, 255);
    private final Setting<Integer> blue       = intSetting("Blue",  200, 0, 255);
    private final Setting<Integer> alpha      = intSetting("Alpha", 200, 0, 255);

    // ── Behaviour ─────────────────────────────────────────────────────────────
    private final Setting<Double>  speed      = number("Speed",     1.0,  0.1, 5.0);
    private final Setting<Double>  width      = number("Width",     0.15, 0.05, 1.0);
    private final Setting<Integer> maxPoints  = intSetting("Length", 60,  10, 200);
    private final Setting<Double>  yOffset    = number("YOffset",   0.9, 0.0, 2.0);
    private final Setting<Boolean> fade       = bool("Fade",        true);
    private final Setting<Boolean> glow       = bool("Glow",        false);

    // ─────────────────────────────────────────────────────────────────────────

    private static class TrailPoint {
        double x, y, z;
        long   time;
        float  hue; // for per-point rainbow

        TrailPoint(double x, double y, double z, float hue) {
            this.x = x; this.y = y; this.z = z;
            this.time = System.currentTimeMillis();
            this.hue  = hue;
        }
    }

    private final List<TrailPoint> points = new ArrayList<>();
    private float   rainbowHue  = 0f;
    private double  lastX, lastY, lastZ;
    private long    lastAdd     = 0;

    public TrailModule() {
        super("Trail", "Leaves a trail behind the player.", Category.ADDON);
    }

    @Override
    public void onEnable() {
        points.clear();
    }

    // ─── Collect player positions every N ms based on speed ──────────────────

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        rainbowHue += 0.01f;
        if (rainbowHue >= 1f) rainbowHue -= 1f;

        // Interval between points — lower speed = more gap = sparser trail
        long interval = (long)(200 / speed.getValue());
        if (System.currentTimeMillis() - lastAdd < interval) return;
        lastAdd = System.currentTimeMillis();

        double px = mc.player.posX;
        double py = mc.player.posY + yOffset.getValue();
        double pz = mc.player.posZ;

        // Skip if player hasn't moved meaningfully
        double dist = Math.sqrt(
            Math.pow(px - lastX, 2) +
            Math.pow(py - lastY, 2) +
            Math.pow(pz - lastZ, 2)
        );
        if (dist < 0.05) return;

        lastX = px; lastY = py; lastZ = pz;

        points.add(new TrailPoint(px, py, pz, rainbowHue));

        // Trim to max length
        while (points.size() > maxPoints.getValue()) {
            points.remove(0);
        }
    }

    // ─── Render ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (nullCheck()) return;
        if (points.size() < 2) return;

        RenderManager rm = mc.getRenderManager();
        double cx = rm.viewerPosX;
        double cy = rm.viewerPosY;
        double cz = rm.viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();     // render through world
        GlStateManager.depthMask(false);

        if (glow.getValue()) {
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        }

        switch (type.getValue()) {
            case LINE:    renderLine(cx, cy, cz);    break;
            case RIBBON:  renderRibbon(cx, cy, cz);  break;
            case STARS:   renderStars(cx, cy, cz);   break;
            case CIRCLES: renderCircles(cx, cy, cz); break;
            case SMOKE:   renderSmoke(cx, cy, cz);   break;
            case HEARTS:  renderHearts(cx, cy, cz);  break;
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();

        if (glow.getValue()) GL11.glDisable(GL11.GL_LINE_SMOOTH);

        GlStateManager.popMatrix();
    }

    // ─── LINE ─────────────────────────────────────────────────────────────────

    private void renderLine(double cx, double cy, double cz) {
        GL11.glLineWidth(width.getValue().floatValue() * 10f);
        GL11.glBegin(GL11.GL_LINE_STRIP);

        for (int i = 0; i < points.size(); i++) {
            TrailPoint p = points.get(i);
            float agePct = (float) i / points.size();
            Color c = pointColor(p, agePct);
            setColor(c, fade.getValue() ? (int)(c.getAlpha() * agePct) : c.getAlpha());
            GL11.glVertex3d(p.x - cx, p.y - cy, p.z - cz);
        }

        GL11.glEnd();
    }

    // ─── RIBBON ───────────────────────────────────────────────────────────────

    private void renderRibbon(double cx, double cy, double cz) {
        double w = width.getValue();

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);

        for (int i = 0; i < points.size(); i++) {
            TrailPoint p = points.get(i);
            float agePct = (float) i / points.size();

            // Direction along the trail for ribbon normal
            double dx = 0, dz = 0;
            if (i < points.size() - 1) {
                dx = points.get(i + 1).x - p.x;
                dz = points.get(i + 1).z - p.z;
            } else if (i > 0) {
                dx = p.x - points.get(i - 1).x;
                dz = p.z - points.get(i - 1).z;
            }

            // Perpendicular on XZ plane
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) { dx /= len; dz /= len; }
            double nx = -dz * w;
            double nz =  dx * w;

            Color c = pointColor(p, agePct);
            int a = fade.getValue() ? (int)(c.getAlpha() * agePct) : c.getAlpha();
            setColor(c, a);

            GL11.glVertex3d((p.x - cx) + nx, p.y - cy, (p.z - cz) + nz);
            GL11.glVertex3d((p.x - cx) - nx, p.y - cy, (p.z - cz) - nz);
        }

        GL11.glEnd();
    }

    // ─── STARS ────────────────────────────────────────────────────────────────

    private void renderStars(double cx, double cy, double cz) {
        for (int i = 0; i < points.size(); i++) {
            TrailPoint p = points.get(i);
            float agePct  = (float) i / points.size();
            double size   = width.getValue() * agePct * 0.4;

            Color c = pointColor(p, agePct);
            int a = fade.getValue() ? (int)(c.getAlpha() * agePct) : c.getAlpha();
            setColor(c, a);

            double rx = p.x - cx;
            double ry = p.y - cy;
            double rz = p.z - cz;

            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINES);
            // 6-pointed star via 3 crossing lines
            for (int s = 0; s < 3; s++) {
                double angle = Math.toRadians(s * 60);
                double cos = Math.cos(angle) * size;
                double sin = Math.sin(angle) * size;
                GL11.glVertex3d(rx - cos, ry - sin, rz);
                GL11.glVertex3d(rx + cos, ry + sin, rz);
            }
            GL11.glEnd();
        }
    }

    // ─── CIRCLES ──────────────────────────────────────────────────────────────

    private void renderCircles(double cx, double cy, double cz) {
        int segs = 16;
        for (int i = 0; i < points.size(); i++) {
            TrailPoint p = points.get(i);
            float agePct = (float) i / points.size();
            double r     = width.getValue() * agePct * 0.5;

            Color c = pointColor(p, agePct);
            int a = fade.getValue() ? (int)(c.getAlpha() * agePct) : c.getAlpha();
            setColor(c, a);

            GL11.glLineWidth(1.2f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int s = 0; s < segs; s++) {
                double angle = (2 * Math.PI * s) / segs;
                GL11.glVertex3d(
                    (p.x - cx) + Math.cos(angle) * r,
                     p.y - cy,
                    (p.z - cz) + Math.sin(angle) * r
                );
            }
            GL11.glEnd();
        }
    }

    // ─── SMOKE ────────────────────────────────────────────────────────────────

    private void renderSmoke(double cx, double cy, double cz) {
        for (int i = 0; i < points.size(); i++) {
            TrailPoint p = points.get(i);
            float agePct = (float) i / points.size();
            double size  = width.getValue() * agePct * 0.6;

            // Smoke puffs drift upward slightly over age
            double driftY = (1.0 - agePct) * 0.3;

            Color c = pointColor(p, agePct);
            int a = fade.getValue() ? (int)(c.getAlpha() * agePct * 0.7f) : (int)(c.getAlpha() * 0.7f);
            setColor(c, a);

            double rx = p.x - cx;
            double ry = p.y - cy + driftY;
            double rz = p.z - cz;

            // Billboarded quad for each puff
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3d(rx - size, ry - size, rz);
            GL11.glVertex3d(rx + size, ry - size, rz);
            GL11.glVertex3d(rx + size, ry + size, rz);
            GL11.glVertex3d(rx - size, ry + size, rz);
            GL11.glEnd();
        }
    }

    // ─── HEARTS ───────────────────────────────────────────────────────────────

    private void renderHearts(double cx, double cy, double cz) {
        for (int i = 0; i < points.size(); i++) {
            TrailPoint p = points.get(i);
            float agePct = (float) i / points.size();
            double s     = width.getValue() * agePct * 0.25;

            Color c = pointColor(p, agePct);
            int a = fade.getValue() ? (int)(c.getAlpha() * agePct) : c.getAlpha();
            setColor(c, a);

            double rx = p.x - cx;
            double ry = p.y - cy;
            double rz = p.z - cz;

            // Parametric heart curve on XY plane
            GL11.glBegin(GL11.GL_LINE_LOOP);
            int steps = 32;
            for (int st = 0; st <= steps; st++) {
                double t  = (2 * Math.PI * st) / steps;
                double hx = s * 16 * Math.pow(Math.sin(t), 3);
                double hy = s * (13 * Math.cos(t)
                               -  5 * Math.cos(2 * t)
                               -  2 * Math.cos(3 * t)
                               -      Math.cos(4 * t));
                GL11.glVertex3d(rx + hx, ry + hy, rz);
            }
            GL11.glEnd();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Color pointColor(TrailPoint p, float agePct) {
        if (rainbow.getValue()) {
            float hue = (p.hue + agePct * 0.3f) % 1f;
            return Color.getHSBColor(hue, 1f, 1f);
        }
        return new Color(red.getValue(), green.getValue(),
                         blue.getValue(), alpha.getValue());
    }

    private void setColor(Color c, int a) {
        GL11.glColor4f(
            c.getRed()   / 255f,
            c.getGreen() / 255f,
            c.getBlue()  / 255f,
            Math.max(0, Math.min(255, a)) / 255f
        );
    }
}
