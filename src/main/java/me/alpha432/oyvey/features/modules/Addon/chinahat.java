package me.alpha432.oyvey.features.modules.addon;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.Math;

public class ChinaHatModule extends Module {

    // Size
    private final Setting<Double> radius    = number("Radius",    0.5,  0.1, 2.0);
    private final Setting<Double> height    = number("Height",    0.6,  0.1, 2.0);
    private final Setting<Double> brimSize  = number("BrimSize",  0.15, 0.0, 0.5);
    private final Setting<Double> yOffset   = number("YOffset",   0.3, -1.0, 1.0);
    private final Setting<Integer> segments = intSetting("Segments", 32, 8, 64);

    // Color
    private final Setting<Integer> red     = intSetting("Red",   255, 0, 255);
    private final Setting<Integer> green   = intSetting("Green", 215, 0, 255);
    private final Setting<Integer> blue    = intSetting("Blue",  0,   0, 255);
    private final Setting<Integer> alpha   = intSetting("Alpha", 255, 0, 255);

    // Style
    private final Setting<Boolean> wireframe  = bool("Wireframe",  false);
    private final Setting<Boolean> brim       = bool("Brim",       true);
    private final Setting<Boolean> spin       = bool("Spin",       false);
    private final Setting<Double>  spinSpeed  = number("SpinSpeed", 2.0, 0.1, 10.0);
    private final Setting<Boolean> rainbow    = bool("Rainbow",    false);
    private final Setting<Boolean> outline    = bool("Outline",    true);
    private final Setting<Double>  outlineWidth = number("OutlineWidth", 1.5, 0.5, 4.0);

    private float spinAngle = 0f;

    public ChinaHatModule() {
        super("ChinaHat", "Puts a conical hat on your head.", Category.ADDON);
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Post event) {
        if (nullCheck()) return;
        if (event.getEntityPlayer() != mc.player) return;

        float pt = event.getPartialRenderTick();

        if (spin.getValue()) {
            spinAngle += spinSpeed.getValue() * 0.5f;
            if (spinAngle >= 360f) spinAngle -= 360f;
        }

        GlStateManager.pushMatrix();

        // Move to player head position
        double px = event.getEntityPlayer().lastTickPosX
                + (event.getEntityPlayer().posX - event.getEntityPlayer().lastTickPosX) * pt;
        double py = event.getEntityPlayer().lastTickPosY
                + (event.getEntityPlayer().posY - event.getEntityPlayer().lastTickPosY) * pt;
        double pz = event.getEntityPlayer().lastTickPosZ
                + (event.getEntityPlayer().posZ - event.getEntityPlayer().lastTickPosZ) * pt;

        RenderManager rm = mc.getRenderManager();
        GlStateManager.translate(
            px - rm.viewerPosX,
            py - rm.viewerPosY,
            pz - rm.viewerPosZ
        );

        // Rotate with player's head yaw
        GlStateManager.rotate(-event.getEntityPlayer().rotationYawHead, 0, 1, 0);

        // Spin if enabled
        if (spin.getValue()) {
            GlStateManager.rotate(spinAngle, 0, 1, 0);
        }

        // Move up to sit on top of head (1.8 = player height, 0.2 = head top)
        GlStateManager.translate(0, 1.8 + yOffset.getValue(), 0);

        // GL setup
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        Color hatColor = rainbow.getValue() ? getRainbowColor() : getColor();

        if (wireframe.getValue()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        }

        drawHatCone(hatColor);

        if (brim.getValue()) {
            drawBrim(hatColor);
        }

        if (outline.getValue()) {
            drawOutline(hatColor);
        }

        if (wireframe.getValue()) {
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        }

        // GL restore
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();

        GlStateManager.popMatrix();
    }

    // ─── Cone (main hat body) ─────────────────────────────────────────────────

    private void drawHatCone(Color c) {
        int segs = segments.getValue();
        double r  = radius.getValue();
        double h  = height.getValue();

        setColor(c, c.getAlpha());

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        // Tip of the cone
        GL11.glVertex3d(0, h, 0);

        for (int i = 0; i <= segs; i++) {
            double angle = (2 * Math.PI * i) / segs;
            GL11.glVertex3d(Math.cos(angle) * r, 0, Math.sin(angle) * r);
        }
        GL11.glEnd();

        // Close the base of the cone
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0);
        for (int i = segs; i >= 0; i--) {
            double angle = (2 * Math.PI * i) / segs;
            GL11.glVertex3d(Math.cos(angle) * r, 0, Math.sin(angle) * r);
        }
        GL11.glEnd();
    }

    // ─── Brim (flat ring around base) ─────────────────────────────────────────

    private void drawBrim(Color c) {
        int segs   = segments.getValue();
        double r   = radius.getValue();
        double ext = brimSize.getValue(); // how far brim extends past cone

        // Slightly darker brim so it contrasts
        Color brimColor = c.darker();
        setColor(brimColor, c.getAlpha());

        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segs; i++) {
            double angle = (2 * Math.PI * i) / segs;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            // Inner edge (cone base)
            GL11.glVertex3d(cos * r,       -0.01, sin * r);
            // Outer edge (brim tip)
            GL11.glVertex3d(cos * (r + ext), -0.03, sin * (r + ext));
        }
        GL11.glEnd();
    }

    // ─── Outline ──────────────────────────────────────────────────────────────

    private void drawOutline(Color c) {
        int segs = segments.getValue();
        double r = radius.getValue();
        double h = height.getValue();

        Color outlineColor = new Color(0, 0, 0, 200);
        setColor(outlineColor, 200);
        GL11.glLineWidth(outlineWidth.getValue().floatValue());

        // Base circle outline
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segs; i++) {
            double angle = (2 * Math.PI * i) / segs;
            GL11.glVertex3d(Math.cos(angle) * r, 0, Math.sin(angle) * r);
        }
        GL11.glEnd();

        // Lines from base to tip
        GL11.glBegin(GL11.GL_LINES);
        int ridges = 8; // number of ridge lines up the cone
        for (int i = 0; i < ridges; i++) {
            double angle = (2 * Math.PI * i) / ridges;
            GL11.glVertex3d(Math.cos(angle) * r, 0,  Math.sin(angle) * r);
            GL11.glVertex3d(0, h, 0);
        }
        GL11.glEnd();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void setColor(Color c, int a) {
        GL11.glColor4f(
            c.getRed()   / 255f,
            c.getGreen() / 255f,
            c.getBlue()  / 255f,
            a            / 255f
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

    private Color getRainbowColor() {
        float hue = (System.currentTimeMillis() % 2000) / 2000f;
        return Color.getHSBColor(hue, 1f, 1f);
    }
}
