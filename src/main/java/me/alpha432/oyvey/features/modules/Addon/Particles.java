package me.alpha432.oyvey.features.modules.addon;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticlesModule extends Module {

    public enum ParticleType { STARS, HEARTS, SNOWFLAKES, CIRCLES, SPARKLES, BUTTERFLIES, MUSIC_NOTES, DIAMONDS }
    public enum SpawnMode    { SPHERE, RAIN, ORBIT, FOUNTAIN, VORTEX }

    // ── Type & Spawn ──────────────────────────────────────────────────────────
    private final Setting<ParticleType> particleType = enumSetting("Particle",  ParticleType.STARS);
    private final Setting<SpawnMode>    spawnMode    = enumSetting("SpawnMode", SpawnMode.SPHERE);

    // ── Color ─────────────────────────────────────────────────────────────────
    private final Setting<Boolean> rainbow   = bool("Rainbow",   true);
    private final Setting<Integer> red       = intSetting("Red",   255, 0, 255);
    private final Setting<Integer> green     = intSetting("Green", 255, 0, 255);
    private final Setting<Integer> blue      = intSetting("Blue",  255, 0, 255);
    private final Setting<Integer> alpha     = intSetting("Alpha", 200, 0, 255);

    // ── Behaviour ─────────────────────────────────────────────────────────────
    private final Setting<Integer> count     = intSetting("Count",    80,  5,  200);
    private final Setting<Double>  speed     = number("Speed",        1.0, 0.1, 5.0);
    private final Setting<Double>  size      = number("Size",         0.1, 0.02, 0.5);
    private final Setting<Double>  spread    = number("Spread",       3.0, 0.5, 10.0);
    private final Setting<Double>  yHeight   = number("Height",       3.0, 0.5, 10.0);
    private final Setting<Boolean> fade      = bool("Fade",           true);
    private final Setting<Boolean> twinkle   = bool("Twinkle",        true);
    private final Setting<Boolean> rotate    = bool("Rotate",         true);
    private final Setting<Boolean> seeThru   = bool("SeeThru",        true);

    // ─────────────────────────────────────────────────────────────────────────

    private static class Particle {
        double x, y, z;           // offset from player
        double vx, vy, vz;        // velocity
        float  hue;               // for rainbow
        float  life;              // 0.0 -> 1.0
        float  lifeSpeed;         // how fast it ages
        float  rotation;          // spin angle
        float  rotSpeed;          // spin speed
        float  twinklePhase;      // for brightness oscillation
        double orbitAngle;        // for orbit mode
        double orbitRadius;

        Particle() { reset(true); }

        void reset(boolean randomLife) {
            Random r = new Random();
            hue          = r.nextFloat();
            life         = randomLife ? r.nextFloat() : 0f;
            lifeSpeed    = 0.003f + r.nextFloat() * 0.005f;
            rotation     = r.nextFloat() * 360f;
            rotSpeed     = (r.nextFloat() - 0.5f) * 4f;
            twinklePhase = r.nextFloat() * (float)(Math.PI * 2);
            orbitAngle   = r.nextDouble() * Math.PI * 2;
            orbitRadius  = 0.5 + r.nextDouble() * 2.0;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Random         rng       = new Random();
    private float                globalHue = 0f;
    private int                  tickCount = 0;

    public ParticlesModule() {
        super("Particles", "Spawns customizable particles around you.", Category.ADDON);
    }

    @Override
    public void onEnable() {
        particles.clear();
        for (int i = 0; i < count.getValue(); i++) {
            Particle p = new Particle();
            spawnParticle(p, true);
            particles.add(p);
        }
    }

    // ─── Update positions every tick ─────────────────────────────────────────

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        globalHue += 0.005f;
        if (globalHue >= 1f) globalHue -= 1f;
        tickCount++;

        // Adjust pool size to match count setting
        while (particles.size() < count.getValue()) {
            Particle p = new Particle();
            spawnParticle(p, true);
            particles.add(p);
        }
        while (particles.size() > count.getValue()) {
            particles.remove(particles.size() - 1);
        }

        double spd = speed.getValue() * 0.02;

        for (Particle p : particles) {
            p.life += p.lifeSpeed * speed.getValue();
            p.rotation += p.rotSpeed * speed.getValue();
            p.twinklePhase += 0.08f * speed.getValue().floatValue();

            switch (spawnMode.getValue()) {
                case SPHERE:
                    // Float around in a sphere, drift upward slightly
                    p.x  += p.vx * spd;
                    p.y  += p.vy * spd + 0.005 * speed.getValue();
                    p.z  += p.vz * spd;
                    break;

                case RAIN:
                    // Fall downward from above
                    p.y  -= spd * 1.5;
                    p.x  += Math.sin(tickCount * 0.05 + p.twinklePhase) * 0.005;
                    break;

                case ORBIT:
                    // Circular orbit around player
                    p.orbitAngle += spd * 0.5;
                    p.x = Math.cos(p.orbitAngle) * p.orbitRadius;
                    p.z = Math.sin(p.orbitAngle) * p.orbitRadius;
                    p.y += Math.sin(p.twinklePhase * 0.3) * 0.005;
                    break;

                case FOUNTAIN:
                    // Shoot up then arc outward
                    p.vy -= 0.003 * speed.getValue(); // gravity
                    p.x  += p.vx * spd;
                    p.y  += p.vy * spd;
                    p.z  += p.vz * spd;
                    break;

                case VORTEX:
                    // Spiral tornado shape
                    p.orbitAngle += spd * (1.0 + (p.y / yHeight.getValue()));
                    double vortexR = (p.orbitRadius * (p.y / yHeight.getValue()));
                    p.x = Math.cos(p.orbitAngle) * Math.max(0.1, vortexR);
                    p.z = Math.sin(p.orbitAngle) * Math.max(0.1, vortexR);
                    p.y += spd * 0.8;
                    break;
            }

            // Respawn dead particles
            if (p.life >= 1.0f || isOutOfBounds(p)) {
                spawnParticle(p, false);
            }
        }
    }

    private void spawnParticle(Particle p, boolean randomLife) {
        double sp = spread.getValue();
        double h  = yHeight.getValue();

        p.reset(randomLife);

        switch (spawnMode.getValue()) {
            case SPHERE:
                p.x = (rng.nextDouble() - 0.5) * sp * 2;
                p.y =  rng.nextDouble() * h;
                p.z = (rng.nextDouble() - 0.5) * sp * 2;
                p.vx = (rng.nextDouble() - 0.5) * 0.3;
                p.vy = (rng.nextDouble() - 0.5) * 0.3;
                p.vz = (rng.nextDouble() - 0.5) * 0.3;
                break;

            case RAIN:
                p.x = (rng.nextDouble() - 0.5) * sp * 2;
                p.y = h;
                p.z = (rng.nextDouble() - 0.5) * sp * 2;
                p.vx = 0; p.vy = 0; p.vz = 0;
                break;

            case ORBIT:
                p.orbitRadius = 0.8 + rng.nextDouble() * (sp - 0.8);
                p.orbitAngle  = rng.nextDouble() * Math.PI * 2;
                p.y = 0.5 + rng.nextDouble() * h;
                p.x = Math.cos(p.orbitAngle) * p.orbitRadius;
                p.z = Math.sin(p.orbitAngle) * p.orbitRadius;
                break;

            case FOUNTAIN:
                p.x = (rng.nextDouble() - 0.5) * 0.3;
                p.y = 0.5;
                p.z = (rng.nextDouble() - 0.5) * 0.3;
                double angle = rng.nextDouble() * Math.PI * 2;
                double power = 0.3 + rng.nextDouble() * 0.4;
                p.vx = Math.cos(angle) * power * (sp / 3.0);
                p.vy = 0.5 + rng.nextDouble() * 0.5;
                p.vz = Math.sin(angle) * power * (sp / 3.0);
                break;

            case VORTEX:
                p.orbitRadius = 0.2 + rng.nextDouble() * sp;
                p.orbitAngle  = rng.nextDouble() * Math.PI * 2;
                p.y = 0;
                p.x = Math.cos(p.orbitAngle) * p.orbitRadius;
                p.z = Math.sin(p.orbitAngle) * p.orbitRadius;
                break;
        }
    }

    private boolean isOutOfBounds(Particle p) {
        double sp = spread.getValue();
        double h  = yHeight.getValue();
        return Math.abs(p.x) > sp * 2 + 2
            || Math.abs(p.z) > sp * 2 + 2
            || p.y > h + 2
            || p.y < -2;
    }

    // ─── Render ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (nullCheck()) return;
        if (particles.isEmpty()) return;

        RenderManager rm = mc.getRenderManager();
        double cx = rm.viewerPosX;
        double cy = rm.viewerPosY;
        double cz = rm.viewerPosZ;

        double px = mc.player.posX - cx;
        double py = mc.player.posY - cy;
        double pz = mc.player.posZ - cz;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();

        if (seeThru.getValue()) GL11.glDepthFunc(GL11.GL_ALWAYS);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (Particle p : particles) {
            double rx = px + p.x;
            double ry = py + p.y;
            double rz = pz + p.z;

            float agePct  = 1f - p.life;
            float twinkle = twinkle.getValue()
                ? (float)(0.6 + 0.4 * Math.sin(p.twinklePhase))
                : 1f;
            float fadeAlpha = fade.getValue() ? agePct : 1f;
            float finalAlpha = fadeAlpha * twinkle;

            Color c = getParticleColor(p);
            int a = (int)(c.getAlpha() * finalAlpha);

            GlStateManager.pushMatrix();
            GlStateManager.translate(rx, ry, rz);

            // Billboard: always face camera
            GL11.glRotatef(-mc.getRenderManager().playerViewY, 0, 1, 0);
            GL11.glRotatef( mc.getRenderManager().playerViewX, 1, 0, 0);

            if (rotate.getValue()) {
                GL11.glRotatef(p.rotation, 0, 0, 1);
            }

            double s = size.getValue();

            setColor(c, Math.max(0, Math.min(255, a)));

            switch (particleType.getValue()) {
                case STARS:       drawStar(s);       break;
                case HEARTS:      drawHeart(s);      break;
                case SNOWFLAKES:  drawSnowflake(s);  break;
                case CIRCLES:     drawCircle(s);     break;
                case SPARKLES:    drawSparkle(s);    break;
                case BUTTERFLIES: drawButterfly(s);  break;
                case MUSIC_NOTES: drawMusicNote(s);  break;
                case DIAMONDS:    drawDiamond(s);    break;
            }

            GlStateManager.popMatrix();
        }

        if (seeThru.getValue()) GL11.glDepthFunc(GL11.GL_LEQUAL);

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    // ─── Particle Shapes ──────────────────────────────────────────────────────

    private void drawStar(double s) {
        // 5-pointed star
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0); // center
        for (int i = 0; i <= 10; i++) {
            double angle = Math.toRadians(i * 36 - 90);
            double r     = (i % 2 == 0) ? s : s * 0.4;
            GL11.glVertex3d(Math.cos(angle) * r, Math.sin(angle) * r, 0);
        }
        GL11.glEnd();
    }

    private void drawHeart(double s) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, -s * 0.5, 0);
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            double t  = (2 * Math.PI * i) / steps;
            double hx = s * 0.8 * Math.pow(Math.sin(t), 3);
            double hy = s * 0.7 * (0.8125 * Math.cos(t)
                                 - 0.3125 * Math.cos(2 * t)
                                 - 0.125  * Math.cos(3 * t)
                                 - 0.0625 * Math.cos(4 * t));
            GL11.glVertex3d(hx, hy, 0);
        }
        GL11.glEnd();
    }

    private void drawSnowflake(double s) {
        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINES);
        // 6 arms
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(i * 30);
            double ex = Math.cos(angle) * s;
            double ey = Math.sin(angle) * s;
            GL11.glVertex3d(0,  0,  0);
            GL11.glVertex3d(ex, ey, 0);
            // Branches on each arm
            for (int b = 1; b <= 2; b++) {
                double bFrac = b / 3.0;
                double bx = ex * bFrac;
                double by = ey * bFrac;
                double bLen = s * 0.3;
                double bAngle1 = angle + Math.PI / 4;
                double bAngle2 = angle - Math.PI / 4;
                GL11.glVertex3d(bx, by, 0);
                GL11.glVertex3d(bx + Math.cos(bAngle1) * bLen, by + Math.sin(bAngle1) * bLen, 0);
                GL11.glVertex3d(bx, by, 0);
                GL11.glVertex3d(bx + Math.cos(bAngle2) * bLen, by + Math.sin(bAngle2) * bLen, 0);
            }
        }
        GL11.glEnd();
    }

    private void drawCircle(double s) {
        int segs = 24;
        // Filled
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0);
        for (int i = 0; i <= segs; i++) {
            double a = (2 * Math.PI * i) / segs;
            GL11.glVertex3d(Math.cos(a) * s, Math.sin(a) * s, 0);
        }
        GL11.glEnd();
        // Ring outline
        GL11.glLineWidth(1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segs; i++) {
            double a = (2 * Math.PI * i) / segs;
            GL11.glVertex3d(Math.cos(a) * s, Math.sin(a) * s, 0);
        }
        GL11.glEnd();
    }

    private void drawSparkle(double s) {
        // 4-point sparkle / cross burst
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0);
        for (int i = 0; i <= 8; i++) {
            double angle = Math.toRadians(i * 45);
            double r     = (i % 2 == 0) ? s : s * 0.15;
            GL11.glVertex3d(Math.cos(angle) * r, Math.sin(angle) * r, 0);
        }
        GL11.glEnd();
    }

    private void drawButterfly(double s) {
        // Two wing pairs using bezier-like quads
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        // Left upper wing
        GL11.glVertex3d(0, 0, 0);
        GL11.glVertex3d(-s * 0.1, s * 0.2, 0);
        GL11.glVertex3d(-s * 0.8, s * 0.6, 0);
        GL11.glVertex3d(-s * 0.9, 0, 0);
        GL11.glVertex3d(-s * 0.5, -s * 0.2, 0);
        GL11.glVertex3d(0, 0, 0);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        // Right upper wing
        GL11.glVertex3d(0, 0, 0);
        GL11.glVertex3d(s * 0.1, s * 0.2, 0);
        GL11.glVertex3d(s * 0.8, s * 0.6, 0);
        GL11.glVertex3d(s * 0.9, 0, 0);
        GL11.glVertex3d(s * 0.5, -s * 0.2, 0);
        GL11.glVertex3d(0, 0, 0);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        // Lower wings (smaller)
        GL11.glVertex3d(0, 0, 0);
        GL11.glVertex3d(-s * 0.1, -s * 0.1, 0);
        GL11.glVertex3d(-s * 0.5, -s * 0.7, 0);
        GL11.glVertex3d(-s * 0.2, -s * 0.5, 0);
        GL11.glVertex3d(0, 0, 0);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0);
        GL11.glVertex3d(s * 0.1, -s * 0.1, 0);
        GL11.glVertex3d(s * 0.5, -s * 0.7, 0);
        GL11.glVertex3d(s * 0.2, -s * 0.5, 0);
        GL11.glVertex3d(0, 0, 0);
        GL11.glEnd();
    }

    private void drawMusicNote(double s) {
        // Note head (filled oval)
        int segs = 16;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0);
        for (int i = 0; i <= segs; i++) {
            double a = (2 * Math.PI * i) / segs;
            GL11.glVertex3d(Math.cos(a) * s * 0.5,
                            Math.sin(a) * s * 0.35, 0);
        }
        GL11.glEnd();

        // Stem going up-right
        GL11.glLineWidth(Math.max(1f, (float)(s * 15)));
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(s * 0.45, 0,      0);
        GL11.glVertex3d(s * 0.45, s * 1.4, 0);
        GL11.glEnd();

        // Flag at top
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(s * 0.45, s * 1.4, 0);
        GL11.glVertex3d(s * 0.45, s * 1.1, 0);
        GL11.glVertex3d(s * 0.9,  s * 1.2, 0);
        GL11.glVertex3d(s * 0.8,  s * 1.4, 0);
        GL11.glEnd();
    }

    private void drawDiamond(double s) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3d(0, 0, 0);           // center
        GL11.glVertex3d(0,     s,     0);   // top
        GL11.glVertex3d(s,     s * 0.3, 0); // upper right
        GL11.glVertex3d(s * 0.6, -s,   0); // lower right
        GL11.glVertex3d(-s * 0.6, -s,  0); // lower left
        GL11.glVertex3d(-s,    s * 0.3, 0); // upper left
        GL11.glVertex3d(0,     s,     0);   // close
        GL11.glEnd();

        // Facet lines
        GL11.glLineWidth(1.0f);
        setColorDarker();
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(0,      s,      0);
        GL11.glVertex3d(0,      0,      0);
        GL11.glVertex3d(s,      s * 0.3, 0);
        GL11.glVertex3d(0,      0,      0);
        GL11.glVertex3d(-s,     s * 0.3, 0);
        GL11.glVertex3d(0,      0,      0);
        GL11.glEnd();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Color getParticleColor(Particle p) {
        if (rainbow.getValue()) {
            float hue = (p.hue + globalHue) % 1f;
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
            a            / 255f
        );
    }

    private void setColorDarker() {
        GL11.glColor4f(0, 0, 0, 0.4f);
    }
}
