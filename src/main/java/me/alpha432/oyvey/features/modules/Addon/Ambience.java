package me.alpha432.oyvey.features.modules.addon;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class AmbienceModule extends Module {

    public enum SkyboxPreset  { CUSTOM, SUNSET, MIDNIGHT, AURORA, BLOOD_MOON, VOID, GALAXY, HEAVEN }
    public enum FogMode       { NONE, LINEAR, EXP, EXP2 }
    public enum TimeMode      { FREEZE, CUSTOM, CYCLE, REAL }

    // ── Skybox ────────────────────────────────────────────────────────────────
    private final Setting<SkyboxPreset> skyPreset   = enumSetting("SkyPreset",  SkyboxPreset.CUSTOM);
    private final Setting<Boolean>      customSky   = bool("CustomSky",  true);
    private final Setting<Integer>      skyRed      = intSetting("SkyRed",    100, 0, 255);
    private final Setting<Integer>      skyGreen    = intSetting("SkyGreen",  150, 0, 255);
    private final Setting<Integer>      skyBlue     = intSetting("SkyBlue",   255, 0, 255);

    // Horizon / void color
    private final Setting<Boolean>      customHorizon = bool("CustomHorizon", true);
    private final Setting<Integer>      horRed      = intSetting("HorRed",    80,  0, 255);
    private final Setting<Integer>      horGreen    = intSetting("HorGreen",  80,  0, 255);
    private final Setting<Integer>      horBlue     = intSetting("HorBlue",   80,  0, 255);

    // ── Fog ───────────────────────────────────────────────────────────────────
    private final Setting<FogMode>      fogMode     = enumSetting("FogMode",   FogMode.LINEAR);
    private final Setting<Boolean>      customFog   = bool("CustomFog",  true);
    private final Setting<Integer>      fogRed      = intSetting("FogRed",    100, 0, 255);
    private final Setting<Integer>      fogGreen    = intSetting("FogGreen",  100, 0, 255);
    private final Setting<Integer>      fogBlue     = intSetting("FogBlue",   200, 0, 255);
    private final Setting<Integer>      fogAlpha    = intSetting("FogAlpha",  200, 0, 255);
    private final Setting<Double>       fogStart    = number("FogStart",   10.0,  0.0, 256.0);
    private final Setting<Double>       fogEnd      = number("FogEnd",     60.0,  1.0, 512.0);
    private final Setting<Double>       fogDensity  = number("FogDensity",  0.02, 0.001, 0.1);
    private final Setting<Boolean>      fogRainbow  = bool("FogRainbow",  false);

    // ── Time ──────────────────────────────────────────────────────────────────
    private final Setting<TimeMode>     timeMode    = enumSetting("TimeMode",  TimeMode.FREEZE);
    private final Setting<Integer>      frozenTime  = intSetting("FrozenTime", 6000, 0, 24000);
    private final Setting<Double>       cycleSpeed  = number("CycleSpeed",  1.0, 0.1, 10.0);

    // ── Sun & Moon ────────────────────────────────────────────────────────────
    private final Setting<Boolean>      hideSun     = bool("HideSun",    false);
    private final Setting<Boolean>      hideMoon    = bool("HideMoon",   false);
    private final Setting<Boolean>      hideStars   = bool("HideStars",  false);
    private final Setting<Double>       sunSize     = number("SunSize",   1.0, 0.1, 5.0);

    // ── Void ──────────────────────────────────────────────────────────────────
    private final Setting<Boolean>      customVoid  = bool("CustomVoid",  false);
    private final Setting<Integer>      voidRed     = intSetting("VoidRed",   0, 0, 255);
    private final Setting<Integer>      voidGreen   = intSetting("VoidGreen", 0, 0, 255);
    private final Setting<Integer>      voidBlue    = intSetting("VoidBlue",  0, 0, 255);

    // ── Brightness & Saturation ───────────────────────────────────────────────
    private final Setting<Double>       brightness  = number("Brightness", 1.0, 0.0, 3.0);
    private final Setting<Double>       saturation  = number("Saturation", 1.0, 0.0, 3.0);
    private final Setting<Boolean>      nightVision = bool("NightVision", false);

    // ── Rainbow sky ───────────────────────────────────────────────────────────
    private final Setting<Boolean>      rainbowSky  = bool("RainbowSky",  false);
    private final Setting<Double>       rainbowSpeed = number("RainbowSpeed", 1.0, 0.1, 5.0);

    private float   rainbowHue  = 0f;
    private long    cycleTime   = 0;

    public AmbienceModule() {
        super("Ambience", "Customizes fog, skybox, time and more.", Category.ADDON);
    }

    // ─── Time control ─────────────────────────────────────────────────────────

    @Override
    public void onUpdate() {
        if (nullCheck()) return;

        rainbowHue += 0.002f * rainbowSpeed.getValue().floatValue();
        if (rainbowHue >= 1f) rainbowHue -= 1f;

        switch (timeMode.getValue()) {
            case FREEZE:
                mc.world.setWorldTime(frozenTime.getValue());
                break;

            case CUSTOM:
                mc.world.setWorldTime(frozenTime.getValue());
                break;

            case CYCLE:
                cycleTime += (long)(cycleSpeed.getValue() * 10);
                mc.world.setWorldTime(cycleTime % 24000);
                break;

            case REAL:
                // Sync to real-world time of day
                java.time.LocalTime now = java.time.LocalTime.now();
                int minutes = now.getHour() * 60 + now.getMinute();
                // Map 0-1440 real minutes to 0-24000 MC ticks
                mc.world.setWorldTime((long)(minutes / 1440.0 * 24000));
                break;
        }
    }

    // ─── Fog color ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onFogColor(EntityViewRenderEvent.FogColors event) {
        if (!isEnabled() || !customFog.getValue()) return;

        Color fog = getFogColor();

        event.setRed(fog.getRed()   / 255f);
        event.setGreen(fog.getGreen() / 255f);
        event.setBlue(fog.getBlue()  / 255f);
    }

    // ─── Fog density ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.RenderFogEvent event) {
        if (!isEnabled()) return;

        if (fogMode.getValue() == FogMode.NONE) {
            GL11.glDisable(GL11.GL_FOG);
            return;
        }

        GL11.glEnable(GL11.GL_FOG);

        switch (fogMode.getValue()) {
            case LINEAR:
                GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_LINEAR);
                GL11.glFogf(GL11.GL_FOG_START, fogStart.getValue().floatValue());
                GL11.glFogf(GL11.GL_FOG_END,   fogEnd.getValue().floatValue());
                break;

            case EXP:
                GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
                GL11.glFogf(GL11.GL_FOG_DENSITY, fogDensity.getValue().floatValue());
                break;

            case EXP2:
                GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP2);
                GL11.glFogf(GL11.GL_FOG_DENSITY, fogDensity.getValue().floatValue());
                break;
        }

        Color fog = getFogColor();
        float[] fogColorArr = {
            fog.getRed()   / 255f,
            fog.getGreen() / 255f,
            fog.getBlue()  / 255f,
            fogAlpha.getValue() / 255f
        };
        GL11.glFogfv(GL11.GL_FOG_COLOR,
            java.nio.FloatBuffer.wrap(fogColorArr));
    }

    // ─── Sky color ────────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onSkyColor(EntityViewRenderEvent.SkyColorEvent event) {
        if (!isEnabled()) return;

        Color sky = getSkyColor();
        event.setRed(sky.getRed()   / 255f);
        event.setGreen(sky.getGreen() / 255f);
        event.setBlue(sky.getBlue()  / 255f);
    }

    // ─── Preset colors ────────────────────────────────────────────────────────

    private Color getSkyColor() {
        if (rainbowSky.getValue()) {
            return Color.getHSBColor(rainbowHue, 0.8f, 0.9f);
        }

        switch (skyPreset.getValue()) {
            case SUNSET:     return new Color(255, 100, 50);
            case MIDNIGHT:   return new Color(5,   5,   20);
            case AURORA:     return new Color(20,  180, 120);
            case BLOOD_MOON: return new Color(120, 10,  10);
            case VOID:       return new Color(0,   0,   0);
            case GALAXY:     return new Color(15,  5,   40);
            case HEAVEN:     return new Color(240, 240, 255);
            default:
                if (customSky.getValue()) {
                    return new Color(skyRed.getValue(),
                                     skyGreen.getValue(),
                                     skyBlue.getValue());
                }
                return new Color(100, 150, 255);
        }
    }

    private Color getHorizonColor() {
        switch (skyPreset.getValue()) {
            case SUNSET:     return new Color(200, 60,  20);
            case MIDNIGHT:   return new Color(10,  10,  30);
            case AURORA:     return new Color(10,  120, 80);
            case BLOOD_MOON: return new Color(80,  5,   5);
            case VOID:       return new Color(0,   0,   0);
            case GALAXY:     return new Color(30,  10,  60);
            case HEAVEN:     return new Color(255, 255, 255);
            default:
                if (customHorizon.getValue()) {
                    return new Color(horRed.getValue(),
                                     horGreen.getValue(),
                                     horBlue.getValue());
                }
                return new Color(80, 80, 80);
        }
    }

    private Color getFogColor() {
        if (fogRainbow.getValue()) {
            return Color.getHSBColor((rainbowHue + 0.3f) % 1f, 1f, 1f);
        }

        // If a preset is active, tint fog to match
        switch (skyPreset.getValue()) {
            case SUNSET:     return new Color(200, 80,  40);
            case MIDNIGHT:   return new Color(5,   5,   15);
            case AURORA:     return new Color(15,  150, 100);
            case BLOOD_MOON: return new Color(100, 5,   5);
            case VOID:       return new Color(0,   0,   0);
            case GALAXY:     return new Color(10,  5,   30);
            case HEAVEN:     return new Color(230, 230, 255);
            default:
                return new Color(fogRed.getValue(),
                                 fogGreen.getValue(),
                                 fogBlue.getValue(),
                                 fogAlpha.getValue());
        }
    }

    // ─── Brightness & Saturation via shader-style GL ──────────────────────────

    @SubscribeEvent
    public void onRenderWorld(net.minecraftforge.client.event.RenderWorldLastEvent event) {
        if (!isEnabled()) return;

        float br = brightness.getValue().floatValue();
        float sa = saturation.getValue().floatValue();

        if (br != 1.0f || sa != 1.0f || nightVision.getValue()) {
            GlStateManager.pushMatrix();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);

            float nv = nightVision.getValue() ? 1.5f : 1.0f;

            GL11.glColor4f(br * nv, br * nv, br * nv, 1.0f);

            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }

        // Void color override
        if (customVoid.getValue()) {
            GL11.glClearColor(
                voidRed.getValue()   / 255f,
                voidGreen.getValue() / 255f,
                voidBlue.getValue()  / 255f,
                1f
            );
        }

        // Sun/moon/star visibility
        if (hideSun.getValue() || hideMoon.getValue() || hideStars.getValue()) {
            mc.world.provider.setAllowedSpawnTypes(false, false);
        }
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        // Restore fog
        GL11.glDisable(GL11.GL_FOG);
        GL11.glClearColor(0, 0, 0, 1);
    }
}
