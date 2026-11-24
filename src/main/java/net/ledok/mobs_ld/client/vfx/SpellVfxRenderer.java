package net.ledok.mobs_ld.client.vfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.ledok.mobs_ld.mob.LdMob;
import net.ledok.mobs_ld.network.SpellVfxKind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Procedural 3D world-space renderer for {@link SpellVfxManager}'s active effects.
 *
 * <p>Uses two lightweight shader-compatible {@link RenderType}s: a double-sided translucent layer for volumetric
 * fire/energy shells and a double-sided additive layer ({@link RenderStateShard#LIGHTNING_TRANSPARENCY}) for blinding
 * white-gold cores, shockwave rings, runes, and jagged Frenzied Flame arcs. Depth writes are disabled on both so
 * layered shells glow seamlessly without z-fighting while still respecting terrain occlusion.
 */
public final class SpellVfxRenderer {
    private static final double MIN_RENDER_DIST = 88.0;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private static final RenderType SPELL_TRANSLUCENT = RenderType.create(
            "mobs_ld_spell_translucent",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private static final RenderType SPELL_ADDITIVE = RenderType.create(
            "mobs_ld_spell_additive",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false)
    );

    private SpellVfxRenderer() {
    }

    public static void render(WorldRenderContext context) {
        List<SpellVfxInstance> active = SpellVfxManager.active();
        if (active.isEmpty()) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }

        Vec3 cam = context.camera().getPosition();
        ClientLevel world = context.world();
        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);

        // Two-pass batching: translucent shells first, additive glowing cores/rings/lightning on top.
        VertexConsumer translucent = consumers.getBuffer(SPELL_TRANSLUCENT);
        for (SpellVfxInstance effect : active) {
            if (!inRange(effect, cam)) {
                continue;
            }
            renderEffect(effect, world, poseStack, translucent, cam, partialTick, false);
        }
        if (consumers instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(SPELL_TRANSLUCENT);
        }

        VertexConsumer additive = consumers.getBuffer(SPELL_ADDITIVE);
        for (SpellVfxInstance effect : active) {
            if (!inRange(effect, cam)) {
                continue;
            }
            renderEffect(effect, world, poseStack, additive, cam, partialTick, true);
        }
        if (consumers instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(SPELL_ADDITIVE);
        }
    }

    private static boolean inRange(SpellVfxInstance e, Vec3 cam) {
        double dx = e.x - cam.x;
        double dy = e.y - cam.y;
        double dz = e.z - cam.z;
        double reach = Math.max(e.radius, e.length);
        double max = Math.max(MIN_RENDER_DIST, reach + 32.0);
        return (dx * dx + dy * dy + dz * dz) <= max * max;
    }

    /** Resolves a live animated marker bone position for body-anchored spells (e.g. fx_eye, fx_core). */
    private static Vec3 resolveLiveMarker(SpellVfxInstance e, ClientLevel world, String primary, String fallback) {
        if (world == null || e.sourceEntityId < 0) {
            return null;
        }
        Entity entity = world.getEntity(e.sourceEntityId);
        if (entity instanceof LdMob mob && mob.isAlive()) {
            Vec3 pos = mob.markerPosition(primary);
            if (pos != null) {
                return pos;
            }
            if (fallback != null) {
                return mob.markerPosition(fallback);
            }
        }
        return null;
    }

    private static void renderEffect(SpellVfxInstance e, ClientLevel world, PoseStack poseStack, VertexConsumer out,
                                     Vec3 cam, float partialTick, boolean additive) {
        float age = e.ageSeconds(partialTick);
        if (age > e.duration && !e.isCancelled()) {
            return;
        }
        float cancelMult = e.cancelAlpha(partialTick);
        if (cancelMult <= 0.005F) {
            return;
        }

        boolean inWindup = e.windup > 0.0F && age < e.windup;
        float wp = e.windup > 0.0F ? clamp01(age / e.windup) : 1.0F;
        float activeDuration = Math.max(0.05F, e.duration - e.windup);
        float ap = inWindup ? 0.0F : clamp01((age - e.windup) / activeDuration);

        double ox = e.x;
        double oy = e.y;
        double oz = e.z;
        if (e.kind == SpellVfxKind.EYE_CHARGE || e.kind == SpellVfxKind.SWEEPING_BEAM) {
            Vec3 liveEye = resolveLiveMarker(e, world, "fx_eye", "fx_head");
            if (liveEye == null && world != null && e.sourceEntityId >= 0) {
                Entity src = world.getEntity(e.sourceEntityId);
                if (src != null && src.isAlive()) {
                    liveEye = src.getEyePosition(partialTick);
                }
            }
            if (liveEye != null && liveEye.distanceToSqr(e.x, e.y, e.z) <= 64.0) {
                ox = liveEye.x;
                oy = liveEye.y;
                oz = liveEye.z;
            }
        } else if (e.kind == SpellVfxKind.CINDER_ERUPTION && e.count <= 1) {
            Vec3 liveCore = resolveLiveMarker(e, world, "fx_core", null);
            if (liveCore != null && liveCore.distanceToSqr(e.x, e.y, e.z) <= 16.0) {
                ox = liveCore.x;
                oy = liveCore.y;
                oz = liveCore.z;
            }
        }

        poseStack.pushPose();
        poseStack.translate(ox - cam.x, oy - cam.y, oz - cam.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-e.yaw));
        Matrix4f m = poseStack.last().pose();

        switch (e.kind) {
            case SLASH_ARC -> renderSlashArc(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case LANCE_LUNGE -> renderLanceLunge(e, m, out, age, inWindup, wp, ap, cancelMult, additive);
            case SWEEPING_BEAM -> renderSweepingBeam(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case EYE_CHARGE -> renderEyeCharge(e, poseStack, out, age, ap, cancelMult, additive);
            case METEOR_STRIKE -> renderMeteorStrike(e, m, out, age, inWindup, wp, ap, cancelMult, additive);
            case MAGMA_FISSURES -> renderMagmaFissures(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case NOVA_DOME -> renderNovaDome(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case SHOCKWAVE_RING -> renderShockwaveRing(e, m, out, age, inWindup, wp, ap, cancelMult, additive);
            case FLAME_WAVE -> renderFlameWave(e, m, out, age, inWindup, wp, ap, cancelMult, additive);
            case FLAME_PILLAR -> renderFlamePillar(e, m, out, age, inWindup, wp, ap, cancelMult, additive);
            case VORTEX_SLASH -> renderVortexSlash(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case AMBER_SPIRE -> renderAmberSpire(e, poseStack, out, inWindup, wp, ap, cancelMult, additive);
            case AMBER_GEODE_TRAP -> renderAmberGeodeTrap(e, poseStack, out, inWindup, wp, ap, cancelMult, additive);
            case GROUND_FRACTURE -> renderGroundFracture(e, poseStack, out, inWindup, wp, ap, cancelMult, additive);
            case SHARD_SPRAY -> renderShardSpray(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case RESONANCE_RING -> renderResonanceRing(e, poseStack, out, inWindup, wp, ap, cancelMult, additive);
            case CLAW_RAKE -> renderClawRake(e, poseStack, out, ap, cancelMult, additive);
            case ROOT_TENDRILS -> renderRootTendrils(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case CINDER_ERUPTION -> renderCinderEruption(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case ROLL_TRAIL -> renderRollTrail(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case BURROW_MOUND -> renderBurrowMound(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case DRUM_TREMOR -> renderDrumTremor(e, poseStack, out, inWindup, wp, ap, cancelMult, additive);
            case RIB_FURNACE -> renderRibFurnace(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case MADNESS_GRASP -> renderMadnessGrasp(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case SPEAR_ARC -> renderSpearArc(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case LIGHTNING_BOLT -> renderLightningBolt(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case STORM_GALE -> renderStormGale(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case TEMPEST_CYCLONE -> renderTempestCyclone(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case THUNDER_SPEAR_DIVE -> renderThunderSpearDive(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
            case STORM_NOVA -> renderStormNova(e, poseStack, out, age, inWindup, wp, ap, cancelMult, additive);
        }

        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // 1. SLASH_ARC — Curved 3D fiery greatsword crescent slash + floor cleave readability
    // -------------------------------------------------------------------------
    private static void renderSlashArc(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                       boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float halfDeg = e.angle * 0.5F;
        float startDeg = e.reverse ? halfDeg : -halfDeg;
        float endDeg = e.reverse ? -halfDeg : halfDeg;
        boolean steep = Math.abs(e.tilt) > 35.0F;
        float bladeY = e.height;
        Matrix4f groundM = poseStack.last().pose();

        // Ground-plane projection so players in first-person can read vertical overhead cleaves and cone edges clearly
        // without staring up at the sky or using ugly flat debug rectangles.
        if (inWindup) {
            float pulse = 0.75F + 0.25F * Mth.sin(age * 16.0F);
            float alpha = (0.20F + 0.55F * easeInQuad(wp)) * cm * pulse;
            if (steep) {
                float halfW = Math.max(0.75F, e.width * 0.5F);
                float len = Math.max(e.length, e.radius);
                if (!additive) {
                    int edgeCol = argb(e.primaryColor, 0.04F * alpha);
                    int midCol = argb(e.primaryColor, 0.24F * alpha);
                    quad(groundM, out, -halfW, 0.07F, 0.0F, 0.0F, 0.07F, 0.0F, 0.0F, 0.07F, len, -halfW, 0.07F, len,
                            edgeCol, midCol, midCol, edgeCol);
                    quad(groundM, out, 0.0F, 0.07F, 0.0F, halfW, 0.07F, 0.0F, halfW, 0.07F, len, 0.0F, 0.07F, len,
                            midCol, edgeCol, edgeCol, midCol);
                } else {
                    float railH = 0.14F + 0.16F * wp;
                    int rBot = argb(e.primaryColor, 0.65F * alpha);
                    int rTop = argb(e.secondaryColor, 0.0F);
                    quad(groundM, out, -halfW, 0.06F, 0.0F, -halfW, 0.06F, len, -halfW, railH, len, -halfW, railH, 0.0F,
                            rBot, rBot, rTop, rTop);
                    quad(groundM, out, halfW, 0.06F, 0.0F, halfW, 0.06F, len, halfW, railH, len, halfW, railH, 0.0F,
                            rBot, rBot, rTop, rTop);
                    // Molten center cleave seam racing forward to impact.
                    float seamLen = len * easeOutQuad(wp);
                    drawGroundStrip(groundM, out, 0.0F, 0.0F, 0.0F, seamLen, 0.12F, 0.08F,
                            argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.45F * alpha));
                }
            } else if (additive) {
                // Subtle glowing curved ground rim marking exact horizontal slash reach.
                drawArcRibbon(groundM, out, startDeg, endDeg, e.radius * 0.94F, e.radius, 0.07F, 0.07F, 22,
                        argb(e.primaryColor, 0.25F * alpha), argb(e.secondaryColor, 0.58F * alpha));
            }
        } else if (steep) {
            // Brief scorched molten cleave line along the floor on vertical overhead impact.
            float scorchFade = (1.0F - easeInQuad(ap)) * cm;
            if (scorchFade > 0.02F) {
                float len = Math.max(e.length, e.radius);
                if (additive) {
                    drawGroundStrip(groundM, out, 0.0F, 0.3F, 0.0F, len, 0.22F * (1.0F - 0.4F * ap), 0.08F,
                            argb(0xFFFFFF, 0.85F * scorchFade), argb(e.primaryColor, 0.25F * scorchFade));
                }
            }
        }

        poseStack.pushPose();
        poseStack.translate(0.0F, bladeY, 0.0F);
        if (Math.abs(e.tilt) > 0.01F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(e.tilt));
        }
        Matrix4f m = poseStack.last().pose();

        if (inWindup) {
            // Sleek glowing golden warning arc tracing the upcoming blade path + sweeping spark.
            float pulse = 0.72F + 0.28F * Mth.sin(age * 16.0F);
            float alpha = (0.16F + 0.52F * easeInQuad(wp)) * cm * pulse;
            if (!additive) {
                drawArcRibbon(m, out, startDeg, endDeg, e.radius * 0.52F, e.radius, 0.0F, 0.0F, 22,
                        argb(e.primaryColor, alpha * 0.28F), argb(e.primaryColor, alpha * 0.48F));
            } else {
                // Crisp luminous leading rim + animated glint sliding along the sweep.
                drawArcRibbon(m, out, startDeg, endDeg, e.radius * 0.93F, e.radius * 1.01F, 0.0F, 0.03F, 24,
                        argb(e.primaryColor, alpha * 0.72F), argb(e.secondaryColor, alpha * 0.92F));
                float glintMid = Mth.lerp(easeOutQuad(wp), startDeg, endDeg);
                float glintSpan = Math.min(24.0F, e.angle * 0.25F);
                drawArcRibbon(m, out, glintMid - glintSpan, glintMid + glintSpan,
                        e.radius * 0.84F, e.radius * 1.04F, 0.02F, 0.05F, 8,
                        argb(e.secondaryColor, 0.0F), argb(e.secondaryColor, alpha));
            }
            poseStack.popPose();
            return;
        }

        // Explosive crescent slash sweeping fast across the full arc, then dissolving cleanly.
        float headP = easeOutQuart(clamp01(ap * 3.1F));
        float tailP = easeInQuad(clamp01((ap - 0.14F) / 0.86F));
        float curStart = Mth.lerp(tailP, startDeg, endDeg);
        float curEnd = Mth.lerp(headP, startDeg, endDeg);
        float fade = (1.0F - easeInQuad(ap)) * cm;

        if (Math.abs(curEnd - curStart) > 0.5F && fade > 0.01F) {
            if (!additive) {
                drawTaperedSlash(m, out, curStart, curEnd, e.radius * 0.38F, e.radius * 1.06F, 0.0F, 0.24F, 24,
                        e.primaryColor, e.primaryColor, 0.05F * fade, 0.50F * fade, age);
            } else {
                drawTaperedSlash(m, out, curStart, curEnd, e.radius * 0.60F, e.radius * 1.03F, 0.0F, 0.16F, 24,
                        e.primaryColor, e.secondaryColor, 0.12F * fade, 0.88F * fade, age);
                drawTaperedSlash(m, out, curStart, curEnd, e.radius * 0.87F, e.radius * 1.01F, 0.05F, 0.02F, 24,
                        e.secondaryColor, 0xFFFFFF, 0.35F * fade, fade, age);
                drawTaperedSlash(m, out, curStart, curEnd, e.radius * 0.87F, e.radius * 1.01F, -0.05F, -0.02F, 24,
                        e.secondaryColor, 0xFFFFFF, 0.35F * fade, fade, age);
            }
        }
        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // 2. LANCE_LUNGE — Replaces flat dash arrows with 3D golden energy corridor + spear burst
    // -------------------------------------------------------------------------
    private static void renderLanceLunge(SpellVfxInstance e, Matrix4f m, VertexConsumer out, float age,
                                         boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float halfW = Math.max(0.45F, e.width * 0.5F);
        float len = e.length;
        float coreY = Math.min(2.2F, Math.max(1.2F, e.height * 0.65F));

        if (inWindup) {
            float intensity = (0.35F + 0.65F * easeInQuad(wp)) * cm;
            if (!additive) {
                // Soft molten ground corridor glow (zero arrow, clean translucent heat trail).
                int edgeCol = argb(e.primaryColor, 0.04F * intensity);
                int midCol = argb(e.primaryColor, 0.28F * intensity);
                quad(m, out, -halfW, 0.05F, 0.0F, 0.0F, 0.05F, 0.0F, 0.0F, 0.05F, len, -halfW, 0.05F, len,
                        edgeCol, midCol, midCol, edgeCol);
                quad(m, out, 0.0F, 0.05F, 0.0F, halfW, 0.05F, 0.0F, halfW, 0.05F, len, 0.0F, 0.05F, len,
                        midCol, edgeCol, edgeCol, midCol);
            } else {
                // Twin glowing molten flame rails framing the exact locked lunge lane.
                float railH = 0.22F + 0.28F * wp;
                int railBot = argb(e.primaryColor, 0.78F * intensity);
                int railTop = argb(e.secondaryColor, 0.0F);
                quad(m, out, -halfW, 0.04F, 0.0F, -halfW, 0.04F, len, -halfW, railH, len, -halfW, railH, 0.0F,
                        railBot, railBot, railTop, railTop);
                quad(m, out, halfW, 0.04F, 0.0F, halfW, 0.04F, len, halfW, railH, len, halfW, railH, 0.0F,
                        railBot, railBot, railTop, railTop);

                // Animated golden wind-streaks surging down the corridor to communicate forward momentum (no arrow!).
                int streaks = 5;
                for (int i = 0; i < streaks; i++) {
                    float phase = (age * 2.6F + i / (float) streaks) % 1.0F;
                    float z0 = phase * len * 0.85F;
                    float z1 = Math.min(len, z0 + Math.min(2.6F, len * 0.25F));
                    float xOff = Mth.sin(i * 2.3F + age * 4.0F) * halfW * 0.65F;
                    float yOff = 0.25F + (i % 3) * 0.45F;
                    float sa = Mth.sin(phase * (float) Math.PI) * 0.85F * intensity;
                    drawRibbonSegment(m, out, xOff, yOff, z0, xOff, yOff, z1, 0.08F,
                            argb(e.primaryColor, 0.0F), argb(e.secondaryColor, sa));
                }

                // Three converging golden charge rings around Midra's body tightening toward the lance tip.
                for (int i = 0; i < 3; i++) {
                    float ringP = clamp01((wp * 1.4F) - i * 0.18F);
                    float r = Mth.lerp(easeOutQuad(ringP), halfW * 1.65F, 0.42F + i * 0.14F);
                    float zPos = 0.6F + i * 0.75F;
                    drawVerticalRing(m, out, 0.0F, coreY, zPos, r, r + 0.12F, 18,
                            argb(e.secondaryColor, 0.85F * intensity), argb(e.primaryColor, 0.15F * intensity));
                }

                // Growing white-gold lance ignition star at the spear tip + clean terminal stop-ring at z = length.
                float starR = 0.4F + 0.85F * easeInQuad(wp);
                drawStarburst(m, out, 0.0F, coreY, 2.0F, starR, age * 5.0F,
                        argb(0xFFFFFF, 0.9F * intensity), argb(e.primaryColor, 0.0F));
                drawGroundStrip(m, out, -halfW, len, halfW, len, 0.12F, 0.08F,
                        argb(e.secondaryColor, 0.85F * intensity), argb(e.secondaryColor, 0.85F * intensity));
            }
            return;
        }

        // Active lunge: sleek blazing 3D tapered spear-cone of Frenzied Flame + spiral ribbons + 4 shock rings.
        float thrust = easeOutCubic(clamp01(ap * 2.4F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float tipZ = Math.max(1.0F, len * thrust);
        float baseR = halfW * (0.76F - 0.32F * ap);

        if (!additive) {
            // Outer translucent fiery plasma lance sheath.
            drawTaperedConeZ(m, out, 0.0F, coreY, 0.0F, tipZ, baseR * 1.15F, 0.08F, 10,
                    argb(e.primaryColor, 0.42F * fade), argb(e.secondaryColor, 0.62F * fade));
            // Scorched ember corridor lingering on the floor.
            int floorMid = argb(e.primaryColor, 0.35F * fade);
            int floorEdge = argb(e.primaryColor, 0.0F);
            quad(m, out, -halfW, 0.05F, 0.0F, 0.0F, 0.05F, 0.0F, 0.0F, 0.05F, tipZ, -halfW, 0.05F, tipZ,
                    floorEdge, floorMid, floorMid, floorEdge);
            quad(m, out, 0.0F, 0.05F, 0.0F, halfW, 0.05F, 0.0F, halfW, 0.05F, tipZ, 0.0F, 0.05F, tipZ,
                    floorMid, floorEdge, floorEdge, floorMid);
        } else {
            // Blinding white-gold inner spear core + golden mid plasma lance.
            drawTaperedConeZ(m, out, 0.0F, coreY, 0.2F, tipZ, baseR * 0.72F, 0.04F, 10,
                    argb(e.primaryColor, 0.85F * fade), argb(e.secondaryColor, 0.95F * fade));
            drawTaperedConeZ(m, out, 0.0F, coreY, 0.4F, tipZ, baseR * 0.32F, 0.02F, 8,
                    argb(e.secondaryColor, 0.95F * fade), argb(0xFFFFFF, fade));

            // Dual spiraling helical flame ribbons twisting along the lance length.
            for (int helix = 0; helix < 2; helix++) {
                float phaseOffset = helix * (float) Math.PI + age * 14.0F;
                int segs = 20;
                for (int s = 0; s < segs; s++) {
                    float f0 = s / (float) segs;
                    float f1 = (s + 1) / (float) segs;
                    float z0 = f0 * tipZ;
                    float z1 = f1 * tipZ;
                    float r0 = (1.0F - f0 * 0.85F) * baseR * 1.15F;
                    float r1 = (1.0F - f1 * 0.85F) * baseR * 1.15F;
                    float a0 = f0 * TAU * 2.5F + phaseOffset;
                    float a1 = f1 * TAU * 2.5F + phaseOffset;
                    drawRibbonSegment(m, out,
                            Mth.cos(a0) * r0, coreY + Mth.sin(a0) * r0, z0,
                            Mth.cos(a1) * r1, coreY + Mth.sin(a1) * r1, z1,
                            0.14F * (1.0F - f0 * 0.5F),
                            argb(e.secondaryColor, 0.9F * fade * (1.0F - f0 * 0.3F)),
                            argb(e.primaryColor, 0.55F * fade * (1.0F - f1 * 0.3F)));
                }
            }

            // Four expanding vertical shockwave rings popping open along the thrust path.
            for (int i = 0; i < 4; i++) {
                float frac = 0.16F + i * 0.24F;
                float ringZ = len * frac;
                if (tipZ < ringZ) {
                    continue;
                }
                float localP = clamp01((ap - frac * 0.35F) / 0.65F);
                float ringFade = (1.0F - easeInQuad(localP)) * cm;
                float rIn = halfW * (0.45F + 1.1F * easeOutQuad(localP));
                drawVerticalRing(m, out, 0.0F, coreY, ringZ, rIn, rIn + 0.18F, 20,
                        argb(e.secondaryColor, 0.9F * ringFade), argb(e.primaryColor, 0.0F));
            }
        }
    }

    // -------------------------------------------------------------------------
    // 3. SWEEPING_BEAM — Continuous 3D volumetric Frenzied Flame laser + lightning coils
    //    Focused single eye-laser (angle <= 3 deg, e.g. Frenzied Eye-Wisp) fires straight out of fx_eye along a gentle
    //    aim slope; wide sweeping beam (Midra) plunges from his high eye bone to player torso height and sweeps level.
    // -------------------------------------------------------------------------
    private static void renderSweepingBeam(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                           boolean inWindup, float wp, float ap, float cm, boolean additive) {
        if (e.angle <= 3.0F) {
            renderFocusedEyeBeam(e, poseStack, out, age, inWindup, wp, ap, cm, additive);
            return;
        }
        float halfDeg = e.angle * 0.5F;
        float startDeg = e.reverse ? halfDeg : -halfDeg;
        float endDeg = e.reverse ? -halfDeg : halfDeg;
        float beamLen = e.length;
        float dropY = e.height; // vertical drop from high eye anchor down to player torso height (~1.1 blocks above ground)
        float kneeDist = Math.min(beamLen * 0.28F, 6.0F);
        float descentLen = Mth.sqrt(kneeDist * kneeDist + dropY * dropY);
        float descentPitchDeg = (float) (Math.atan2(dropY, kneeDist) * (180.0 / Math.PI));
        float sweepLen = Math.max(0.5F, beamLen - kneeDist);
        float groundY = -(dropY + 1.02F);

        if (inWindup) {
            float intensity = (0.3F + 0.7F * easeInQuad(wp)) * cm;
            // Ground sweep-fan glow softly outlining the upcoming beam sweep below the eye.
            Matrix4f baseM = poseStack.last().pose();
            if (!additive) {
                drawArcRibbon(baseM, out, -halfDeg, halfDeg, 1.5F, beamLen, groundY, groundY, 20,
                        argb(e.primaryColor, 0.04F * intensity), argb(e.primaryColor, 0.16F * intensity));
            } else {
                // Directional preview glint gliding from startDeg toward endDeg so players see which way the beam sweeps.
                float previewDeg = Mth.lerp(easeOutQuad(wp), startDeg, endDeg);
                float previewRad = previewDeg * DEG_TO_RAD;
                drawGroundStrip(baseM, out, 0.0F, 0.0F, -Mth.sin(previewRad) * beamLen, Mth.cos(previewRad) * beamLen,
                        0.15F, groundY + 0.04F, argb(e.secondaryColor, 0.68F * intensity), argb(e.primaryColor, 0.18F * intensity));
                // Glowing focusing rings at Midra's blazing eye.
                float ringR = Mth.lerp(easeOutQuad(wp), 2.0F, 0.55F);
                drawStarburst(baseM, out, 0.0F, 0.0F, 0.0F, 0.6F + 1.2F * wp, age * 6.0F,
                        argb(0xFFFFFF, 0.9F * intensity), argb(e.primaryColor, 0.0F));
                float thinR = 0.05F + 0.12F * easeInQuad(wp);
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(-startDeg));
                // Steep downward targeting sight-line from eye down to player torso level.
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(descentPitchDeg));
                Matrix4f descM = poseStack.last().pose();
                drawVerticalRing(descM, out, 0.0F, 0.0F, 0.35F, ringR, ringR + 0.12F, 18,
                        argb(e.secondaryColor, 0.9F * intensity), argb(e.primaryColor, 0.0F));
                drawBeamPrism(descM, out, descentLen, thinR, thinR * 1.15F, 6,
                        argb(e.secondaryColor, 0.85F * intensity), argb(e.primaryColor, 0.55F * intensity));
                poseStack.popPose();
                // Horizontal player-height targeting beam continuing across the arena along startDeg.
                poseStack.pushPose();
                poseStack.translate(0.0F, -dropY, kneeDist);
                Matrix4f levelM = poseStack.last().pose();
                drawStarburst(levelM, out, 0.0F, 0.0F, 0.0F, 0.45F + 0.55F * wp, -age * 6.0F,
                        argb(e.secondaryColor, 0.85F * intensity), argb(e.primaryColor, 0.0F));
                drawBeamPrism(levelM, out, sweepLen, thinR * 1.15F, thinR * 1.4F, 6,
                        argb(e.secondaryColor, 0.80F * intensity), argb(e.primaryColor, 0.32F * intensity));
                poseStack.popPose();
                poseStack.popPose();
            }
            return;
        }

        // Smooth continuous sweep across [-angle/2, +angle/2] at player torso height.
        float sweepP = clamp01(ap / 0.92F);
        float curDeg = Mth.lerp(sweepP, startDeg, endDeg);
        float rampIn = clamp01(ap * 10.0F);
        float fadeOut = ap > 0.86F ? clamp01((1.0F - ap) / 0.14F) : 1.0F;
        float pulse = 0.88F + 0.12F * Mth.sin(age * 42.0F);
        float alpha = rampIn * fadeOut * cm;
        float baseR = Math.max(0.25F, e.width * 0.45F) * (0.4F + 0.6F * rampIn) * pulse;

        // Lingering scorched ground ember arc behind the sweeping beam.
        if (!additive && Math.abs(curDeg - startDeg) > 1.0F) {
            drawArcRibbon(poseStack.last().pose(), out, startDeg, curDeg, 2.0F, beamLen, groundY, groundY, 16,
                    argb(e.primaryColor, 0.04F * alpha), argb(e.primaryColor, 0.24F * alpha));
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-curDeg));

        // Segment 1: steep eye-to-torso descent beam from Midra's blazing eye down to (0, -dropY, kneeDist).
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(descentPitchDeg));
        Matrix4f descM = poseStack.last().pose();
        if (!additive) {
            drawBeamPrism(descM, out, descentLen, baseR * 1.25F, baseR * 1.50F, 10,
                    argb(e.primaryColor, 0.42F * alpha), argb(e.primaryColor, 0.36F * alpha));
        } else {
            drawBeamPrism(descM, out, descentLen, baseR * 0.72F, baseR * 0.88F, 10,
                    argb(e.primaryColor, 0.88F * alpha), argb(e.secondaryColor, 0.82F * alpha));
            drawBeamPrism(descM, out, descentLen, baseR * 0.34F, baseR * 0.40F, 8,
                    argb(0xFFFFFF, alpha), argb(e.secondaryColor, 0.95F * alpha));
            // Blazing eye muzzle flare + expanding shock ring.
            drawStarburst(descM, out, 0.0F, 0.0F, 0.2F, baseR * 2.6F, age * 9.0F,
                    argb(0xFFFFFF, alpha), argb(e.primaryColor, 0.0F));
            float ringWave = (age * 3.5F) % 1.0F;
            float muzzleR = baseR * (1.0F + 2.0F * ringWave);
            drawVerticalRing(descM, out, 0.0F, 0.0F, 0.5F + ringWave * 3.0F, muzzleR, muzzleR + 0.15F, 16,
                    argb(e.secondaryColor, (1.0F - ringWave) * 0.85F * alpha), argb(e.primaryColor, 0.0F));
        }
        poseStack.popPose();

        // Segment 2: horizontal player-height beam sweeping straight through chest/head height from kneeDist to beamLen.
        poseStack.pushPose();
        poseStack.translate(0.0F, -dropY, kneeDist);
        Matrix4f levelM = poseStack.last().pose();
        if (!additive) {
            drawBeamPrism(levelM, out, sweepLen, baseR * 1.48F, baseR * 1.95F, 10,
                    argb(e.primaryColor, 0.44F * alpha), argb(e.primaryColor, 0.22F * alpha));
        } else {
            // Golden mid-plasma cylinder + blinding white-gold inner laser core slicing at player height.
            drawBeamPrism(levelM, out, sweepLen, baseR * 0.86F, baseR * 1.15F, 10,
                    argb(e.primaryColor, 0.90F * alpha), argb(e.secondaryColor, 0.68F * alpha));
            drawBeamPrism(levelM, out, sweepLen, baseR * 0.40F, baseR * 0.48F, 8,
                    argb(0xFFFFFF, alpha), argb(e.secondaryColor, 0.95F * alpha));

            // Intense focal flare + ground scorch ring where the eye laser plunges down onto player level.
            drawStarburst(levelM, out, 0.0F, 0.0F, 0.0F, baseR * 2.5F, -age * 11.0F,
                    argb(0xFFFFFF, 0.95F * alpha), argb(e.primaryColor, 0.0F));
            drawHorizontalRing(levelM, out, -1.00F, baseR * 0.55F, baseR * 1.85F, 16,
                    argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.0F));

            // Three jagged crackling Frenzied Flame lightning tendrils coiling around the player-level beam.
            int stepSeed = (int) (age * 18.0F);
            for (int arc = 0; arc < 3; arc++) {
                int segs = 12;
                float prevX = 0.0F;
                float prevY = 0.0F;
                float prevZ = 0.0F;
                for (int s = 1; s <= segs; s++) {
                    float f = s / (float) segs;
                    float z = f * sweepLen;
                    float ang = f * TAU * 3.0F + arc * (TAU / 3.0F) + age * 12.0F;
                    float jitter = hashSigned(stepSeed * 31 + arc * 17 + s * 7) * 0.52F;
                    float r = baseR * (1.1F + 0.42F * f) + jitter;
                    float nx = Mth.cos(ang) * r;
                    float ny = Mth.sin(ang) * r;
                    drawRibbonSegment(levelM, out, prevX, prevY, prevZ, nx, ny, z, 0.09F,
                            argb(e.secondaryColor, 0.92F * alpha), argb(e.primaryColor, 0.72F * alpha));
                    prevX = nx;
                    prevY = ny;
                    prevZ = z;
                }
            }

            // Burning terminal starburst at the far end of the beam.
            drawStarburst(levelM, out, 0.0F, 0.0F, sweepLen, baseR * 3.0F, -age * 7.0F,
                    argb(e.secondaryColor, 0.9F * alpha), argb(e.primaryColor, 0.0F));
        }
        poseStack.popPose();

        poseStack.popPose();
    }

    /** Straight focused eye-laser (e.g. Frenzied Eye-Wisp) firing directly out of fx_eye onto the locked lane. */
    private static void renderFocusedEyeBeam(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                             boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float beamLen = e.length;
        float dropY = e.height;
        float halfW = Math.max(0.45F, e.width * 0.5F);
        float slopeLen = Mth.sqrt(beamLen * beamLen + dropY * dropY);
        float pitchDeg = (float) (Math.atan2(dropY, beamLen) * (180.0 / Math.PI));
        float groundY = -(dropY + 1.15F);

        if (inWindup) {
            float intensity = (0.30F + 0.70F * easeInQuad(wp)) * cm;
            Matrix4f baseM = poseStack.last().pose();
            // Clear ground-level corridor rails so players can sidestep the incoming eye-laser cleanly.
            if (!additive) {
                int edgeCol = argb(e.primaryColor, 0.04F * intensity);
                int midCol = argb(e.primaryColor, 0.22F * intensity);
                quad(baseM, out, -halfW, groundY, 0.4F, 0.0F, groundY, 0.4F, 0.0F, groundY, beamLen, -halfW, groundY, beamLen,
                        edgeCol, midCol, midCol, edgeCol);
                quad(baseM, out, 0.0F, groundY, 0.4F, halfW, groundY, 0.4F, halfW, groundY, beamLen, 0.0F, groundY, beamLen,
                        midCol, edgeCol, edgeCol, midCol);
            } else {
                drawGroundStrip(baseM, out, -halfW, 0.4F, -halfW, beamLen, 0.09F, groundY + 0.03F,
                        argb(e.secondaryColor, 0.75F * intensity), argb(e.primaryColor, 0.30F * intensity));
                drawGroundStrip(baseM, out, halfW, 0.4F, halfW, beamLen, 0.09F, groundY + 0.03F,
                        argb(e.secondaryColor, 0.75F * intensity), argb(e.primaryColor, 0.30F * intensity));
                // Glowing charge starburst + focusing ring right on the wisp's face/eye (0, 0, 0).
                float ringR = Mth.lerp(easeOutQuad(wp), 1.15F, 0.28F);
                drawStarburst(baseM, out, 0.0F, 0.0F, 0.0F, 0.35F + 0.55F * wp, age * 7.0F,
                        argb(0xFFFFFF, 0.92F * intensity), argb(e.primaryColor, 0.0F));
                float thinR = 0.04F + 0.09F * easeInQuad(wp);
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
                Matrix4f aimM = poseStack.last().pose();
                drawVerticalRing(aimM, out, 0.0F, 0.0F, 0.2F, ringR, ringR + 0.09F, 16,
                        argb(e.secondaryColor, 0.9F * intensity), argb(e.primaryColor, 0.0F));
                drawBeamPrism(aimM, out, slopeLen, thinR, thinR * 1.25F, 6,
                        argb(e.secondaryColor, 0.85F * intensity), argb(e.primaryColor, 0.35F * intensity));
                poseStack.popPose();
            }
            return;
        }

        float rampIn = clamp01(ap * 12.0F);
        float fadeOut = ap > 0.72F ? clamp01((1.0F - ap) / 0.28F) : 1.0F;
        float pulse = 0.88F + 0.12F * Mth.sin(age * 42.0F);
        float alpha = rampIn * fadeOut * cm;
        float baseR = Math.max(0.18F, e.width * 0.26F) * (0.45F + 0.55F * rampIn) * pulse;

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        Matrix4f beamM = poseStack.last().pose();
        if (!additive) {
            drawBeamPrism(beamM, out, slopeLen, baseR * 1.25F, baseR * 1.60F, 10,
                    argb(e.primaryColor, 0.44F * alpha), argb(e.primaryColor, 0.22F * alpha));
        } else {
            drawBeamPrism(beamM, out, slopeLen, baseR * 0.72F, baseR * 0.95F, 10,
                    argb(e.primaryColor, 0.90F * alpha), argb(e.secondaryColor, 0.72F * alpha));
            drawBeamPrism(beamM, out, slopeLen, baseR * 0.34F, baseR * 0.42F, 8,
                    argb(0xFFFFFF, alpha), argb(e.secondaryColor, 0.95F * alpha));
            drawStarburst(beamM, out, 0.0F, 0.0F, 0.12F, baseR * 2.4F, age * 10.0F,
                    argb(0xFFFFFF, alpha), argb(e.primaryColor, 0.0F));
            int stepSeed = (int) (age * 20.0F);
            for (int arc = 0; arc < 2; arc++) {
                int segs = 10;
                float prevX = 0.0F;
                float prevY = 0.0F;
                float prevZ = 0.0F;
                for (int s = 1; s <= segs; s++) {
                    float f = s / (float) segs;
                    float z = f * slopeLen;
                    float ang = f * TAU * 2.5F + arc * (float) Math.PI + age * 14.0F;
                    float jitter = hashSigned(stepSeed * 29 + arc * 13 + s * 7) * 0.28F;
                    float r = baseR * (1.15F + 0.35F * f) + jitter;
                    float nx = Mth.cos(ang) * r;
                    float ny = Mth.sin(ang) * r;
                    drawRibbonSegment(beamM, out, prevX, prevY, prevZ, nx, ny, z, 0.065F,
                            argb(e.secondaryColor, 0.90F * alpha), argb(e.primaryColor, 0.65F * alpha));
                    prevX = nx;
                    prevY = ny;
                    prevZ = z;
                }
            }
            drawStarburst(beamM, out, 0.0F, 0.0F, slopeLen, baseR * 2.2F, -age * 8.0F,
                    argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.0F));
        }
        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // 4. EYE_CHARGE — Blazing Frenzied Flame sun-corona + counter-rotating arcane rings
    // -------------------------------------------------------------------------
    private static void renderEyeCharge(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                        float ap, float cm, boolean additive) {
        float env = clamp01(ap * 5.0F) * (1.0F - easeInQuad(clamp01((ap - 0.65F) / 0.35F))) * cm;
        if (env <= 0.01F) {
            return;
        }
        float pulse = 0.88F + 0.12F * Mth.sin(age * 24.0F);
        float r = e.radius * (0.55F + 0.45F * easeOutCubic(clamp01(ap * 2.2F))) * pulse;
        Matrix4f m = poseStack.last().pose();

        if (!additive) {
            drawSphere(m, out, 0.0F, 0.0F, 0.0F, r * 0.62F, 12, 6, argb(e.primaryColor, 0.42F * env));
        } else {
            drawSphere(m, out, 0.0F, 0.0F, 0.0F, r * 0.36F, 10, 5, argb(0xFFFFFF, 0.92F * env));
            drawStarburst(m, out, 0.0F, 0.0F, 0.0F, r * 1.45F, age * 4.5F,
                    argb(e.secondaryColor, 0.9F * env), argb(e.primaryColor, 0.0F));

            // Three tilted counter-rotating golden halo rings.
            for (int i = 0; i < 3; i++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(55.0F * i + age * (90.0F + i * 40.0F)));
                poseStack.mulPose(Axis.ZP.rotationDegrees(38.0F * (i + 1) - age * (70.0F + i * 35.0F)));
                float ringR = r * (0.72F + i * 0.26F);
                drawHorizontalRing(poseStack.last().pose(), out, 0.0F, ringR, ringR + 0.1F, 20,
                        argb(i == 0 ? e.secondaryColor : e.primaryColor, 0.88F * env),
                        argb(e.primaryColor, 0.15F * env));
                poseStack.popPose();
            }

            // Converging radial filaments pulling golden energy inward.
            int rays = Math.max(4, Math.min(12, e.count * 2));
            for (int i = 0; i < rays; i++) {
                float p = (age * 2.2F + i / (float) rays) % 1.0F;
                float theta = i * (TAU / rays) + age * 0.8F;
                float phi = Mth.sin(i * 1.7F) * 0.85F;
                float outerD = r * Mth.lerp(p, 2.3F, 0.5F);
                float innerD = Math.max(0.15F, outerD - r * 0.65F);
                float cx = Mth.cos(theta) * Mth.cos(phi);
                float cy = Mth.sin(phi);
                float cz = Mth.sin(theta) * Mth.cos(phi);
                float fa = Mth.sin(p * (float) Math.PI) * 0.85F * env;
                drawRibbonSegment(m, out, cx * outerD, cy * outerD, cz * outerD,
                        cx * innerD, cy * innerD, cz * innerD, 0.07F,
                        argb(e.primaryColor, 0.0F), argb(e.secondaryColor, fa));
            }
        }
    }

    // -------------------------------------------------------------------------
    // 5. METEOR_STRIKE — Arcane ground sigil + plummeting 3D comet + detonation burst
    // -------------------------------------------------------------------------
    private static void renderMeteorStrike(SpellVfxInstance e, Matrix4f m, VertexConsumer out, float age,
                                           boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float r = e.radius;
        if (inWindup) {
            float alpha = (0.35F + 0.65F * easeInQuad(wp)) * cm;
            // Accelerating diagonal descent straight into (0, 0.3, 0).
            float fall = 1.0F - wp * wp;
            float my = 0.35F + e.height * fall;
            float mx = 1.8F * (1.0F - wp);
            float mz = -1.8F * (1.0F - wp);
            float meteorR = 0.55F + 0.25F * wp;

            if (!additive) {
                // Soft glowing ground danger disc inside the rune perimeter.
                drawHorizontalRing(m, out, 0.05F, 0.0F, r, 24,
                        argb(e.primaryColor, 0.14F * alpha), argb(e.primaryColor, 0.26F * alpha));
                // Translucent fiery outer meteor orb.
                drawSphere(m, out, mx, my, mz, meteorR * 1.35F, 10, 5, argb(e.primaryColor, 0.58F * cm));
            } else {
                // Rotating double arcane sigil ring + shrinking impact timing ring + rune spokes.
                drawHorizontalRing(m, out, 0.06F, r * 0.92F, r, 28,
                        argb(e.primaryColor, 0.82F * alpha), argb(e.secondaryColor, alpha));
                drawHorizontalRing(m, out, 0.06F, r * 0.54F, r * 0.60F, 24,
                        argb(e.secondaryColor, 0.72F * alpha), argb(e.primaryColor, 0.2F * alpha));
                float shrinkR = Math.max(0.15F, r * (1.0F - wp));
                drawHorizontalRing(m, out, 0.07F, shrinkR, shrinkR + 0.14F, 24,
                        argb(0xFFFFFF, 0.95F * alpha), argb(e.primaryColor, 0.0F));
                for (int i = 0; i < 4; i++) {
                    float a = age * 2.2F + i * (TAU * 0.25F);
                    float cs = Mth.cos(a);
                    float sn = Mth.sin(a);
                    drawGroundStrip(m, out, cs * r * 0.2F, sn * r * 0.2F, cs * r * 0.94F, sn * r * 0.94F,
                            0.08F, 0.065F, argb(e.secondaryColor, 0.75F * alpha), argb(e.primaryColor, 0.45F * alpha));
                }

                // Blazing white-gold meteor core + elongated fiery comet tail trailing into the sky.
                drawSphere(m, out, mx, my, mz, meteorR * 0.72F, 10, 5, argb(0xFFFFFF, 0.95F * cm));
                float tailTopX = mx + 0.85F;
                float tailTopY = my + Math.min(5.2F, e.height * 0.42F);
                float tailTopZ = mz - 0.85F;
                drawRibbonSegment(m, out, mx, my, mz, tailTopX, tailTopY, tailTopZ, meteorR * 0.95F,
                        argb(e.secondaryColor, 0.95F * cm), argb(e.primaryColor, 0.0F));
                drawRibbonSegment(m, out, mx, my, mz, tailTopX, tailTopY, tailTopZ, meteorR * 0.45F,
                        argb(0xFFFFFF, cm), argb(e.secondaryColor, 0.0F));
            }
            return;
        }

        // Impact detonation: blazing fiery dome + rising core column + ground shockwave ring.
        float expand = easeOutCubic(clamp01(ap * 2.3F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float curR = r * (0.25F + 0.85F * expand);

        if (!additive) {
            drawHemisphere(m, out, curR, 14, 6, argb(e.primaryColor, 0.52F * fade), argb(e.primaryColor, 0.08F * fade));
        } else {
            drawHemisphere(m, out, curR * 0.72F, 12, 5, argb(e.secondaryColor, 0.9F * fade), argb(e.primaryColor, 0.05F * fade));
            // Rising fiery eruption column.
            float colH = Math.min(6.5F, e.height * 0.42F) * easeOutQuad(clamp01(ap * 2.5F));
            drawCylinderY(m, out, 0.0F, colH, r * 0.35F * (1.0F - 0.35F * ap), r * 0.14F, 10,
                    argb(0xFFFFFF, 0.95F * fade), argb(e.primaryColor, 0.0F));
            // Ground shockwave ring matching the exact hit radius.
            drawHorizontalRing(m, out, 0.08F, curR * 0.78F, curR * 1.06F, 24,
                    argb(e.secondaryColor, 0.92F * fade), argb(e.primaryColor, 0.0F));
        }
    }

    // -------------------------------------------------------------------------
    // 6. MAGMA_FISSURES — Radiating molten cracks erupting into jagged 3D flame walls
    // -------------------------------------------------------------------------
    private static void renderMagmaFissures(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                            boolean inWindup, float wp, float ap, float cm, boolean additive) {
        int rays = Math.max(2, e.count);
        float len = e.length;
        float baseHalfW = Math.max(0.25F, e.width * 0.5F);

        for (int i = 0; i < rays; i++) {
            float rayDeg = i * (360.0F / rays);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-rayDeg));
            Matrix4f rm = poseStack.last().pose();

            if (inWindup) {
                float reach = len * easeOutQuad(wp);
                float alpha = (0.3F + 0.7F * easeInQuad(wp)) * cm;
                float farHalfW = baseHalfW + 0.06F * reach;
                if (!additive) {
                    int c0 = argb(e.primaryColor, 0.36F * alpha);
                    int c1 = argb(e.primaryColor, 0.22F * alpha);
                    quad(rm, out, -baseHalfW, 0.05F, 0.4F, baseHalfW, 0.05F, 0.4F,
                            farHalfW, 0.05F, reach, -farHalfW, 0.05F, reach, c0, c0, c1, c1);
                } else {
                    // Glowing molten lava crack racing down the center of each fissure.
                    float crackW = 0.16F + 0.14F * wp;
                    int core0 = argb(e.secondaryColor, 0.95F * alpha);
                    int core1 = argb(e.primaryColor, 0.75F * alpha);
                    quad(rm, out, -crackW, 0.07F, 0.4F, crackW, 0.07F, 0.4F,
                            crackW * 0.7F, 0.07F, reach, -crackW * 0.7F, 0.07F, reach, core0, core0, core1, core1);
                    // Glowing molten side rims framing the widening lane.
                    quad(rm, out, -baseHalfW, 0.06F, 0.4F, -baseHalfW + 0.12F, 0.06F, 0.4F,
                            -farHalfW + 0.12F, 0.06F, reach, -farHalfW, 0.06F, reach, core1, core1, core1, core1);
                    quad(rm, out, baseHalfW - 0.12F, 0.06F, 0.4F, baseHalfW, 0.06F, 0.4F,
                            farHalfW, 0.06F, reach, farHalfW - 0.12F, 0.06F, reach, core1, core1, core1, core1);
                }
            } else {
                // Jagged 3D vertical walls/spires of Frenzied Flame erupting skyward along every fissure!
                float rise = easeOutCubic(clamp01(ap * 3.4F));
                float fade = (1.0F - easeInQuad(ap)) * cm;
                int teeth = 7;
                float step = (len - 0.5F) / teeth;

                for (int t = 0; t < teeth; t++) {
                    float z0 = 0.5F + t * step;
                    float z1 = z0 + step;
                    float jagged = 0.68F + 0.36F * Math.abs(Mth.sin(t * 2.1F + i * 1.3F + age * 8.0F));
                    float h0 = e.height * rise * jagged * (1.0F - 0.18F * (t / (float) teeth));
                    float h1 = e.height * rise * (0.65F + 0.35F * Math.abs(Mth.cos(t * 1.9F + i + age * 7.0F)))
                            * (1.0F - 0.18F * ((t + 1) / (float) teeth));
                    float hw0 = (baseHalfW + 0.06F * z0) * 0.55F;
                    float hw1 = (baseHalfW + 0.06F * z1) * 0.55F;

                    if (!additive) {
                        int bot = argb(e.primaryColor, 0.52F * fade);
                        int top = argb(e.primaryColor, 0.0F);
                        quad(rm, out, -hw0, 0.05F, z0, -hw1, 0.05F, z1, -hw1 * 0.35F, h1, z1, -hw0 * 0.35F, h0, z0,
                                bot, bot, top, top);
                        quad(rm, out, hw0, 0.05F, z0, hw1, 0.05F, z1, hw1 * 0.35F, h1, z1, hw0 * 0.35F, h0, z0,
                                bot, bot, top, top);
                    } else {
                        // Blazing white-gold central flame curtain + tilted cross-fin for 3D volume.
                        int bot = argb(0xFFFFFF, 0.95F * fade);
                        int mid = argb(e.secondaryColor, 0.72F * fade);
                        int top = argb(e.primaryColor, 0.0F);
                        quad(rm, out, 0.0F, 0.05F, z0, 0.0F, 0.05F, z1, 0.0F, h1 * 0.85F, z1, 0.0F, h0 * 0.85F, z0,
                                bot, bot, top, top);
                        // Molten glowing floor lava strip under the erupting wall.
                        quad(rm, out, -hw0, 0.07F, z0, hw0, 0.07F, z0, hw1, 0.07F, z1, -hw1, 0.07F, z1,
                                mid, mid, mid, mid);
                    }
                }
            }
            poseStack.popPose();
        }
    }
    // -------------------------------------------------------------------------
    // 7. NOVA_DOME — Contracting charge sphere exploding into layered 3D blazing dome
    // -------------------------------------------------------------------------
    private static void renderNovaDome(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                       boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;

        if (inWindup) {
            float alpha = (0.28F + 0.72F * easeInQuad(wp)) * cm;
            float orbY = Math.min(2.6F, e.height * 0.65F);
            if (!additive) {
                drawHorizontalRing(m, out, 0.07F, 0.0F, r, 32,
                        argb(e.primaryColor, 0.06F * alpha), argb(e.primaryColor, 0.22F * alpha));
                float orbR = Mth.lerp(easeOutQuad(wp), r * 0.40F, 0.55F);
                drawSphere(m, out, 0.0F, orbY, 0.0F, orbR, 12, 6, argb(e.primaryColor, 0.40F * alpha));
            } else {
                // Crisp glowing perimeter bevel + contracting timing ring converging to detonation.
                drawHorizontalRing(m, out, 0.08F, r * 0.94F, r * 1.01F, 36,
                        argb(e.primaryColor, 0.82F * alpha), argb(e.secondaryColor, alpha));
                drawCylinderY(m, out, 0.06F, 0.26F, r, r, 32,
                        argb(e.secondaryColor, 0.48F * alpha), argb(e.primaryColor, 0.0F));
                float shrinkR = Math.max(0.25F, r * (1.0F - wp));
                drawHorizontalRing(m, out, 0.09F, shrinkR, shrinkR + 0.22F, 32,
                        argb(0xFFFFFF, 0.92F * alpha), argb(e.primaryColor, 0.0F));
                // Glowing intensifying sun-core + inward gathering filaments.
                float coreR = 0.35F + 0.75F * easeInQuad(wp);
                drawSphere(m, out, 0.0F, orbY, 0.0F, coreR, 10, 5, argb(0xFFFFFF, 0.92F * alpha));
                for (int i = 0; i < 8; i++) {
                    float fp = (age * 2.4F + i * 0.125F) % 1.0F;
                    float a = i * (TAU / 8.0F) + age * 1.2F;
                    float d0 = Mth.lerp(fp, r * 0.95F, 0.4F);
                    float d1 = Math.max(0.2F, d0 - r * 0.28F);
                    float y0 = Mth.lerp(fp, 0.2F, orbY);
                    float y1 = Mth.lerp(clamp01(fp + 0.25F), 0.2F, orbY);
                    float fa = Mth.sin(fp * (float) Math.PI) * 0.9F * alpha;
                    drawRibbonSegment(m, out, Mth.cos(a) * d0, y0, Mth.sin(a) * d0,
                            Mth.cos(a) * d1, y1, Mth.sin(a) * d1, 0.1F,
                            argb(e.primaryColor, 0.0F), argb(e.secondaryColor, fa));
                }
            }
            return;
        }

        // Explosive 3-layer blazing 3D dome + dual tilted equatorial shock rings (boss stays visible inside).
        float expand = easeOutCubic(clamp01(ap * 2.1F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float curR = r * (0.18F + 0.86F * expand);

        if (!additive) {
            drawHemisphere(m, out, curR, 20, 8, argb(e.primaryColor, 0.42F * fade), argb(e.primaryColor, 0.10F * fade));
        } else {
            drawHemisphere(m, out, curR * 0.84F, 18, 7, argb(e.secondaryColor, 0.85F * fade), argb(e.primaryColor, 0.08F * fade));
            float flashFade = (1.0F - clamp01(ap * 2.6F)) * cm;
            if (flashFade > 0.01F) {
                drawHemisphere(m, out, curR * 0.56F, 14, 5, argb(0xFFFFFF, flashFade), argb(e.secondaryColor, 0.25F * flashFade));
            }
            // Ground shockwave ring at the dome perimeter.
            drawHorizontalRing(m, out, 0.1F, curR * 0.84F, curR * 1.08F, 36,
                    argb(e.secondaryColor, 0.95F * fade), argb(e.primaryColor, 0.0F));
            // Two tilted equatorial shockwave rings spinning outward.
            for (int i = 0; i < 2; i++) {
                poseStack.pushPose();
                poseStack.translate(0.0F, curR * 0.22F, 0.0F);
                poseStack.mulPose(Axis.XP.rotationDegrees(i == 0 ? 18.0F : -22.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(i == 0 ? 14.0F : -16.0F));
                drawHorizontalRing(poseStack.last().pose(), out, 0.0F, curR * 0.92F, curR * 1.12F, 32,
                        argb(e.secondaryColor, 0.82F * fade), argb(e.primaryColor, 0.0F));
                poseStack.popPose();
            }
        }
    }

    // -------------------------------------------------------------------------
    // 8. SHOCKWAVE_RING — Expanding 3D cylindrical fire wall from inner width to outer radius
    // -------------------------------------------------------------------------
    private static void renderShockwaveRing(SpellVfxInstance e, Matrix4f m, VertexConsumer out, float age,
                                            boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float innerR = Math.min(e.width, e.radius * 0.92F);
        float outerR = e.radius;

        if (inWindup) {
            float alpha = (0.28F + 0.72F * easeInQuad(wp)) * cm;
            float waveR = Mth.lerp(wp, innerR, outerR);
            if (!additive) {
                drawHorizontalRing(m, out, 0.05F, innerR, outerR, 36,
                        argb(e.primaryColor, 0.16F * alpha), argb(e.primaryColor, 0.26F * alpha));
            } else {
                // Crisp inner & outer glowing boundary rings + outward filling wave ring.
                drawHorizontalRing(m, out, 0.07F, innerR, innerR + 0.2F, 32,
                        argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.25F * alpha));
                drawHorizontalRing(m, out, 0.07F, outerR - 0.22F, outerR, 36,
                        argb(e.primaryColor, 0.78F * alpha), argb(e.secondaryColor, alpha));
                drawHorizontalRing(m, out, 0.08F, Math.max(innerR, waveR - 0.35F), Math.min(outerR, waveR + 0.12F), 36,
                        argb(0xFFFFFF, 0.92F * alpha), argb(e.primaryColor, 0.0F));
            }
            return;
        }

        // Roaring curved 3D fire wall sweeping outward from innerR to outerR!
        float sweep = easeOutCubic(clamp01(ap * 2.1F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float curR = Mth.lerp(sweep, innerR, outerR);
        float wallH = e.height * (0.55F + 0.55F * Mth.sin(clamp01(ap * 1.4F) * (float) Math.PI));

        int segs = 36;
        for (int i = 0; i < segs; i++) {
            float a0 = i * (TAU / segs);
            float a1 = (i + 1) * (TAU / segs);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float h0 = wallH * (0.76F + 0.24F * Mth.sin(i * 1.7F + age * 14.0F));
            float h1 = wallH * (0.76F + 0.24F * Mth.sin((i + 1) * 1.7F + age * 14.0F));
            float topR = curR + 0.55F;

            if (!additive) {
                int bot = argb(e.primaryColor, 0.54F * fade);
                int top = argb(e.primaryColor, 0.0F);
                quad(m, out, c0 * curR, 0.04F, s0 * curR, c1 * curR, 0.04F, s1 * curR,
                        c1 * topR, h1, s1 * topR, c0 * topR, h0, s0 * topR, bot, bot, top, top);
            } else {
                int bot = argb(0xFFFFFF, 0.92F * fade);
                int top = argb(e.secondaryColor, 0.0F);
                float coreR = Math.max(0.2F, curR - 0.14F);
                quad(m, out, c0 * coreR, 0.05F, s0 * coreR, c1 * coreR, 0.05F, s1 * coreR,
                        c1 * curR, h1 * 0.62F, s1 * curR, c0 * curR, h0 * 0.62F, s0 * curR, bot, bot, top, top);
            }
        }
        if (additive) {
            drawHorizontalRing(m, out, 0.09F, Math.max(innerR, curR - 1.1F), curR + 0.25F, 36,
                    argb(e.secondaryColor, 0.92F * fade), argb(e.primaryColor, 0.0F));
            drawHorizontalRing(m, out, wallH * 0.55F, curR + 0.15F, curR + 0.45F, 32,
                    argb(e.secondaryColor, 0.72F * fade), argb(e.primaryColor, 0.0F));
        }
    }

    // -------------------------------------------------------------------------
    // 9. FLAME_WAVE — Traveling 3D curved crescent tsunami of fire surging across a cone
    // -------------------------------------------------------------------------
    private static void renderFlameWave(SpellVfxInstance e, Matrix4f m, VertexConsumer out, float age,
                                        boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float halfDeg = e.angle * 0.5F;
        float len = e.length;

        if (inWindup) {
            float alpha = (0.28F + 0.72F * easeInQuad(wp)) * cm;
            float reach = len * easeOutQuad(wp);
            if (!additive) {
                drawArcRibbon(m, out, -halfDeg, halfDeg, 0.6F, reach, 0.05F, 0.05F, 20,
                        argb(e.primaryColor, 0.12F * alpha), argb(e.primaryColor, 0.26F * alpha));
            } else {
                drawArcRibbon(m, out, -halfDeg, halfDeg, Math.max(0.5F, reach - 0.45F), reach, 0.07F, 0.07F, 22,
                        argb(e.primaryColor, 0.4F * alpha), argb(e.secondaryColor, alpha));
                // Glowing side border rails framing the wave cone.
                float lRad = -halfDeg * DEG_TO_RAD;
                float rRad = halfDeg * DEG_TO_RAD;
                drawGroundStrip(m, out, 0.0F, 0.0F, -Mth.sin(lRad) * reach, Mth.cos(lRad) * reach,
                        0.1F, 0.07F, argb(e.secondaryColor, 0.8F * alpha), argb(e.primaryColor, 0.35F * alpha));
                drawGroundStrip(m, out, 0.0F, 0.0F, -Mth.sin(rRad) * reach, Mth.cos(rRad) * reach,
                        0.1F, 0.07F, argb(e.secondaryColor, 0.8F * alpha), argb(e.primaryColor, 0.35F * alpha));
            }
            return;
        }

        // Crashing 3D curved flame wall surging forward from r=1.0 to r=length!
        float travel = easeOutQuad(clamp01(ap * 1.45F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float curR = Mth.lerp(travel, 1.2F, len);
        float wakeR = Math.max(0.5F, curR - Math.min(3.5F, len * 0.32F));
        float crestH = e.height * (0.75F + 0.35F * Mth.sin(ap * (float) Math.PI));

        int segs = 20;
        for (int i = 0; i < segs; i++) {
            float f0 = i / (float) segs;
            float f1 = (i + 1) / (float) segs;
            float a0 = Mth.lerp(f0, -halfDeg, halfDeg) * DEG_TO_RAD;
            float a1 = Mth.lerp(f1, -halfDeg, halfDeg) * DEG_TO_RAD;
            // Taper height smoothly at the left/right edges of the crescent wave.
            float taper0 = Mth.sin(f0 * (float) Math.PI);
            float taper1 = Mth.sin(f1 * (float) Math.PI);
            float wave0 = 0.82F + 0.18F * Mth.sin(i * 1.6F + age * 15.0F);
            float wave1 = 0.82F + 0.18F * Mth.sin((i + 1) * 1.6F + age * 15.0F);
            float h0 = crestH * (0.25F + 0.75F * taper0) * wave0;
            float h1 = crestH * (0.25F + 0.75F * taper1) * wave1;

            float fx0 = -Mth.sin(a0);
            float fz0 = Mth.cos(a0);
            float fx1 = -Mth.sin(a1);
            float fz1 = Mth.cos(a1);

            float topR = curR + 0.75F; // leaning forward like a crashing wave crest
            if (!additive) {
                int bot = argb(e.primaryColor, 0.58F * fade);
                int top = argb(e.primaryColor, 0.04F * fade);
                // Crashing outer fire wall.
                quad(m, out, fx0 * curR, 0.04F, fz0 * curR, fx1 * curR, 0.04F, fz1 * curR,
                        fx1 * topR, h1, fz1 * topR, fx0 * topR, h0, fz0 * topR, bot, bot, top, top);
                // Trailing scorched ground wake behind the crest.
                quad(m, out, fx0 * wakeR, 0.06F, fz0 * wakeR, fx1 * wakeR, 0.06F, fz1 * wakeR,
                        fx1 * curR, 0.06F, fz1 * curR, fx0 * curR, 0.06F, fz0 * curR,
                        argb(e.primaryColor, 0.0F), argb(e.primaryColor, 0.0F), bot, bot);
            } else {
                // Blinding white-gold leading inner wave face & incandescent crest lip.
                int bot = argb(0xFFFFFF, 0.95F * fade);
                int top = argb(e.secondaryColor, 0.12F * fade);
                float coreR = curR + 0.18F;
                float coreTopR = topR + 0.12F;
                quad(m, out, fx0 * coreR, 0.06F, fz0 * coreR, fx1 * coreR, 0.06F, fz1 * coreR,
                        fx1 * coreTopR, h1 * 0.72F, fz1 * coreTopR, fx0 * coreTopR, h0 * 0.72F, fz0 * coreTopR,
                        bot, bot, top, top);
            }
        }
    }

    // -------------------------------------------------------------------------
    // 10. FLAME_PILLAR — Towering rotating vortex column + 3 helical ribbons
    // -------------------------------------------------------------------------
    private static void renderFlamePillar(SpellVfxInstance e, Matrix4f m, VertexConsumer out, float age,
                                          boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float r = e.radius;
        if (inWindup) {
            float alpha = (0.3F + 0.7F * easeInQuad(wp)) * cm;
            if (!additive) {
                drawHorizontalRing(m, out, 0.05F, 0.0F, r, 24,
                        argb(e.primaryColor, 0.14F * alpha), argb(e.primaryColor, 0.28F * alpha));
            } else {
                drawHorizontalRing(m, out, 0.07F, r * 0.9F, r, 28,
                        argb(e.primaryColor, 0.8F * alpha), argb(e.secondaryColor, alpha));
                float shrinkR = Math.max(0.15F, r * (1.0F - wp));
                drawHorizontalRing(m, out, 0.08F, shrinkR, shrinkR + 0.14F, 24,
                        argb(0xFFFFFF, 0.9F * alpha), argb(e.primaryColor, 0.0F));
            }
            return;
        }

        float rise = easeOutCubic(clamp01(ap * 3.0F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float h = e.height * rise;
        float curR = r * (1.0F - 0.25F * ap);

        if (!additive) {
            drawCylinderY(m, out, 0.0F, h, curR * 1.05F, curR * 1.28F, 14,
                    argb(e.primaryColor, 0.52F * fade), argb(e.primaryColor, 0.04F * fade));
        } else {
            drawCylinderY(m, out, 0.0F, h * 0.95F, curR * 0.62F, curR * 0.45F, 12,
                    argb(e.secondaryColor, 0.88F * fade), argb(e.primaryColor, 0.04F * fade));
            drawCylinderY(m, out, 0.0F, h * 0.88F, curR * 0.28F, curR * 0.12F, 10,
                    argb(0xFFFFFF, 0.95F * fade), argb(e.secondaryColor, 0.08F * fade));

            // Three spiraling 3D helical flame ribbons shooting skyward.
            for (int helix = 0; helix < 3; helix++) {
                float offset = helix * (TAU / 3.0F) + age * 8.5F;
                int segs = 16;
                for (int s = 0; s < segs; s++) {
                    float f0 = s / (float) segs;
                    float f1 = (s + 1) / (float) segs;
                    float a0 = f0 * TAU * 1.8F + offset;
                    float a1 = f1 * TAU * 1.8F + offset;
                    float hr0 = curR * (1.05F + 0.3F * f0);
                    float hr1 = curR * (1.05F + 0.3F * f1);
                    drawRibbonSegment(m, out,
                            Mth.cos(a0) * hr0, f0 * h, Mth.sin(a0) * hr0,
                            Mth.cos(a1) * hr1, f1 * h, Mth.sin(a1) * hr1,
                            0.18F * (1.0F - 0.5F * f0),
                            argb(e.secondaryColor, 0.88F * fade * (1.0F - f0 * 0.85F)),
                            argb(e.primaryColor, 0.65F * fade * (1.0F - f1 * 0.85F)));
                }
            }

            // Expanding base shock ring.
            float ringR = r * (0.7F + 0.85F * easeOutQuad(ap));
            drawHorizontalRing(m, out, 0.08F, ringR * 0.82F, ringR, 28,
                    argb(e.secondaryColor, 0.88F * fade), argb(e.primaryColor, 0.0F));
        }
    }

    // -------------------------------------------------------------------------
    // 11. VORTEX_SLASH — 360-degree spinning double-layered flaming whirlwind
    // -------------------------------------------------------------------------
    private static void renderVortexSlash(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                          boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;

        if (inWindup) {
            float alpha = (0.28F + 0.72F * easeInQuad(wp)) * cm;
            if (!additive) {
                drawHorizontalRing(m, out, 0.05F, r * 0.35F, r, 32,
                        argb(e.primaryColor, 0.08F * alpha), argb(e.primaryColor, 0.24F * alpha));
            } else {
                drawHorizontalRing(m, out, 0.07F, r * 0.93F, r, 32,
                        argb(e.primaryColor, 0.8F * alpha), argb(e.secondaryColor, alpha));
                // Spinning inward wind arc telegraphing the imminent cyclone.
                float spinDeg = age * (e.reverse ? -340.0F : 340.0F);
                float curR = Mth.lerp(wp, r * 1.12F, r * 0.72F);
                drawArcRibbon(m, out, spinDeg, spinDeg + 150.0F, curR * 0.88F, curR, 1.4F, 1.55F, 16,
                        argb(e.primaryColor, 0.0F), argb(e.secondaryColor, 0.88F * alpha));
            }
            return;
        }

        float env = clamp01(ap * 5.0F) * (1.0F - easeInQuad(clamp01((ap - 0.55F) / 0.45F))) * cm;
        if (env <= 0.01F) {
            return;
        }
        float dir = e.reverse ? -1.0F : 1.0F;
        float spin = age * 540.0F * dir;

        // Subtle fiery tornado funnel around Midra.
        if (!additive) {
            drawCylinderY(m, out, 0.15F, e.height, r * 0.48F, r * 0.98F, 18,
                    argb(e.primaryColor, 0.28F * env), argb(e.primaryColor, 0.03F * env));
        }

        // Two stacked, oppositely tilted 360-degree spinning greatsword crescent rings!
        for (int layer = 0; layer < 2; layer++) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 1.05F + layer * 1.25F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(layer == 0 ? 11.0F : -13.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(layer == 0 ? -9.0F : 10.0F));
            Matrix4f lm = poseStack.last().pose();
            float baseDeg = spin + layer * 180.0F;
            float layerR = r * (layer == 0 ? 1.0F : 0.92F);

            if (!additive) {
                drawTaperedSlash(lm, out, baseDeg - 230.0F, baseDeg, layerR * 0.42F, layerR * 1.05F, 0.0F, 0.22F, 24,
                        e.primaryColor, e.primaryColor, 0.08F * env, 0.52F * env, age);
            } else {
                drawTaperedSlash(lm, out, baseDeg - 220.0F, baseDeg, layerR * 0.65F, layerR * 1.03F, 0.0F, 0.15F, 24,
                        e.primaryColor, e.secondaryColor, 0.15F * env, 0.92F * env, age);
                drawTaperedSlash(lm, out, baseDeg - 160.0F, baseDeg, layerR * 0.88F, layerR * 1.01F, 0.04F, 0.02F, 18,
                        e.secondaryColor, 0xFFFFFF, 0.3F * env, env, age);
            }
            poseStack.popPose();
        }
    }

    // -------------------------------------------------------------------------
    // 12. AMBER_SPIRE — Chunky 3-needle faceted Minecraft-style amber cluster bursting from cracked soil
    // -------------------------------------------------------------------------
    private static void renderAmberSpire(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out,
                                         boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;

        if (inWindup) {
            float alpha = (0.30F + 0.58F * easeInQuad(wp)) * cm;
            if (!additive) {
                // Crisp octagonal earthen-amber fracture ring (raised cleanly above grass) + 3 soil fissures.
                drawHorizontalRing(m, out, 0.07F, r * 0.78F, r, 8,
                        argb(darkenRgb(e.primaryColor, 0.52F), 0.42F * alpha),
                        argb(e.primaryColor, 0.72F * alpha));
                for (int i = 0; i < 3; i++) {
                    float a = (i * (TAU / 3.0F)) + 0.35F;
                    float reach = r * (0.45F + 0.5F * easeOutQuad(wp));
                    drawGroundStrip(m, out, 0.0F, 0.0F, Mth.cos(a) * reach, Mth.sin(a) * reach,
                            0.1F, 0.08F, argb(e.secondaryColor, 0.68F * alpha), argb(e.primaryColor, 0.14F * alpha));
                }
                // Tiny emerging amber crystal tip pushing through the soil just before eruption.
                if (wp > 0.45F) {
                    float bud = (wp - 0.45F) / 0.55F;
                    drawFacetedCrystal(m, out, 0.24F, 0.45F * bud, 5, e.primaryColor, e.secondaryColor, 0.75F * alpha);
                }
            } else {
                drawHorizontalRing(m, out, 0.085F, r * 0.88F, r * 0.99F, 8,
                        argb(e.primaryColor, 0.18F * alpha), argb(e.secondaryColor, 0.36F * alpha));
            }
            return;
        }

        // Fast punchy upward eruption, brief solid crystal hold, smooth sink back into earth.
        float pop = easeOutCubic(clamp01(ap * 4.6F));
        float recede = ap > 0.68F ? clamp01((ap - 0.68F) / 0.32F) : 0.0F;
        float scaleH = pop * (1.0F - easeInQuad(recede));
        float alpha = (1.0F - 0.85F * easeInQuad(recede)) * cm;
        if (scaleH <= 0.02F || alpha <= 0.01F) {
            return;
        }

        float baseR = Math.min(r * 0.42F, 0.85F);
        float h = e.height * scaleH;

        if (!additive) {
            // Solid-looking translucent 3-tone faceted central amber prism.
            drawFacetedCrystal(m, out, baseR, h, 6, e.primaryColor, e.secondaryColor, 0.86F * alpha);
            // Three staggered angled companion crystal shards forming an organic cluster.
            float[] sideYaws = {42.0F, 168.0F, -78.0F};
            float[] sideScales = {0.62F, 0.48F, 0.54F};
            float[] sideTilts = {-22.0F, -29.0F, -25.0F};
            for (int side = 0; side < 3; side++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(sideYaws[side]));
                poseStack.translate(baseR * 0.62F, 0.0F, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(sideTilts[side]));
                drawFacetedCrystal(poseStack.last().pose(), out, baseR * 0.48F, h * sideScales[side], 5,
                        darkenRgb(e.primaryColor, 0.84F), e.secondaryColor, 0.82F * alpha);
                poseStack.popPose();
            }
            // Earthy cracked base collar expanding slightly on eruption.
            float collarR = r * (0.75F + 0.25F * pop);
            drawHorizontalRing(m, out, 0.075F, baseR * 0.45F, collarR, 8,
                    argb(darkenRgb(e.primaryColor, 0.40F), 0.58F * alpha),
                    argb(e.primaryColor, 0.0F));
        } else {
            // Restrained warm honey-gold internal facet highlight (vanilla-friendly, never neon).
            drawFacetedCrystal(m, out, baseR * 0.52F, h * 0.90F, 6, e.primaryColor, e.secondaryColor, 0.24F * alpha);
        }
    }

    // -------------------------------------------------------------------------
    // 13. AMBER_GEODE_TRAP — 4 angled amber crystal jaws clamping shut around a locked player spot
    // -------------------------------------------------------------------------
    private static void renderAmberGeodeTrap(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out,
                                             boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;
        int prongs = Math.max(4, Math.min(8, e.count));

        if (inWindup) {
            float warnBoost = wp > 0.78F ? 1.0F + 0.22F * Mth.sin((wp - 0.78F) * 40.0F) : 1.0F;
            float alpha = clamp01((0.32F + 0.58F * easeInQuad(wp)) * warnBoost) * cm;
            if (!additive) {
                // Clean octagonal earthen danger border + emerging crystal tips poking from the soil.
                drawHorizontalRing(m, out, 0.07F, r * 0.85F, r, 8,
                        argb(darkenRgb(e.primaryColor, 0.50F), 0.48F * alpha),
                        argb(e.primaryColor, 0.76F * alpha));
                float budH = 0.24F + 0.46F * easeInQuad(wp);
                for (int i = 0; i < prongs; i++) {
                    float deg = i * (360.0F / prongs) + 45.0F;
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(deg));
                    poseStack.translate(r * 0.84F, 0.0F, 0.0F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(36.0F));
                    drawFacetedCrystal(poseStack.last().pose(), out, 0.26F, budH, 5,
                            e.primaryColor, e.secondaryColor, 0.76F * alpha);
                    poseStack.popPose();
                }
            } else {
                // Thin contracting octagonal timing ring so the jaw-snap moment is unmistakably readable.
                float shrinkR = Math.max(0.25F, r * (1.0F - wp * 0.82F));
                drawHorizontalRing(m, out, 0.085F, shrinkR, shrinkR + 0.12F, 8,
                        argb(e.secondaryColor, 0.45F * alpha), argb(e.primaryColor, 0.05F * alpha));
            }
            return;
        }

        float snap = easeOutQuart(clamp01(ap * 5.2F));
        float recede = ap > 0.65F ? clamp01((ap - 0.65F) / 0.35F) : 0.0F;
        float scaleH = snap * (1.0F - easeInQuad(recede));
        float alpha = (1.0F - 0.85F * easeInQuad(recede)) * cm;
        if (scaleH <= 0.02F || alpha <= 0.01F) {
            return;
        }
        // Dynamic jaw-clamping angle: erupts tilted wide (36 deg) and clamps inward (18 deg)!
        float clampAngle = Mth.lerp(snap, 36.0F, 18.0F);

        for (int i = 0; i < prongs; i++) {
            float deg = i * (360.0F / prongs) + 45.0F;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            poseStack.translate(r * 0.86F, 0.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(clampAngle));
            Matrix4f pm = poseStack.last().pose();
            if (!additive) {
                drawFacetedCrystal(pm, out, 0.44F, e.height * scaleH, 5, e.primaryColor, e.secondaryColor, 0.88F * alpha);
            } else {
                drawFacetedCrystal(pm, out, 0.22F, e.height * 0.86F * scaleH, 5, e.primaryColor, e.secondaryColor, 0.22F * alpha);
            }
            poseStack.popPose();
        }
        if (!additive) {
            drawFacetedCrystal(m, out, 0.36F, e.height * 0.56F * scaleH, 5,
                    darkenRgb(e.primaryColor, 0.85F), e.secondaryColor, 0.82F * alpha);
            drawHorizontalRing(m, out, 0.075F, 0.2F, r, 8,
                    argb(darkenRgb(e.primaryColor, 0.42F), 0.46F * alpha), argb(e.primaryColor, 0.0F));
        }
    }

    // -------------------------------------------------------------------------
    // 14. GROUND_FRACTURE — Multi-segment jagged zigzag earth cracks + popping crust slabs
    // -------------------------------------------------------------------------
    private static void renderGroundFracture(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out,
                                             boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;
        int cracks = Math.max(4, Math.min(10, e.count));

        if (inWindup) {
            float alpha = (0.28F + 0.56F * easeInQuad(wp)) * cm;
            if (!additive) {
                drawHorizontalRing(m, out, 0.07F, r * 0.84F, r, 12,
                        argb(darkenRgb(e.primaryColor, 0.52F), 0.40F * alpha),
                        argb(e.primaryColor, 0.68F * alpha));
            } else {
                float fillR = r * easeOutQuad(wp);
                drawHorizontalRing(m, out, 0.085F, Math.max(0.15F, fillR - 0.24F), fillR, 12,
                        argb(e.primaryColor, 0.05F * alpha), argb(e.secondaryColor, 0.36F * alpha));
            }
            return;
        }

        float rise = easeOutCubic(clamp01(ap * 4.8F));
        float settle = ap > 0.55F ? clamp01((ap - 0.55F) / 0.45F) : 0.0F;
        float slabH = e.height * rise * (1.0F - easeInQuad(settle));
        float fade = (1.0F - easeInQuad(settle)) * cm;
        if (fade <= 0.01F) {
            return;
        }

        if (!additive) {
            // Multi-segment jagged zigzag earthen fracture seams radiating across the crater.
            int darkEarth = argb(darkenRgb(e.primaryColor, 0.38F), 0.82F * fade);
            int midCrack = argb(darkenRgb(e.primaryColor, 0.58F), 0.60F * fade);
            int crackTip = argb(e.primaryColor, 0.05F * fade);
            for (int i = 0; i < cracks; i++) {
                float a = i * (TAU / cracks) + 0.2F;
                float jag = hashSigned(i * 17 + 5) * 0.32F;
                float len = r * (0.85F + 0.18F * Mth.sin(i * 2.7F)) * rise;
                float midLen = len * 0.52F;
                float mx = Mth.cos(a + jag * 0.35F) * midLen;
                float mz = Mth.sin(a + jag * 0.35F) * midLen;
                float ex = Mth.cos(a - jag * 0.18F) * len;
                float ez = Mth.sin(a - jag * 0.18F) * len;
                drawGroundStrip(m, out, 0.0F, 0.0F, mx, mz, 0.14F, 0.075F, darkEarth, midCrack);
                drawGroundStrip(m, out, mx, mz, ex, ez, 0.09F, 0.075F, midCrack, crackTip);
            }
            // Chunky tilted earthen-amber crust slabs popping outward around the impact ring.
            for (int i = 0; i < cracks; i++) {
                float deg = i * (360.0F / cracks) + 18.0F;
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(deg));
                poseStack.translate(r * (0.62F + 0.12F * rise), 0.0F, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(-24.0F - 10.0F * rise));
                float sh = slabH * (0.75F + 0.3F * Math.abs(Mth.sin(i * 1.9F)));
                drawFacetedCrystal(poseStack.last().pose(), out, 0.44F, sh, 4,
                        darkenRgb(e.primaryColor, 0.65F), e.primaryColor, 0.88F * fade);
                poseStack.popPose();
            }
        } else {
            // Restrained warm amber crack glow along the center fissures.
            for (int i = 0; i < cracks; i++) {
                float a = i * (TAU / cracks) + 0.2F;
                float jag = hashSigned(i * 17 + 5) * 0.32F;
                float len = r * 0.72F * rise;
                float mx = Mth.cos(a + jag * 0.35F) * len * 0.55F;
                float mz = Mth.sin(a + jag * 0.35F) * len * 0.55F;
                drawGroundStrip(m, out, 0.0F, 0.0F, mx, mz, 0.055F, 0.085F,
                        argb(e.secondaryColor, 0.34F * fade), argb(e.primaryColor, 0.04F * fade));
            }
        }
    }

    // -------------------------------------------------------------------------
    // 15. SHARD_SPRAY — Fan of chunky tumbling 3D amber crystals + crisp motion streaks
    // -------------------------------------------------------------------------
    private static void renderShardSpray(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                         boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float halfDeg = e.angle * 0.5F;

        if (inWindup) {
            float alpha = (0.28F + 0.55F * easeInQuad(wp)) * cm;
            float reach = e.length * 0.72F * easeOutQuad(wp);
            if (!additive) {
                drawArcRibbon(m, out, -halfDeg, halfDeg, 0.8F, reach, 0.07F, 0.07F, 14,
                        argb(e.primaryColor, 0.06F * alpha), argb(e.primaryColor, 0.30F * alpha));
            } else {
                drawArcRibbon(m, out, -halfDeg, halfDeg, Math.max(0.7F, reach - 0.24F), reach, 0.08F, 0.08F, 14,
                        argb(e.primaryColor, 0.12F * alpha), argb(e.secondaryColor, 0.40F * alpha));
            }
            return;
        }

        int shards = Math.max(4, Math.min(14, e.count));
        float fade = (1.0F - easeInQuad(clamp01((ap - 0.55F) / 0.45F))) * cm;
        if (fade <= 0.01F) {
            return;
        }

        for (int i = 0; i < shards; i++) {
            float frac = shards > 1 ? i / (float) (shards - 1) : 0.5F;
            float deg = Mth.lerp(frac, -halfDeg, halfDeg) + hashSigned(i * 19 + 3) * 4.5F;
            float rad = deg * DEG_TO_RAD;
            float speedScale = 0.78F + 0.28F * Math.abs(hashSigned(i * 29 + 7));
            float dist = e.length * speedScale * easeOutQuad(ap);
            float prevAp = Math.max(0.0F, ap - 0.14F);
            float prevDist = e.length * speedScale * easeOutQuad(prevAp);
            // Natural ballistic arc with gravity: kicks up from knuckle drag then drops toward the floor.
            float peakH = e.height * (0.55F + 0.35F * Math.abs(hashSigned(i * 13 + 5)));
            float yPos = Math.max(0.18F, 0.35F + 4.0F * peakH * ap * (1.0F - ap));
            float prevY = Math.max(0.18F, 0.35F + 4.0F * peakH * prevAp * (1.0F - prevAp));
            float sinR = -Mth.sin(rad);
            float cosR = Mth.cos(rad);
            float sx = sinR * dist;
            float sz = cosR * dist;
            float shardSize = 0.28F + 0.16F * Math.abs(hashSigned(i * 41 + 11));

            if (additive && ap > 0.04F) {
                // Crisp subtle amber motion streak trailing each airborne shard.
                drawRibbonSegment(m, out, sinR * prevDist, prevY, cosR * prevDist, sx, yPos, sz, 0.055F,
                        argb(e.primaryColor, 0.0F), argb(e.secondaryColor, 0.38F * fade));
            }

            poseStack.pushPose();
            poseStack.translate(sx, yPos, sz);
            poseStack.mulPose(Axis.YP.rotationDegrees(-deg + age * (180.0F + i * 35.0F)));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * (260.0F + i * 40.0F)));
            Matrix4f sm = poseStack.last().pose();
            if (!additive) {
                drawOctaShard(sm, out, shardSize, shardSize * 1.9F, e.primaryColor, e.secondaryColor, 0.88F * fade);
            } else {
                drawOctaShard(sm, out, shardSize * 0.52F, shardSize * 1.25F, e.primaryColor, e.secondaryColor, 0.22F * fade);
            }
            poseStack.popPose();
        }
    }

    // -------------------------------------------------------------------------
    // 16. RESONANCE_RING — Ring of spaced chunky amber crystal spikes across [width, radius]
    // -------------------------------------------------------------------------
    private static void renderResonanceRing(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out,
                                            boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float innerR = Math.min(e.width, e.radius * 0.9F);
        float outerR = e.radius;
        float midR = (innerR + outerR) * 0.5F;
        int spikes = Math.max(6, Math.min(18, e.count));
        int ringSegs = outerR > 12.0F ? 36 : 18;

        if (inWindup) {
            float alpha = (0.30F + 0.60F * easeInQuad(wp)) * cm;
            if (!additive) {
                // Crisp inner and outer band outlines clearly showing the safe interior & exterior.
                drawHorizontalRing(m, out, 0.07F, innerR, innerR + 0.22F, ringSegs,
                        argb(e.primaryColor, 0.78F * alpha), argb(e.primaryColor, 0.25F * alpha));
                drawHorizontalRing(m, out, 0.07F, outerR - 0.24F, outerR, ringSegs,
                        argb(e.primaryColor, 0.25F * alpha), argb(e.primaryColor, 0.78F * alpha));
                // Small emerging crystal buds spaced evenly around the ring midpoint.
                float budH = 0.20F + 0.36F * easeInQuad(wp);
                for (int i = 0; i < spikes; i++) {
                    float deg = i * (360.0F / spikes);
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(deg));
                    poseStack.translate(midR, 0.0F, 0.0F);
                    drawFacetedCrystal(poseStack.last().pose(), out, 0.24F, budH, 5,
                            e.primaryColor, e.secondaryColor, 0.76F * alpha);
                    poseStack.popPose();
                }
            } else {
                if (innerR > 1.0F) {
                    drawHorizontalRing(m, out, 0.085F, innerR, innerR + 0.24F, ringSegs,
                            argb(e.secondaryColor, 0.75F * alpha), argb(e.primaryColor, 0.12F * alpha));
                }
                float waveR = Mth.lerp(wp, innerR, outerR);
                drawHorizontalRing(m, out, 0.09F, Math.max(innerR, waveR - 0.28F), Math.min(outerR, waveR + 0.12F), ringSegs,
                        argb(e.secondaryColor, 0.55F * alpha), argb(e.primaryColor, 0.05F * alpha));
            }
            return;
        }

        float pop = easeOutCubic(clamp01(ap * 4.6F));
        float recede = ap > 0.66F ? clamp01((ap - 0.66F) / 0.34F) : 0.0F;
        float scaleH = pop * (1.0F - easeInQuad(recede));
        float alpha = (1.0F - 0.85F * easeInQuad(recede)) * cm;
        if (scaleH <= 0.02F || alpha <= 0.01F) {
            return;
        }

        for (int i = 0; i < spikes; i++) {
            float deg = i * (360.0F / spikes);
            float var = 0.76F + 0.32F * Math.abs(Mth.sin(i * 2.3F));
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            poseStack.translate(midR, 0.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-12.0F));
            Matrix4f sm = poseStack.last().pose();
            if (!additive) {
                drawFacetedCrystal(sm, out, 0.44F, e.height * var * scaleH, 5,
                        e.primaryColor, e.secondaryColor, 0.88F * alpha);
            } else {
                drawFacetedCrystal(sm, out, 0.22F, e.height * 0.84F * var * scaleH, 5,
                        e.primaryColor, e.secondaryColor, 0.22F * alpha);
            }
            poseStack.popPose();
        }
        if (!additive) {
            drawHorizontalRing(m, out, 0.075F, innerR, outerR, 20,
                    argb(darkenRgb(e.primaryColor, 0.42F), 0.42F * alpha),
                    argb(e.primaryColor, 0.04F * alpha));
        }
    }

    // -------------------------------------------------------------------------
    // 17. CLAW_RAKE — Crisp, understated 3-prong physical melee claw trails
    // -------------------------------------------------------------------------
    private static void renderClawRake(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out,
                                       float ap, float cm, boolean additive) {
        float halfDeg = e.angle * 0.5F;
        float startDeg = e.reverse ? halfDeg : -halfDeg;
        float endDeg = e.reverse ? -halfDeg : halfDeg;
        float headP = easeOutQuart(clamp01(ap * 3.6F));
        float tailP = easeInQuad(clamp01((ap - 0.15F) / 0.85F));
        float curStart = Mth.lerp(tailP, startDeg, endDeg);
        float curEnd = Mth.lerp(headP, startDeg, endDeg);
        float fade = (1.0F - easeInQuad(ap)) * cm;
        if (Math.abs(curEnd - curStart) < 0.5F || fade <= 0.01F) {
            return;
        }

        poseStack.pushPose();
        if (Math.abs(e.tilt) > 0.01F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(e.tilt));
        }
        Matrix4f m = poseStack.last().pose();

        // Three parallel lean claw marks (middle talon slightly longer, like Amber Brute's 3-clawed left hand).
        for (int claw = -1; claw <= 1; claw++) {
            float yOff = claw * 0.22F;
            float rOuter = e.radius * (claw == 0 ? 1.02F : 0.92F);
            float rInner = rOuter - 0.14F;
            if (!additive) {
                drawClawStreak(m, out, curStart, curEnd, rInner, rOuter, yOff, 14,
                        e.primaryColor, 0.60F * fade);
            } else {
                drawClawStreak(m, out, curStart, curEnd, rInner + 0.035F, rOuter - 0.02F, yOff, 14,
                        e.secondaryColor, 0.42F * fade);
            }
        }
        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // 18. ROOT_TENDRILS — Chunky stepped charred-wood roots coiling up with ember tips
    // -------------------------------------------------------------------------
    private static void renderRootTendrils(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                           boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;
        int roots = Math.max(4, Math.min(e.count, 10));
        int barkRgb = darkenRgb(e.primaryColor, 0.32F);
        int emberRgb = e.primaryColor;
        int hotRgb = e.secondaryColor;

        if (inWindup) {
            float alpha = (0.28F + 0.68F * easeInQuad(wp)) * cm;
            if (!additive) {
                // Dark smoldering soil disc + root-crack perimeter.
                drawHorizontalRing(m, out, 0.055F, 0.0F, r * 0.96F, 12,
                        argb(barkRgb, 0.34F * alpha), argb(barkRgb, 0.50F * alpha));
                // Emerging chunky wooden root nubs poking through the soil before snapping shut.
                for (int i = 0; i < roots; i++) {
                    float deg = i * (360.0F / roots) + hashSigned(e.id + i) * 14.0F;
                    float pokeH = (0.18F + 0.55F * easeInQuad(wp)) * (0.85F + 0.25F * Math.abs(Mth.sin(i * 2.1F)));
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(deg));
                    poseStack.translate(r * 0.78F, 0.0F, 0.0F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(18.0F));
                    drawCharredRootSegment(poseStack.last().pose(), out, 0.22F, 0.14F, pokeH,
                            barkRgb, emberRgb, 0.88F * alpha);
                    poseStack.popPose();
                }
            } else {
                // Subtle smoldering ember fissure ring + inward closing burn line.
                drawHorizontalRing(m, out, 0.075F, r * 0.88F, r, 16,
                        argb(emberRgb, 0.55F * alpha), argb(hotRgb, 0.78F * alpha));
                float shrinkR = Math.max(0.2F, r * (1.0F - wp));
                drawHorizontalRing(m, out, 0.085F, shrinkR, shrinkR + 0.14F, 14,
                        argb(hotRgb, 0.72F * alpha), argb(emberRgb, 0.0F));
            }
            return;
        }

        // Erupting 3-segment angular charred-wood roots hooking inward like a burning briar cage!
        float rise = easeOutQuart(clamp01(ap * 4.5F));
        float retreat = ap > 0.68F ? 1.0F - easeInQuad((ap - 0.68F) / 0.32F) : 1.0F;
        float growth = rise * retreat;
        float alpha = clamp01(retreat * 1.2F) * cm;
        if (growth <= 0.02F || alpha <= 0.01F) {
            return;
        }

        for (int i = 0; i < roots; i++) {
            float deg = i * (360.0F / roots) + hashSigned(e.id + i) * 14.0F;
            float hScale = (0.82F + 0.30F * Math.abs(Mth.sin(i * 2.4F + e.id))) * growth;
            float segH = e.height * 0.36F * hScale;
            float sway = Mth.sin(age * 5.0F + i * 1.4F) * 2.5F;

            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(deg));
            poseStack.translate(r * 0.80F, 0.0F, 0.0F);
            // Segment 1: thick charred trunk angling upward & slightly outward
            poseStack.mulPose(Axis.ZP.rotationDegrees(-10.0F + sway));
            if (!additive) {
                drawCharredRootSegment(poseStack.last().pose(), out, 0.28F, 0.21F, segH,
                        barkRgb, barkRgb, 0.94F * alpha);
            }
            // Segment 2: sharp woody kink curling inward over the target
            poseStack.translate(0.0F, segH * 0.92F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(38.0F + sway * 0.6F));
            if (!additive) {
                drawCharredRootSegment(poseStack.last().pose(), out, 0.21F, 0.14F, segH * 0.95F,
                        barkRgb, emberRgb, 0.94F * alpha);
            }
            // Segment 3: burning ember tip hooked down like a charred thorn
            poseStack.translate(0.0F, segH * 0.88F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(34.0F));
            if (!additive) {
                drawCharredRootSegment(poseStack.last().pose(), out, 0.14F, 0.04F, segH * 0.78F,
                        emberRgb, hotRgb, 0.92F * alpha);
            } else {
                drawCharredRootSegment(poseStack.last().pose(), out, 0.10F, 0.02F, segH * 0.80F,
                        emberRgb, hotRgb, 0.36F * alpha);
            }
            poseStack.popPose();
        }
        if (!additive) {
            drawHorizontalRing(m, out, 0.07F, 0.0F, r * 0.95F, 12,
                    argb(barkRgb, 0.45F * alpha), argb(barkRgb, 0.06F * alpha));
        }
    }

    // -------------------------------------------------------------------------
    // 19. CINDER_ERUPTION — Rough blocky volcanic cinder-furnace jet(s) from chest core
    //     count == 1: single chest blast; count > 1: radiating 360-deg raid crossfire star
    // -------------------------------------------------------------------------
    private static void renderCinderEruption(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                             boolean inWindup, float wp, float ap, float cm, boolean additive) {
        int rays = Math.max(1, e.count);
        float len = e.length;
        float halfW = Math.max(0.55F, e.width * 0.42F);
        float floorY = -1.78F; // core is ~1.85 blocks above feet; project heated cinder lane near ground
        int sootRgb = darkenRgb(e.primaryColor, 0.28F);

        if (inWindup) {
            float alpha = (0.25F + 0.72F * easeInQuad(wp)) * cm;
            float reach = len * easeOutQuad(wp);
            Matrix4f centerM = poseStack.last().pose();
            if (!additive) {
                // Tumbling dark cinder blocks pulled inward toward the superheating chest core.
                for (int i = 0; i < 6; i++) {
                    float fp = (age * 1.4F + i * 0.166F) % 1.0F;
                    float a = i * (TAU / 6.0F) + age * 2.2F;
                    float rad = Mth.lerp(fp, 1.65F, 0.24F);
                    poseStack.pushPose();
                    poseStack.translate(Mth.cos(a) * rad, Mth.sin(a) * rad * 0.85F,
                            rays == 1 ? (0.35F + (1.0F - fp) * 0.9F) : 0.0F);
                    poseStack.mulPose(Axis.YP.rotationDegrees(age * 160.0F + i * 50.0F));
                    poseStack.mulPose(Axis.XP.rotationDegrees(age * 120.0F + i * 40.0F));
                    drawBlockyCube(poseStack.last().pose(), out, 0.14F + 0.08F * wp,
                            i % 2 == 0 ? sootRgb : e.primaryColor, 0.85F * alpha);
                    poseStack.popPose();
                }
            } else {
                float pulseR = 0.28F + 0.52F * easeInQuad(wp);
                drawBlockyCube(centerM, out, pulseR, e.secondaryColor, 0.70F * alpha);
            }

            for (int ray = 0; ray < rays; ray++) {
                float rayDeg = ray * (360.0F / rays);
                poseStack.pushPose();
                if (ray > 0) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(-rayDeg));
                }
                Matrix4f rm = poseStack.last().pose();
                float farHalfW = rays > 1 ? halfW + 0.04F * reach : halfW;
                if (!additive) {
                    // Scorched ash strip on the ground along each blast corridor.
                    quad(rm, out, -halfW, floorY, 0.5F, halfW, floorY, 0.5F,
                            farHalfW, floorY, reach, -farHalfW, floorY, reach,
                            argb(sootRgb, 0.34F * alpha), argb(sootRgb, 0.34F * alpha),
                            argb(sootRgb, 0.14F * alpha), argb(sootRgb, 0.14F * alpha));
                } else {
                    // Smoldering glowing rail lines framing each furnace corridor.
                    drawGroundStrip(rm, out, -halfW, 0.5F, -farHalfW, reach, 0.08F, floorY + 0.02F,
                            argb(e.primaryColor, 0.78F * alpha), argb(e.primaryColor, 0.28F * alpha));
                    drawGroundStrip(rm, out, halfW, 0.5F, farHalfW, reach, 0.08F, floorY + 0.02F,
                            argb(e.primaryColor, 0.78F * alpha), argb(e.primaryColor, 0.28F * alpha));
                    if (rays > 1) {
                        // Subtle glowing center seam so players clearly read every lane in a 6-ray raid star.
                        drawGroundStrip(rm, out, 0.0F, 0.5F, 0.0F, reach, 0.07F, floorY + 0.03F,
                                argb(e.secondaryColor, 0.65F * alpha), argb(e.primaryColor, 0.22F * alpha));
                    }
                }
                poseStack.popPose();
            }
            return;
        }

        // Roaring volcanic cinder jet(s): chunky square-profile furnace exhaust + tumbling glowing cinder chunks!
        float punch = easeOutCubic(clamp01(ap * 5.0F));
        float fade = (ap > 0.72F ? 1.0F - easeInQuad((ap - 0.72F) / 0.28F) : 1.0F) * cm;
        float jetLen = len * punch;
        float coreR = halfW * (rays > 1 ? 0.44F : 0.52F) * (0.85F + 0.15F * Mth.sin(age * 24.0F));
        float ashR = halfW * (rays > 1 ? 0.84F : 0.95F);
        int chunksPerRay = rays > 1 ? 5 : 12;

        for (int ray = 0; ray < rays; ray++) {
            float rayDeg = ray * (360.0F / rays);
            poseStack.pushPose();
            if (ray > 0) {
                poseStack.mulPose(Axis.YP.rotationDegrees(-rayDeg));
            }
            // Gently aim the chest furnace jet downward toward player chest height (~1.1-1.5 blocks) instead of head-high.
            poseStack.translate(0.0F, -0.22F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(1.6F));
            Matrix4f rm = poseStack.last().pose();
            if (!additive) {
                // Outer billowing volcanic smoke/ash sheath.
                drawTaperedConeZ(rm, out, 0.0F, 0.0F, 0.2F, jetLen, coreR * 0.85F, ashR * 1.15F, 6,
                        argb(sootRgb, 0.58F * fade), argb(sootRgb, 0.12F * fade));
                // Tumbling blocky charcoal & molten slag chunks blasted forward through each jet!
                for (int i = 0; i < chunksPerRay; i++) {
                    float fp = (age * 2.4F + (i + ray * 0.37F) / chunksPerRay) % 1.0F;
                    float zPos = 0.6F + fp * (jetLen - 0.6F);
                    float spread = (0.35F + 0.65F * fp) * halfW;
                    float a = i * 2.15F + ray * 1.1F + age * 4.5F;
                    poseStack.pushPose();
                    poseStack.translate(Mth.cos(a) * spread, Mth.sin(a) * spread * 0.72F, zPos);
                    poseStack.mulPose(Axis.XP.rotationDegrees(age * 240.0F + i * 45.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(age * 180.0F + i * 33.0F));
                    drawBlockyCube(poseStack.last().pose(), out, 0.21F * (1.0F - 0.35F * fp),
                            (i % 3 == 0) ? e.secondaryColor : ((i % 2 == 0) ? e.primaryColor : sootRgb), 0.88F * fade);
                    poseStack.popPose();
                }
            } else {
                drawTaperedConeZ(rm, out, 0.0F, 0.0F, 0.15F, jetLen, coreR * 0.52F, coreR * 0.90F, 6,
                        argb(e.secondaryColor, 0.78F * fade), argb(e.primaryColor, 0.10F * fade));
                drawTaperedConeZ(rm, out, 0.0F, 0.0F, 0.15F, jetLen * 0.82F, coreR * 0.25F, coreR * 0.32F, 4,
                        argb(0xFFF6D4, 0.84F * fade), argb(e.secondaryColor, 0.05F * fade));
            }
            poseStack.popPose();
        }
    }

    // -------------------------------------------------------------------------
    // 20. ROLL_TRAIL — Twin scorched bark wheel ruts + tumbling charred wood splinters
    // -------------------------------------------------------------------------
    private static void renderRollTrail(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                        boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float len = e.length;
        float trackOff = Math.max(0.55F, e.width * 0.36F);
        int sootRgb = darkenRgb(e.primaryColor, 0.30F);

        if (inWindup) {
            float alpha = (0.30F + 0.68F * easeInQuad(wp)) * cm;
            float reach = len * easeOutQuad(wp);
            if (!additive) {
                // Two parallel dark churned-earth wheel ruts framing the boulder roll lane.
                drawGroundStrip(m, out, -trackOff, 0.4F, -trackOff, reach, 0.26F, 0.055F,
                        argb(sootRgb, 0.52F * alpha), argb(sootRgb, 0.22F * alpha));
                drawGroundStrip(m, out, trackOff, 0.4F, trackOff, reach, 0.26F, 0.055F,
                        argb(sootRgb, 0.52F * alpha), argb(sootRgb, 0.22F * alpha));
            } else {
                // Glowing ember skid seams down the middle of each rut + pacing embers rolling forward.
                drawGroundStrip(m, out, -trackOff, 0.4F, -trackOff, reach, 0.07F, 0.072F,
                        argb(e.primaryColor, 0.72F * alpha), argb(e.secondaryColor, 0.32F * alpha));
                drawGroundStrip(m, out, trackOff, 0.4F, trackOff, reach, 0.07F, 0.072F,
                        argb(e.primaryColor, 0.72F * alpha), argb(e.secondaryColor, 0.32F * alpha));
                for (int i = 0; i < 4; i++) {
                    float fp = (age * 2.2F + i * 0.25F) % 1.0F;
                    float z = 0.6F + fp * (reach - 0.6F);
                    float fa = Mth.sin(fp * (float) Math.PI) * 0.85F * alpha;
                    drawGroundStrip(m, out, -trackOff * 0.9F, z, trackOff * 0.9F, z, 0.08F, 0.08F,
                            argb(e.secondaryColor, fa), argb(e.secondaryColor, fa));
                }
            }
            return;
        }

        // Active roll wake: twin smoldering ruts left behind the rolling boulder + kicking bark clods!
        float front = len * easeOutQuad(clamp01(ap * 1.18F));
        float fade = (ap > 0.72F ? 1.0F - easeInQuad((ap - 0.72F) / 0.28F) : 1.0F) * cm;
        if (!additive) {
            drawGroundStrip(m, out, -trackOff, 0.2F, -trackOff, front, 0.32F, 0.058F,
                    argb(sootRgb, 0.62F * fade), argb(sootRgb, 0.45F * fade));
            drawGroundStrip(m, out, trackOff, 0.2F, trackOff, front, 0.32F, 0.058F,
                    argb(sootRgb, 0.62F * fade), argb(sootRgb, 0.45F * fade));
            // Tumbling bark & dirt clods kicked up near the rolling front.
            for (int i = 0; i < 8; i++) {
                float localP = (age * 2.6F + i * 0.125F) % 1.0F;
                float zPos = Math.max(0.3F, front - localP * 3.8F);
                float side = ((i & 1) == 0 ? 1.0F : -1.0F) * (trackOff * 0.75F + localP * 0.85F);
                float arcY = 0.18F + (1.0F - (2.0F * localP - 1.0F) * (2.0F * localP - 1.0F)) * e.height * 0.75F;
                poseStack.pushPose();
                poseStack.translate(side, arcY, zPos);
                poseStack.mulPose(Axis.XP.rotationDegrees(localP * 320.0F + i * 40.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(localP * 210.0F));
                drawBlockyCube(poseStack.last().pose(), out, 0.17F * (1.0F - 0.4F * localP),
                        (i % 3 == 0) ? e.primaryColor : sootRgb, 0.88F * fade);
                poseStack.popPose();
            }
        } else {
            drawGroundStrip(m, out, -trackOff, 0.2F, -trackOff, front, 0.09F, 0.075F,
                    argb(e.primaryColor, 0.65F * fade), argb(e.secondaryColor, 0.78F * fade));
            drawGroundStrip(m, out, trackOff, 0.2F, trackOff, front, 0.09F, 0.075F,
                    argb(e.primaryColor, 0.65F * fade), argb(e.secondaryColor, 0.78F * fade));
        }
    }

    // -------------------------------------------------------------------------
    // 21. BURROW_MOUND — Heaving mound of blocky earth clods & smoldering cracks
    // -------------------------------------------------------------------------
    private static void renderBurrowMound(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                          boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;
        int clods = Math.max(6, Math.min(e.count, 12));
        int soilRgb = darkenRgb(e.primaryColor, 0.30F);
        int barkRgb = darkenRgb(e.primaryColor, 0.46F);

        if (inWindup) {
            float alpha = (0.35F + 0.65F * easeInQuad(wp)) * cm;
            float heave = 0.35F + 0.65F * easeInQuad(wp) + 0.12F * Mth.sin(age * 22.0F);
            if (!additive) {
                // Chunky earthen blocks heaving upward in a churning pile.
                drawHorizontalRing(m, out, 0.055F, 0.0F, r * 0.96F, 14,
                        argb(soilRgb, 0.44F * alpha), argb(soilRgb, 0.18F * alpha));
                for (int i = 0; i < clods; i++) {
                    float a = i * (TAU / clods) + hashSigned(e.id + i) * 0.35F;
                    float dist = r * (0.22F + 0.48F * Math.abs(hashSigned(e.id + i * 7)));
                    float sz = 0.28F + 0.18F * Math.abs(Mth.sin(i * 1.9F));
                    float lift = sz * 0.45F + (0.12F + 0.42F * heave) * (1.0F - dist / r);
                    poseStack.pushPose();
                    poseStack.translate(Mth.cos(a) * dist, lift, Mth.sin(a) * dist);
                    poseStack.mulPose(Axis.YP.rotationDegrees(i * 41.0F + age * 25.0F));
                    poseStack.mulPose(Axis.XP.rotationDegrees(14.0F * Mth.sin(age * 16.0F + i)));
                    drawBlockyCube(poseStack.last().pose(), out, sz, (i % 3 == 0) ? barkRgb : soilRgb, 0.92F * alpha);
                    poseStack.popPose();
                }
            } else {
                // Glowing molten fissure cracks peeking between the heaving soil blocks.
                drawHorizontalRing(m, out, 0.075F, r * 0.88F, r, 16,
                        argb(e.primaryColor, 0.60F * alpha), argb(e.secondaryColor, 0.82F * alpha));
                for (int i = 0; i < 5; i++) {
                    float a = i * (TAU / 5.0F) + 0.3F;
                    drawGroundStrip(m, out, 0.0F, 0.0F, Mth.cos(a) * r * 0.88F, Mth.sin(a) * r * 0.88F,
                            0.08F, 0.08F, argb(e.secondaryColor, 0.78F * alpha), argb(e.primaryColor, 0.15F * alpha));
                }
            }
            return;
        }

        // Eruption burst: soil & charred-wood blocks blasting radially outward in parabolic arcs!
        float fade = (1.0F - easeInQuad(ap)) * cm;
        if (!additive) {
            for (int i = 0; i < clods + 2; i++) {
                float a = i * (TAU / (clods + 2)) + hashSigned(e.id + i) * 0.25F;
                float speed = r * (0.72F + 0.40F * Math.abs(Mth.sin(i * 2.3F)));
                float dist = 0.4F + speed * easeOutCubic(ap);
                float arcY = 0.2F + Math.max(0.0F, e.height * 1.45F * ap * (1.0F - 0.92F * ap) * 3.8F);
                float sz = (0.28F + 0.14F * (i % 3)) * (1.0F - 0.45F * ap);
                poseStack.pushPose();
                poseStack.translate(Mth.cos(a) * dist, arcY, Mth.sin(a) * dist);
                poseStack.mulPose(Axis.XP.rotationDegrees(ap * 360.0F + i * 50.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(ap * 240.0F));
                drawBlockyCube(poseStack.last().pose(), out, sz, (i % 3 == 0) ? e.primaryColor : soilRgb, 0.90F * fade);
                poseStack.popPose();
            }
            drawHorizontalRing(m, out, 0.07F, r * 0.25F, r * (0.55F + 0.50F * easeOutCubic(ap)), 16,
                    argb(soilRgb, 0.45F * fade), argb(soilRgb, 0.04F * fade));
        } else {
            float curR = r * (0.30F + 0.76F * easeOutCubic(clamp01(ap * 2.2F)));
            drawHorizontalRing(m, out, 0.09F, curR * 0.78F, curR, 18,
                    argb(e.secondaryColor, 0.75F * fade), argb(e.primaryColor, 0.0F));
        }
    }

    // -------------------------------------------------------------------------
    // 22. DRUM_TREMOR — Blocky ash-and-cinder seismic ground tiles rippling outward
    // -------------------------------------------------------------------------
    private static void renderDrumTremor(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out,
                                         boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float innerR = e.width > 0.1F && e.width < e.radius * 0.9F ? e.width : 0.45F;
        float outerR = e.radius;
        int tiles = Math.max(6, Math.min(e.count, 16));
        int ringSegs = outerR > 12.0F ? 36 : 18;
        int ashRgb = darkenRgb(e.primaryColor, 0.32F);

        if (inWindup) {
            float alpha = (0.28F + 0.68F * easeInQuad(wp)) * cm;
            if (!additive) {
                drawHorizontalRing(m, out, 0.055F, innerR, outerR, ringSegs,
                        argb(ashRgb, 0.22F * alpha), argb(ashRgb, 0.38F * alpha));
            } else {
                drawHorizontalRing(m, out, 0.075F, outerR - 0.22F, outerR, ringSegs,
                        argb(e.primaryColor, 0.70F * alpha), argb(e.secondaryColor, 0.88F * alpha));
                if (innerR > 0.6F) {
                    drawHorizontalRing(m, out, 0.08F, innerR, innerR + 0.26F, ringSegs,
                            argb(0xFFF6D0, 0.92F * alpha), argb(e.primaryColor, 0.28F * alpha));
                }
                float waveR = Mth.lerp(wp, innerR, outerR);
                drawHorizontalRing(m, out, 0.085F, Math.max(innerR, waveR - 0.32F), waveR, ringSegs,
                        argb(e.secondaryColor, 0.82F * alpha), argb(e.primaryColor, 0.0F));
            }
            return;
        }

        // Expanding seismic ripple of chunky tilted earth/ash tiles popping upward then slamming back down!
        float sweep = easeOutCubic(clamp01(ap * 2.1F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float curR = Mth.lerp(sweep, Math.max(0.8F, innerR), outerR);
        float pop = Mth.sin(clamp01(ap * 1.65F) * (float) Math.PI);

        if (!additive) {
            for (int i = 0; i < tiles; i++) {
                float deg = i * (360.0F / tiles);
                float var = 0.80F + 0.30F * Math.abs(Mth.sin(i * 2.1F + e.id));
                float tileH = e.height * 0.72F * pop * var;
                if (tileH <= 0.04F) {
                    continue;
                }
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(deg));
                poseStack.translate(curR, tileH * 0.42F, 0.0F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(-24.0F * pop));
                drawBlockySlab(poseStack.last().pose(), out, 0.36F, tileH * 0.55F, 0.48F,
                        (i % 3 == 0) ? darkenRgb(e.primaryColor, 0.52F) : ashRgb, 0.90F * fade);
                poseStack.popPose();
            }
            drawHorizontalRing(m, out, 0.068F, Math.max(innerR, curR - 0.9F), curR + 0.25F, ringSegs,
                    argb(ashRgb, 0.46F * fade), argb(ashRgb, 0.04F * fade));
        } else {
            drawHorizontalRing(m, out, 0.085F, Math.max(innerR, curR - 0.45F), curR + 0.10F, ringSegs,
                    argb(e.primaryColor, 0.65F * fade), argb(e.secondaryColor, 0.80F * fade));
        }
    }

    // -------------------------------------------------------------------------
    // 23. RIB_FURNACE — Split-ribcage ember burst + orbiting/expanding cinder blocks
    // -------------------------------------------------------------------------
    private static void renderRibFurnace(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                         boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float r = e.radius;
        int blocks = Math.max(6, Math.min(e.count, 14));
        int sootRgb = darkenRgb(e.primaryColor, 0.30F);

        if (inWindup) {
            float alpha = (0.28F + 0.70F * easeInQuad(wp)) * cm;
            if (!additive) {
                drawHorizontalRing(m, out, 0.06F, 0.0F, r, 20,
                        argb(sootRgb, 0.16F * alpha), argb(e.primaryColor, 0.24F * alpha));
                // Orbiting charred-cinder blocks tightening around the ribcage as pressure builds.
                for (int i = 0; i < blocks; i++) {
                    float a = i * (TAU / blocks) + age * (2.0F + 2.5F * wp);
                    float rad = Mth.lerp(easeInQuad(wp), r * 0.68F, 1.35F);
                    float y = 1.25F + 0.85F * Mth.sin(i * 1.7F + age * 4.0F);
                    poseStack.pushPose();
                    poseStack.translate(Mth.cos(a) * rad, y, Mth.sin(a) * rad);
                    poseStack.mulPose(Axis.YP.rotationDegrees(age * 130.0F + i * 35.0F));
                    drawBlockyCube(poseStack.last().pose(), out, 0.20F,
                            (i % 2 == 0) ? e.primaryColor : sootRgb, 0.88F * alpha);
                    poseStack.popPose();
                }
            } else {
                drawHorizontalRing(m, out, 0.08F, r * 0.92F, r, 24,
                        argb(e.primaryColor, 0.70F * alpha), argb(e.secondaryColor, 0.90F * alpha));
                float shrinkR = Math.max(0.45F, r * (1.0F - wp));
                drawHorizontalRing(m, out, 0.09F, shrinkR, shrinkR + 0.20F, 22,
                        argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.0F));
            }
            return;
        }

        // Furnace blast: blocky flying molten cinders + low chunky hexagonal heatwave blast shell!
        float expand = easeOutCubic(clamp01(ap * 2.2F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        float curR = r * (0.22F + 0.82F * expand);

        if (!additive) {
            // Chunky low hexagonal furnace heat-wall (10 sides = grounded Minecraft silhouette, not smooth sci-fi orb).
            drawCylinderY(m, out, 0.1F, e.height * (0.55F + 0.45F * (1.0F - ap)), curR, curR * 1.08F, 10,
                    argb(e.primaryColor, 0.40F * fade), argb(sootRgb, 0.04F * fade));
            for (int i = 0; i < blocks; i++) {
                float a = i * (TAU / blocks) + hashSigned(e.id + i) * 0.2F;
                float dist = curR * (0.72F + 0.34F * Math.abs(Mth.sin(i * 1.9F)));
                float y = 0.65F + e.height * 0.62F * easeOutQuad(ap) * (0.55F + 0.45F * Math.abs(Mth.cos(i * 2.3F)));
                poseStack.pushPose();
                poseStack.translate(Mth.cos(a) * dist, y, Mth.sin(a) * dist);
                poseStack.mulPose(Axis.XP.rotationDegrees(ap * 300.0F + i * 40.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(ap * 220.0F + i * 60.0F));
                drawBlockyCube(poseStack.last().pose(), out, 0.26F * (1.0F - 0.35F * ap),
                        (i % 3 == 0) ? e.secondaryColor : ((i % 2 == 0) ? e.primaryColor : sootRgb), 0.90F * fade);
                poseStack.popPose();
            }
        } else {
            drawCylinderY(m, out, 0.1F, e.height * 0.58F, curR * 0.86F, curR * 0.94F, 10,
                    argb(e.secondaryColor, 0.70F * fade), argb(e.primaryColor, 0.0F));
            drawHorizontalRing(m, out, 0.10F, curR * 0.78F, curR * 1.04F, 22,
                    argb(e.secondaryColor, 0.82F * fade), argb(e.primaryColor, 0.0F));
        }
    }

    /** Chunky 4-sided tapered charred-wood root segment (blocky Minecraft silhouette, alternating bark shading). */
    private static void drawCharredRootSegment(Matrix4f m, VertexConsumer out, float baseHalfW, float topHalfW,
                                               float height, int baseRgb, int topRgb, float alpha) {
        if (height <= 0.02F) {
            return;
        }
        float[] bx = {-baseHalfW, baseHalfW, baseHalfW, -baseHalfW};
        float[] bz = {-baseHalfW, -baseHalfW, baseHalfW, baseHalfW};
        float[] tx = {-topHalfW, topHalfW, topHalfW, -topHalfW};
        float[] tz = {-topHalfW, -topHalfW, topHalfW, topHalfW};
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            float shade = (i & 1) == 0 ? 1.0F : 0.72F;
            int cBot = argb(darkenRgb(baseRgb, shade), alpha);
            int cTop = argb(darkenRgb(topRgb, 0.82F + 0.18F * shade), alpha);
            quad(m, out, bx[i], 0.0F, bz[i], bx[j], 0.0F, bz[j],
                    tx[j], height, tz[j], tx[i], height, tz[i], cBot, cBot, cTop, cTop);
        }
        int capCol = argb(topRgb, alpha);
        quad(m, out, tx[0], height, tz[0], tx[1], height, tz[1],
                tx[2], height, tz[2], tx[3], height, tz[3], capCol, capCol, capCol, capCol);
    }

    /** Chunky 6-face Minecraft cube centered at the origin with directional face shading. */
    private static void drawBlockyCube(Matrix4f m, VertexConsumer out, float halfSize, int rgb, float alpha) {
        drawBlockySlab(m, out, halfSize, halfSize, halfSize, rgb, alpha);
    }

    /** Chunky 6-face Minecraft box centered at the origin with directional top/side/bottom shading. */
    private static void drawBlockySlab(Matrix4f m, VertexConsumer out, float hx, float hy, float hz,
                                       int rgb, float alpha) {
        int top = argb(rgb, alpha);
        int sideA = argb(darkenRgb(rgb, 0.80F), alpha);
        int sideB = argb(darkenRgb(rgb, 0.64F), alpha);
        int bot = argb(darkenRgb(rgb, 0.50F), alpha);
        // Top (+Y) & Bottom (-Y)
        quad(m, out, -hx, hy, -hz, hx, hy, -hz, hx, hy, hz, -hx, hy, hz, top, top, top, top);
        quad(m, out, -hx, -hy, hz, hx, -hy, hz, hx, -hy, -hz, -hx, -hy, -hz, bot, bot, bot, bot);
        // North (-Z) & South (+Z)
        quad(m, out, -hx, -hy, -hz, hx, -hy, -hz, hx, hy, -hz, -hx, hy, -hz, sideA, sideA, sideA, sideA);
        quad(m, out, hx, -hy, hz, -hx, -hy, hz, -hx, hy, hz, hx, hy, hz, sideA, sideA, sideA, sideA);
        // West (-X) & East (+X)
        quad(m, out, -hx, -hy, hz, -hx, -hy, -hz, -hx, hy, -hz, -hx, hy, hz, sideB, sideB, sideB, sideB);
        quad(m, out, hx, -hy, -hz, hx, -hy, hz, hx, hy, hz, hx, hy, -hz, sideB, sideB, sideB, sideB);
    }

    /**
     * Chunky 3-tone faceted crystal stalagmite: dark earthy root collar at the soil line -> warm translucent amber
     * prism -> luminous golden crystal tip, with alternating facet shading for crisp vanilla-Minecraft 3D depth.
     */
    private static void drawFacetedCrystal(Matrix4f m, VertexConsumer out, float baseRadius, float height, int sides,
                                           int baseRgb, int tipRgb, float alpha) {
        if (height <= 0.02F || baseRadius <= 0.01F) {
            return;
        }
        float rootY = height * 0.18F;
        float rootR = baseRadius * 1.06F;
        float midY = height * 0.66F;
        float midR = baseRadius * 0.74F;
        for (int i = 0; i < sides; i++) {
            float a0 = i * (TAU / sides);
            float a1 = (i + 1) * (TAU / sides);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float shade = (i & 1) == 0 ? 1.0F : 0.70F;
            int colSoil = argb(darkenRgb(baseRgb, shade * 0.44F), alpha);
            int colRoot = argb(darkenRgb(baseRgb, shade * 0.76F), alpha);
            int colMid = argb(darkenRgb(baseRgb, shade), alpha);
            int colTip = argb(darkenRgb(tipRgb, 0.85F + 0.15F * shade), alpha * 0.94F);

            // Dark earthy root collar emerging from soil
            quad(m, out,
                    c0 * rootR, 0.0F, s0 * rootR,
                    c1 * rootR, 0.0F, s1 * rootR,
                    c1 * baseRadius, rootY, s1 * baseRadius,
                    c0 * baseRadius, rootY, s0 * baseRadius,
                    colSoil, colSoil, colRoot, colRoot);
            // Main faceted amber prism body
            quad(m, out,
                    c0 * baseRadius, rootY, s0 * baseRadius,
                    c1 * baseRadius, rootY, s1 * baseRadius,
                    c1 * midR, midY, s1 * midR,
                    c0 * midR, midY, s0 * midR,
                    colRoot, colRoot, colMid, colMid);
            // Tapered golden crystal tip
            quad(m, out,
                    c0 * midR, midY, s0 * midR,
                    c1 * midR, midY, s1 * midR,
                    0.0F, height, 0.0F,
                    0.0F, height, 0.0F,
                    colMid, colMid, colTip, colTip);
        }
    }

    /** Compact faceted double-pointed octahedral crystal shard (for airborne shard sprays). */
    private static void drawOctaShard(Matrix4f m, VertexConsumer out, float radius, float halfLen,
                                      int primaryRgb, int tipRgb, float alpha) {
        int sides = 4;
        for (int i = 0; i < sides; i++) {
            float a0 = i * (TAU / sides);
            float a1 = (i + 1) * (TAU / sides);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float shade = (i & 1) == 0 ? 1.0F : 0.74F;
            int midCol = argb(darkenRgb(primaryRgb, shade), alpha);
            int tipCol = argb(darkenRgb(tipRgb, 0.85F + 0.15F * shade), alpha);
            quad(m, out, c0 * radius, 0.0F, s0 * radius, c1 * radius, 0.0F, s1 * radius,
                    0.0F, halfLen, 0.0F, 0.0F, halfLen, 0.0F, midCol, midCol, tipCol, tipCol);
            quad(m, out, c1 * radius, 0.0F, s1 * radius, c0 * radius, 0.0F, s0 * radius,
                    0.0F, -halfLen * 0.75F, 0.0F, 0.0F, -halfLen * 0.75F, 0.0F, midCol, midCol, midCol, midCol);
        }
    }

    /** Lean, sharp curved scratch streak tapering to needle tips at both ends. */
    private static void drawClawStreak(Matrix4f m, VertexConsumer out, float startDeg, float endDeg,
                                       float innerR, float outerR, float yOff, int segs, int rgb, float alpha) {
        float midR = (innerR + outerR) * 0.5F;
        float halfW = (outerR - innerR) * 0.5F;
        for (int i = 0; i < segs; i++) {
            float f0 = i / (float) segs;
            float f1 = (i + 1) / (float) segs;
            float a0 = Mth.lerp(f0, startDeg, endDeg) * DEG_TO_RAD;
            float a1 = Mth.lerp(f1, startDeg, endDeg) * DEG_TO_RAD;
            float env0 = Mth.sin(f0 * (float) Math.PI);
            float env1 = Mth.sin(f1 * (float) Math.PI);
            float fx0 = -Mth.sin(a0);
            float fz0 = Mth.cos(a0);
            float fx1 = -Mth.sin(a1);
            float fz1 = Mth.cos(a1);
            float in0 = midR - halfW * env0;
            float out0 = midR + halfW * env0;
            float in1 = midR - halfW * env1;
            float out1 = midR + halfW * env1;
            int c0 = argb(rgb, alpha * env0);
            int c1 = argb(rgb, alpha * env1);
            quad(m, out,
                    fx0 * in0, yOff, fz0 * in0,
                    fx1 * in1, yOff, fz1 * in1,
                    fx1 * out1, yOff, fz1 * out1,
                    fx0 * out0, yOff, fz0 * out0,
                    c0, c1, c1, c0);
        }
    }

    // -------------------------------------------------------------------------
    // 24. MADNESS_GRASP — Midra's closing frenzied grab cone & constricting tendrils
    // -------------------------------------------------------------------------
    private static void renderMadnessGrasp(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                           boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        float halfDeg = e.angle * 0.5F;
        int fingers = Math.max(3, Math.min(e.count, 7));

        if (inWindup) {
            float alpha = (0.24F + 0.56F * easeInQuad(wp)) * cm;
            float reach = e.radius * (0.35F + 0.65F * easeOutQuad(wp));
            if (!additive) {
                drawArcRibbon(m, out, -halfDeg, halfDeg, 0.45F, reach, 0.08F, 0.08F, 14,
                        argb(e.primaryColor, 0.08F * alpha), argb(e.primaryColor, 0.34F * alpha));
            } else {
                drawArcRibbon(m, out, -halfDeg, halfDeg, Math.max(0.45F, reach - 0.25F), reach, 0.11F, 0.11F, 14,
                        argb(e.secondaryColor, 0.15F * alpha), argb(e.secondaryColor, 0.85F * alpha));
                // Reaching curved tendrils closing inward from the cone flanks.
                float pinch = Mth.lerp(easeInQuad(wp), 1.0F, 0.38F);
                for (int i = 0; i < fingers; i++) {
                    float frac = (i / (float) (fingers - 1)) * 2.0F - 1.0F;
                    float baseAng = frac * halfDeg * pinch * DEG_TO_RAD;
                    float bx = -Mth.sin(baseAng);
                    float bz = Mth.cos(baseAng);
                    float tipY = e.height * (0.45F + 0.25F * (1.0F - Math.abs(frac)));
                    float midR = reach * 0.55F;
                    drawRibbonSegment(m, out,
                            bx * 0.8F, 0.35F, bz * 0.8F,
                            bx * midR, tipY * 0.85F, bz * midR,
                            0.09F, argb(e.primaryColor, 0.55F * alpha), argb(e.secondaryColor, 0.85F * alpha));
                    drawRibbonSegment(m, out,
                            bx * midR, tipY * 0.85F, bz * midR,
                            bx * reach * 0.86F, tipY, bz * reach * 0.86F,
                            0.06F, argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.35F * alpha));
                }
            }
            return;
        }

        // Active hold cage: constricting tilted golden madness rings around Midra's hold anchor.
        float fade = (ap < 0.78F) ? 1.0F : (1.0F - (ap - 0.78F) / 0.22F);
        float alpha = easeOutQuad(Math.min(1.0F, ap * 5.0F)) * fade * cm;
        if (alpha <= 0.01F) {
            return;
        }
        float holdZ = Math.min(2.35F, e.radius * 0.54F);
        float holdY = e.height * (0.68F + 0.20F * ap);
        float cageR = 0.82F + 0.12F * Mth.sin(age * 16.0F);

        poseStack.pushPose();
        poseStack.translate(0.0F, holdY, holdZ);
        if (!additive) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 120.0F));
            drawCylinderY(poseStack.last().pose(), out, -0.75F, 0.75F, cageR, cageR * 0.86F, 12,
                    argb(e.primaryColor, 0.16F * alpha), argb(e.primaryColor, 0.32F * alpha));
            poseStack.popPose();
        } else {
            for (int r = 0; r < 3; r++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(age * (150.0F + r * 45.0F) * (r % 2 == 0 ? 1.0F : -1.0F)));
                poseStack.mulPose(Axis.XP.rotationDegrees(26.0F + r * 34.0F));
                drawHorizontalRing(poseStack.last().pose(), out, 0.0F, cageR * 0.86F, cageR * 1.04F, 16,
                        argb(e.secondaryColor, 0.82F * alpha), argb(e.primaryColor, 0.18F * alpha));
                poseStack.popPose();
            }
            // Connecting sear tendrils from boss hand/eye toward held target.
            Matrix4f bm = poseStack.last().pose();
            for (int i = 0; i < 4; i++) {
                float ang = i * (TAU / 4.0F) + age * 3.5F;
                float rx = Mth.cos(ang) * 0.55F;
                float ry = Mth.sin(ang) * 0.55F;
                drawJaggedBolt(bm, out, 0.0F, 0.25F, -holdZ * 0.75F, rx, ry, 0.0F, 4, 0.16F,
                        e.id + i * 19 + (int) (age * 14.0F), 0.045F,
                        argb(e.secondaryColor, 0.88F * alpha), argb(e.primaryColor, 0.55F * alpha));
            }
        }
        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // 25. SPEAR_ARC — Sleek storm-steel & sunlight-lightning Dragonslayer Swordspear crescent
    // -------------------------------------------------------------------------
    private static void renderSpearArc(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                       boolean inWindup, float wp, float ap, float cm, boolean additive) {
        float halfDeg = e.angle * 0.5F;
        float startDeg = e.reverse ? halfDeg : -halfDeg;
        float endDeg = e.reverse ? -halfDeg : halfDeg;
        boolean steep = Math.abs(e.tilt) > 35.0F;
        Matrix4f groundM = poseStack.last().pose();

        if (inWindup) {
            float alpha = (0.18F + 0.50F * easeInQuad(wp)) * cm;
            if (steep) {
                float halfW = Math.max(0.65F, e.width * 0.5F);
                float len = Math.max(e.length, e.radius);
                if (!additive) {
                    int edgeCol = argb(e.primaryColor, 0.05F * alpha);
                    int midCol = argb(e.primaryColor, 0.22F * alpha);
                    quad(groundM, out, -halfW, 0.07F, 0.0F, 0.0F, 0.07F, 0.0F, 0.0F, 0.07F, len, -halfW, 0.07F, len,
                            edgeCol, midCol, midCol, edgeCol);
                    quad(groundM, out, 0.0F, 0.07F, 0.0F, halfW, 0.07F, 0.0F, halfW, 0.07F, len, 0.0F, 0.07F, len,
                            midCol, edgeCol, edgeCol, midCol);
                } else {
                    float tip = len * easeInQuad(wp);
                    drawGroundStrip(groundM, out, 0.0F, 0.0F, 0.0F, tip, 0.08F, 0.11F,
                            argb(e.secondaryColor, 0.45F * alpha), argb(e.secondaryColor, 0.92F * alpha));
                    drawJaggedBolt(groundM, out, 0.0F, 0.14F, 0.2F, 0.0F, 0.14F, tip, 5, 0.22F,
                            e.id + (int) (age * 16.0F), 0.045F,
                            argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.40F * alpha));
                }
            } else {
                float reach = e.radius * (0.45F + 0.55F * easeOutQuad(wp));
                if (!additive) {
                    drawArcRibbon(groundM, out, -halfDeg, halfDeg, 0.85F, reach, 0.08F, 0.08F, 14,
                            argb(e.primaryColor, 0.04F * alpha), argb(e.primaryColor, 0.24F * alpha));
                } else {
                    drawArcRibbon(groundM, out, -halfDeg, halfDeg, Math.max(0.85F, reach - 0.18F), reach, 0.11F, 0.11F, 14,
                            argb(e.secondaryColor, 0.14F * alpha), argb(e.secondaryColor, 0.78F * alpha));
                }
            }
            return;
        }

        // Crisp narrow spear blade crescent + crackling lightning filament along outer rim.
        float sweep = easeOutCubic(Math.min(1.0F, ap * 2.5F));
        float tail = easeInQuad(clamp01((ap - 0.26F) / 0.74F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        if (fade <= 0.01F || sweep <= tail + 0.02F) {
            return;
        }

        float curStart = Mth.lerp(tail, startDeg, endDeg);
        float curEnd = Mth.lerp(sweep, startDeg, endDeg);

        poseStack.pushPose();
        poseStack.translate(0.0F, e.height, 0.0F);
        if (e.tilt != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(e.tilt));
        }
        Matrix4f m = poseStack.last().pose();
        if (!additive) {
            drawClawStreak(m, out, curStart, curEnd, e.radius * 0.62F, e.radius * 1.02F, 0.0F, 16,
                    e.primaryColor, 0.52F * fade);
        } else {
            drawClawStreak(m, out, curStart, curEnd, e.radius * 0.86F, e.radius * 1.04F, 0.02F, 16,
                    e.secondaryColor, 0.94F * fade);
            // Crackling lightning fork hugging the spear tip arc.
            int segs = 5;
            for (int i = 0; i < segs; i++) {
                float f0 = i / (float) segs;
                float f1 = (i + 1) / (float) segs;
                float a0 = Mth.lerp(f0, curStart, curEnd) * DEG_TO_RAD;
                float a1 = Mth.lerp(f1, curStart, curEnd) * DEG_TO_RAD;
                float r0 = e.radius * (0.90F + 0.09F * hashSigned(e.id + i * 11 + (int) (age * 20.0F)));
                float r1 = e.radius * (0.90F + 0.09F * hashSigned(e.id + (i + 1) * 11 + (int) (age * 20.0F)));
                drawRibbonSegment(m, out,
                        -Mth.sin(a0) * r0, 0.08F * hashSigned(e.id + i * 7), Mth.cos(a0) * r0,
                        -Mth.sin(a1) * r1, 0.08F * hashSigned(e.id + (i + 1) * 7), Mth.cos(a1) * r1,
                        0.055F, argb(e.secondaryColor, 0.92F * fade), argb(e.primaryColor, 0.65F * fade));
            }
        }
        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // 26. LIGHTNING_BOLT — Storm-cloud ring + jagged golden-white sky thunderbolt
    // -------------------------------------------------------------------------
    private static void renderLightningBolt(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                            boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        int cloudSlate = darkenRgb(e.primaryColor, 0.36F);
        float skyY = Math.min(e.height, 14.5F);

        if (inWindup) {
            float alpha = (0.22F + 0.60F * easeInQuad(wp)) * cm;
            float ringR = e.radius * (0.45F + 0.55F * easeOutQuad(wp));
            float innerR = e.radius * (1.0F - 0.72F * easeInQuad(wp));
            if (!additive) {
                // Dark overhead storm cloud disc + ground impact warning band.
                drawHorizontalRing(m, out, skyY, 0.15F, e.radius * 1.15F, 12,
                        argb(cloudSlate, 0.52F * alpha), argb(cloudSlate, 0.06F * alpha));
                drawHorizontalRing(m, out, 0.08F, 0.15F, ringR, 14,
                        argb(e.primaryColor, 0.08F * alpha), argb(e.primaryColor, 0.28F * alpha));
            } else {
                drawHorizontalRing(m, out, 0.11F, Math.max(0.1F, ringR - 0.20F), ringR, 16,
                        argb(e.secondaryColor, 0.18F * alpha), argb(e.secondaryColor, 0.88F * alpha));
                drawHorizontalRing(m, out, 0.12F, Math.max(0.05F, innerR - 0.14F), innerR + 0.14F, 14,
                        argb(e.secondaryColor, 0.82F * alpha), argb(e.primaryColor, 0.20F * alpha));
                // Pre-strike pilot leader flicker descending from the cloud.
                if (wp > 0.45F) {
                    float leaderLen = skyY * ((wp - 0.45F) / 0.55F);
                    drawJaggedBolt(m, out, 0.0F, skyY, 0.0F, 0.0F, skyY - leaderLen, 0.0F, 6, 0.45F,
                            e.id + (int) (age * 24.0F), 0.055F,
                            argb(e.secondaryColor, 0.72F * alpha), argb(e.primaryColor, 0.25F * alpha));
                }
            }
            return;
        }

        float flash = (ap < 0.20F) ? 1.0F : (1.0F - easeOutQuad((ap - 0.20F) / 0.80F));
        float alpha = flash * cm;
        if (alpha <= 0.01F) {
            return;
        }

        int seed = e.id + (int) (age * 28.0F);
        int forks = Math.max(2, Math.min(e.count, 5));
        if (!additive) {
            drawHorizontalRing(m, out, skyY, 0.2F, e.radius * 1.25F, 12,
                    argb(cloudSlate, 0.42F * alpha), argb(cloudSlate, 0.02F * alpha));
            drawHorizontalRing(m, out, 0.09F, 0.2F, e.radius * (0.75F + 0.35F * ap), 14,
                    argb(e.primaryColor, 0.28F * alpha), argb(e.primaryColor, 0.02F * alpha));
        } else {
            // Main blinding sunlight-lightning trunk.
            drawJaggedBolt(m, out, 0.0F, skyY, 0.0F, 0.0F, 0.08F, 0.0F, 9, 0.65F, seed,
                    0.26F * (0.55F + 0.45F * flash),
                    argb(0xFFFDF0, 0.98F * alpha), argb(e.secondaryColor, 0.92F * alpha));
            drawJaggedBolt(m, out, 0.0F, skyY, 0.0F, 0.0F, 0.08F, 0.0F, 9, 0.72F, seed + 5,
                    0.46F * flash,
                    argb(e.primaryColor, 0.45F * alpha), argb(e.secondaryColor, 0.32F * alpha));
            // Branching forks snapping out to the ground perimeter.
            for (int f = 0; f < forks; f++) {
                float ang = f * (TAU / forks) + hashSigned(e.id + f * 17) * 0.5F;
                float startY = skyY * (0.35F + 0.35F * (f / (float) forks));
                float gx = Mth.cos(ang) * e.radius * 0.92F;
                float gz = Mth.sin(ang) * e.radius * 0.92F;
                drawJaggedBolt(m, out, 0.0F, startY, 0.0F, gx, 0.12F, gz, 5, 0.42F, seed + f * 31,
                        0.09F * flash,
                        argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.40F * alpha));
            }
            drawStarburst(m, out, 0.0F, 0.45F, 0.0F, e.radius * 0.55F * (0.65F + 0.35F * flash), age * 5.0F,
                    argb(0xFFFDF0, 0.90F * alpha), argb(e.secondaryColor, 0.0F));
            drawHorizontalRing(m, out, 0.13F, e.radius * 0.72F, e.radius * (0.95F + 0.15F * ap), 18,
                    argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.08F * alpha));
        }
    }

    // -------------------------------------------------------------------------
    // 27. STORM_GALE — Layered pale-cyan wind-shear crescents (cone) or 360-deg radiating storm lanes (rays)
    // -------------------------------------------------------------------------
    private static void renderStormGale(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                        boolean inWindup, float wp, float ap, float cm, boolean additive) {
        if (e.angle >= 359.0F) {
            renderStormGaleRays(e, poseStack, out, age, inWindup, wp, ap, cm, additive);
            return;
        }
        Matrix4f m = poseStack.last().pose();
        float halfDeg = e.angle * 0.5F;
        int mistColor = darkenRgb(e.primaryColor, 0.65F);

        if (inWindup) {
            float alpha = (0.18F + 0.48F * easeInQuad(wp)) * cm;
            float reach = e.length * (0.30F + 0.70F * easeOutQuad(wp));
            if (!additive) {
                drawArcRibbon(m, out, -halfDeg, halfDeg, 0.6F, reach, 0.08F, 0.08F, 14,
                        argb(mistColor, 0.05F * alpha), argb(e.primaryColor, 0.24F * alpha));
            } else {
                drawArcRibbon(m, out, -halfDeg, halfDeg, Math.max(0.6F, reach - 0.25F), reach, 0.11F, 0.11F, 14,
                        argb(e.primaryColor, 0.12F * alpha), argb(e.secondaryColor, 0.75F * alpha));
            }
            return;
        }

        float fade = (1.0F - easeInQuad(ap)) * cm;
        if (fade <= 0.01F) {
            return;
        }
        int waves = Math.max(3, Math.min(e.count, 6));
        for (int w = 0; w < waves; w++) {
            float lag = w * 0.13F;
            float p = clamp01((ap - lag) / Math.max(0.25F, 1.0F - lag));
            if (p <= 0.01F || p >= 0.99F) {
                continue;
            }
            float dist = Mth.lerp(easeOutQuad(p), 1.2F, e.length);
            float wAlpha = Mth.sin(p * (float) Math.PI) * fade;
            float bandY = 0.45F + (w % 3) * (e.height * 0.34F);
            float span = halfDeg * (0.72F + 0.28F * (w % 2));
            if (!additive) {
                // Translucent layered storm-wind blade sheet.
                drawArcRibbon(m, out, -span, span, Math.max(0.4F, dist - 1.55F), dist,
                        bandY - 0.25F, bandY + 0.25F, 16,
                        argb(mistColor, 0.04F * wAlpha), argb(e.primaryColor, 0.46F * wAlpha));
            } else {
                // Crisp sunlit-cyan leading wind crest.
                drawClawStreak(m, out, -span, span, Math.max(0.4F, dist - 0.45F), dist + 0.12F,
                        bandY + 0.12F, 16, e.secondaryColor, 0.78F * wAlpha);
            }
        }
        // Horizontal streaming wind streaks along the cone.
        if (additive) {
            for (int s = 0; s < 5; s++) {
                float frac = (s / 4.0F) * 2.0F - 1.0F;
                float ang = frac * halfDeg * 0.85F * DEG_TO_RAD;
                float fx = -Mth.sin(ang);
                float fz = Mth.cos(ang);
                float d0 = e.length * clamp01(ap * 1.15F - 0.35F);
                float d1 = e.length * clamp01(ap * 1.35F);
                float sy = 0.7F + (s % 3) * 0.65F + 0.15F * Mth.sin(age * 12.0F + s);
                drawRibbonSegment(m, out, fx * d0, sy, fz * d0, fx * d1, sy + 0.25F, fz * d1,
                        0.06F, argb(e.primaryColor, 0.10F * fade), argb(e.secondaryColor, 0.75F * fade));
            }
        }
    }

    /** Multi-ray 360-degree radiating storm-gale lanes for Shape.Rays global attacks (e.g., Wrath of the Firstborn). */
    private static void renderStormGaleRays(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                            boolean inWindup, float wp, float ap, float cm, boolean additive) {
        int rays = Math.max(2, e.count);
        float len = e.length;
        float baseHalfW = Math.max(0.45F, e.width * 0.5F);
        int mistColor = darkenRgb(e.primaryColor, 0.55F);

        for (int i = 0; i < rays; i++) {
            float rayDeg = i * (360.0F / rays);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-rayDeg));
            Matrix4f rm = poseStack.last().pose();

            if (inWindup) {
                float reach = len * (0.20F + 0.80F * easeOutQuad(wp));
                float alpha = (0.24F + 0.72F * easeInQuad(wp)) * cm;
                float farHalfW = baseHalfW + 0.07F * reach;
                if (!additive) {
                    int c0 = argb(mistColor, 0.30F * alpha);
                    int c1 = argb(e.primaryColor, 0.22F * alpha);
                    quad(rm, out, -baseHalfW, 0.06F, 0.5F, baseHalfW, 0.06F, 0.5F,
                            farHalfW, 0.06F, reach, -farHalfW, 0.06F, reach, c0, c0, c1, c1);
                } else {
                    int rim = argb(e.primaryColor, 0.78F * alpha);
                    int core = argb(e.secondaryColor, 0.92F * alpha);
                    float centerW = 0.12F + 0.12F * wp;
                    quad(rm, out, -centerW, 0.08F, 0.5F, centerW, 0.08F, 0.5F,
                            centerW * 0.8F, 0.08F, reach, -centerW * 0.8F, 0.08F, reach, core, core, rim, rim);
                    quad(rm, out, -baseHalfW, 0.07F, 0.5F, -baseHalfW + 0.14F, 0.07F, 0.5F,
                            -farHalfW + 0.14F, 0.07F, reach, -farHalfW, 0.07F, reach, rim, rim, rim, rim);
                    quad(rm, out, baseHalfW - 0.14F, 0.07F, 0.5F, baseHalfW, 0.07F, 0.5F,
                            farHalfW, 0.07F, reach, farHalfW - 0.14F, 0.07F, reach, rim, rim, rim, rim);
                }
            } else {
                float fade = (1.0F - easeInQuad(ap)) * cm;
                if (fade > 0.01F) {
                    float wallH = e.height * easeOutCubic(clamp01(ap * 3.0F));
                    float farHalfW = baseHalfW + 0.07F * len;
                    if (!additive) {
                        int bot = argb(e.primaryColor, 0.45F * fade);
                        int top = argb(mistColor, 0.02F * fade);
                        quad(rm, out, -baseHalfW * 0.6F, 0.06F, 0.5F, -farHalfW * 0.6F, 0.06F, len,
                                -farHalfW * 0.25F, wallH, len, -baseHalfW * 0.25F, wallH, 0.5F, bot, bot, top, top);
                        quad(rm, out, baseHalfW * 0.6F, 0.06F, 0.5F, farHalfW * 0.6F, 0.06F, len,
                                farHalfW * 0.25F, wallH, len, baseHalfW * 0.25F, wallH, 0.5F, bot, bot, top, top);
                    } else {
                        int seed = e.id + i * 31 + (int) (age * 24.0F);
                        drawJaggedBolt(rm, out, 0.0F, 1.15F, 0.6F, 0.0F, 0.65F, len, 7, 0.42F, seed,
                                0.16F * fade, argb(0xFFFDF0, 0.96F * fade), argb(e.secondaryColor, 0.68F * fade));
                        int floorCol = argb(e.secondaryColor, 0.65F * fade);
                        quad(rm, out, -baseHalfW * 0.7F, 0.08F, 0.5F, baseHalfW * 0.7F, 0.08F, 0.5F,
                                farHalfW * 0.7F, 0.08F, len, -farHalfW * 0.7F, 0.08F, len,
                                floorCol, floorCol, argb(e.primaryColor, 0.22F * fade), argb(e.primaryColor, 0.22F * fade));
                    }
                }
            }
            poseStack.popPose();
        }
    }

    // -------------------------------------------------------------------------
    // 28. TEMPEST_CYCLONE — Spiraling storm-cloud funnel + orbiting golden static rings
    // -------------------------------------------------------------------------
    private static void renderTempestCyclone(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                             boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        int cloudSlate = darkenRgb(e.primaryColor, 0.45F);

        if (inWindup) {
            float alpha = (0.20F + 0.55F * easeInQuad(wp)) * cm;
            float r = e.radius * (0.45F + 0.55F * easeOutQuad(wp));
            if (!additive) {
                drawHorizontalRing(m, out, 0.09F, 1.0F, r, 20,
                        argb(cloudSlate, 0.06F * alpha), argb(e.primaryColor, 0.26F * alpha));
            } else {
                drawHorizontalRing(m, out, 0.12F, Math.max(0.8F, r - 0.22F), r, 20,
                        argb(e.primaryColor, 0.15F * alpha), argb(e.secondaryColor, 0.80F * alpha));
            }
            return;
        }

        float fade = (ap < 0.78F) ? easeOutQuad(Math.min(1.0F, ap * 5.0F))
                : (1.0F - (ap - 0.78F) / 0.22F);
        float alpha = fade * cm;
        if (alpha <= 0.01F) {
            return;
        }
        float dir = e.reverse ? -1.0F : 1.0F;
        int tiers = Math.max(4, Math.min(e.count, 7));

        for (int i = 0; i < tiers; i++) {
            float frac = i / (float) (tiers - 1);
            float y = 0.35F + frac * e.height;
            float tierR = e.radius * (0.58F + 0.48F * frac);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(dir * (age * (340.0F + i * 35.0F) + i * 52.0F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(5.0F * (i % 2 == 0 ? 1.0F : -1.0F)));
            Matrix4f rm = poseStack.last().pose();
            if (!additive) {
                drawArcRibbon(rm, out, -115.0F, 115.0F, tierR * 0.76F, tierR, y - 0.22F, y + 0.28F, 16,
                        argb(cloudSlate, 0.10F * alpha), argb(e.primaryColor, 0.42F * alpha));
            } else if (i % 2 == 0) {
                drawClawStreak(rm, out, -85.0F, 85.0F, tierR * 0.90F, tierR * 1.06F, y + 0.08F, 14,
                        e.secondaryColor, 0.82F * alpha);
            }
            poseStack.popPose();
        }
        // Subtle cyclone floor ring showing pull radius.
        if (additive) {
            drawHorizontalRing(m, out, 0.11F, e.radius * 0.88F, e.radius, 22,
                    argb(e.primaryColor, 0.12F * alpha), argb(e.secondaryColor, 0.65F * alpha));
        }
    }

    // -------------------------------------------------------------------------
    // 29. THUNDER_SPEAR_DIVE — Skyward storm halo + plunging lightning lance + impact forks
    // -------------------------------------------------------------------------
    private static void renderThunderSpearDive(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                               boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        int cloudSlate = darkenRgb(e.primaryColor, 0.40F);
        float halfW = Math.max(0.8F, e.width * 0.5F);
        float skyY = Math.max(3.5F, e.height);

        if (inWindup) {
            float alpha = (0.22F + 0.58F * easeInQuad(wp)) * cm;
            float reach = e.length * (0.25F + 0.75F * easeOutQuad(wp));
            if (!additive) {
                // Storm cloud halo above launch point + dive corridor shadow on the ground.
                drawHorizontalRing(m, out, skyY, 0.4F, 3.2F, 14,
                        argb(cloudSlate, 0.48F * alpha), argb(cloudSlate, 0.04F * alpha));
                int edgeCol = argb(e.primaryColor, 0.05F * alpha);
                int midCol = argb(e.primaryColor, 0.24F * alpha);
                quad(m, out, -halfW, 0.08F, 0.0F, 0.0F, 0.08F, 0.0F, 0.0F, 0.08F, reach, -halfW, 0.08F, reach,
                        edgeCol, midCol, midCol, edgeCol);
                quad(m, out, 0.0F, 0.08F, 0.0F, halfW, 0.08F, 0.0F, halfW, 0.08F, reach, 0.0F, 0.08F, reach,
                        midCol, edgeCol, edgeCol, midCol);
            } else {
                // Diagonal aiming lightning sight-line from the storm cloud to the landing spot.
                drawJaggedBolt(m, out, 0.0F, skyY, 0.0F, 0.0F, 0.14F, reach, 7, 0.30F,
                        e.id + (int) (age * 20.0F), 0.07F,
                        argb(e.secondaryColor, 0.85F * alpha), argb(e.primaryColor, 0.35F * alpha));
                poseStack.pushPose();
                poseStack.translate(0.0F, 0.12F, reach);
                drawHorizontalRing(poseStack.last().pose(), out, 0.0F, halfW * 0.75F, halfW * 1.15F, 16,
                        argb(e.secondaryColor, 0.20F * alpha), argb(e.secondaryColor, 0.88F * alpha));
                poseStack.popPose();
            }
            return;
        }

        float fade = (1.0F - easeInQuad(ap)) * cm;
        if (fade <= 0.01F) {
            return;
        }
        float headZ = e.length * Math.min(1.0F, easeOutCubic(ap * 1.85F));
        float tailZ = Math.max(0.0F, headZ - 5.5F);
        float headY = Mth.lerp(headZ / Math.max(0.1F, e.length), skyY, 0.9F);
        float tailY = Mth.lerp(tailZ / Math.max(0.1F, e.length), skyY, 0.9F);
        if (!additive) {
            drawGroundStrip(m, out, 0.0F, 0.0F, 0.0F, headZ, halfW * 0.85F, 0.09F,
                    argb(e.primaryColor, 0.06F * fade), argb(e.primaryColor, 0.34F * fade));
        } else {
            drawJaggedBolt(m, out, 0.0F, tailY, tailZ, 0.0F, headY, headZ, 6, 0.35F,
                    e.id + (int) (age * 26.0F), 0.20F * fade,
                    argb(0xFFFDF0, 0.95F * fade), argb(e.secondaryColor, 0.85F * fade));
            // Ground impact lightning forks once the dive reaches the target end.
            if (ap > 0.32F) {
                float burst = 1.0F - clamp01((ap - 0.32F) / 0.68F);
                int forks = Math.max(4, Math.min(e.count, 6));
                for (int f = 0; f < forks; f++) {
                    float ang = f * (TAU / forks) + 0.3F;
                    float r = e.radius * (0.45F + 0.55F * (1.0F - burst));
                    drawJaggedBolt(m, out, 0.0F, 0.18F, e.length,
                            Mth.cos(ang) * r, 0.18F, e.length + Mth.sin(ang) * r,
                            4, 0.25F, e.id + f * 23, 0.08F * burst,
                            argb(e.secondaryColor, 0.90F * burst * cm), argb(e.primaryColor, 0.25F * burst * cm));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 30. STORM_NOVA — Updraft storm-mist gathering shell -> radial golden lightning & cyan gale ring
    // -------------------------------------------------------------------------
    private static void renderStormNova(SpellVfxInstance e, PoseStack poseStack, VertexConsumer out, float age,
                                        boolean inWindup, float wp, float ap, float cm, boolean additive) {
        Matrix4f m = poseStack.last().pose();
        int cloudSlate = darkenRgb(e.primaryColor, 0.42F);
        float innerR = e.width > 0.1F && e.width < e.radius * 0.9F ? e.width : 0.4F;
        int ringSegs = e.radius > 12.0F ? 36 : 24;

        if (inWindup) {
            float alpha = (0.22F + 0.56F * easeInQuad(wp)) * cm;
            float r = Mth.lerp(easeOutQuad(wp), Math.max(innerR + 0.2F, e.radius * 0.38F), e.radius);
            float riseY = 0.35F + 2.2F * wp;
            if (!additive) {
                drawHorizontalRing(m, out, 0.09F, innerR, r, ringSegs,
                        argb(cloudSlate, 0.08F * alpha), argb(e.primaryColor, 0.26F * alpha));
                if (innerR <= 0.6F) {
                    // Rising updraft mist shell for full-circle storm novas.
                    drawCylinderY(m, out, 0.15F, riseY, e.radius * 0.72F, e.radius * (0.55F - 0.25F * wp), 16,
                            argb(e.primaryColor, 0.24F * alpha), argb(e.primaryColor, 0.02F * alpha));
                }
            } else {
                drawHorizontalRing(m, out, 0.12F, Math.max(innerR, r - 0.24F), r, ringSegs,
                        argb(e.primaryColor, 0.16F * alpha), argb(e.secondaryColor, 0.85F * alpha));
                if (innerR > 0.6F) {
                    drawHorizontalRing(m, out, 0.12F, innerR, innerR + 0.24F, ringSegs,
                            argb(0xFFFDF0, 0.90F * alpha), argb(e.primaryColor, 0.20F * alpha));
                }
                // Upward spiraling wind ribbons telegraphing the updraft lift.
                for (int i = 0; i < 4; i++) {
                    float a0 = i * (TAU / 4.0F) + age * 4.0F;
                    float a1 = a0 + 0.9F;
                    float rr = innerR > 0.6F ? Mth.lerp(0.5F, innerR, e.radius) : e.radius * (0.68F - 0.25F * wp);
                    drawRibbonSegment(m, out,
                            Mth.cos(a0) * rr, 0.2F, Mth.sin(a0) * rr,
                            Mth.cos(a1) * rr * 0.86F, riseY, Mth.sin(a1) * rr * 0.86F,
                            0.08F, argb(e.primaryColor, 0.35F * alpha), argb(e.secondaryColor, 0.80F * alpha));
                }
            }
            return;
        }

        float expand = easeOutCubic(Math.min(1.0F, ap * 1.65F));
        float fade = (1.0F - easeInQuad(ap)) * cm;
        if (fade <= 0.01F) {
            return;
        }
        float curR = Mth.lerp(expand, Math.max(1.0F, innerR), e.radius);
        int rays = Math.max(6, Math.min(e.count, 10));

        if (!additive) {
            // Clean translucent sky-cyan storm shockwave wall.
            drawCylinderY(m, out, 0.12F, e.height * (0.45F + 0.55F * (1.0F - ap)),
                    curR * 0.92F, curR, ringSegs,
                    argb(e.primaryColor, 0.44F * fade), argb(cloudSlate, 0.02F * fade));
            drawHorizontalRing(m, out, 0.10F, Math.max(innerR, curR * 0.65F), curR, ringSegs,
                    argb(e.primaryColor, 0.05F * fade), argb(e.primaryColor, 0.34F * fade));
        } else {
            drawHorizontalRing(m, out, 0.15F, Math.max(innerR, curR - 0.45F), curR + 0.15F, ringSegs,
                    argb(e.secondaryColor, 0.88F * fade), argb(e.primaryColor, 0.08F * fade));
            // Jagged golden floor lightning forks radiating across the storm band.
            int seed = e.id + (int) (age * 22.0F);
            for (int i = 0; i < rays; i++) {
                float ang = i * (TAU / rays) + 0.18F;
                float sx = innerR > 0.6F ? Mth.cos(ang) * innerR : 0.0F;
                float sz = innerR > 0.6F ? Mth.sin(ang) * innerR : 0.0F;
                float gx = Mth.cos(ang) * curR;
                float gz = Mth.sin(ang) * curR;
                drawJaggedBolt(m, out, sx, 0.9F, sz, gx, 0.16F, gz, 5, 0.38F, seed + i * 19,
                        0.11F * fade,
                        argb(0xFFFDF0, 0.94F * fade), argb(e.secondaryColor, 0.55F * fade));
            }
            if (ap < 0.35F && innerR <= 0.6F) {
                float core = (1.0F - ap / 0.35F) * cm;
                drawStarburst(m, out, 0.0F, 1.6F, 0.0F, 2.2F * core + 0.8F, age * 6.0F,
                        argb(0xFFFDF0, 0.92F * core), argb(e.secondaryColor, 0.0F));
            }
        }
    }

    /** Deterministic multi-segment jagged 3D lightning bolt connecting two points with zero allocations. */
    private static void drawJaggedBolt(Matrix4f m, VertexConsumer out,
                                       float x0, float y0, float z0,
                                       float x1, float y1, float z1,
                                       int segments, float jitter, int seed, float halfWidth,
                                       int startCol, int endCol) {
        float px = x0;
        float py = y0;
        float pz = z0;
        for (int i = 1; i <= segments; i++) {
            float f = i / (float) segments;
            float nx = Mth.lerp(f, x0, x1);
            float ny = Mth.lerp(f, y0, y1);
            float nz = Mth.lerp(f, z0, z1);
            if (i < segments) {
                float env = Mth.sin(f * (float) Math.PI);
                nx += hashSigned(seed + i * 17) * jitter * env;
                ny += hashSigned(seed + i * 31) * jitter * 0.45F * env;
                nz += hashSigned(seed + i * 53) * jitter * env;
            }
            int c0 = lerpArgb(startCol, endCol, (i - 1) / (float) segments);
            int c1 = lerpArgb(startCol, endCol, f);
            drawRibbonSegment(m, out, px, py, pz, nx, ny, nz, halfWidth, c0, c1);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static int darkenRgb(int rgb, float factor) {
        int r = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    // =========================================================================
    // Low-level 3D geometry primitives (zero allocations)
    // =========================================================================

    private static void drawArcRibbon(Matrix4f m, VertexConsumer out, float startDeg, float endDeg,
                                      float innerR, float outerR, float yInner, float yOuter, int segs,
                                      int innerCol, int outerCol) {
        for (int i = 0; i < segs; i++) {
            float a0 = Mth.lerp(i / (float) segs, startDeg, endDeg) * DEG_TO_RAD;
            float a1 = Mth.lerp((i + 1) / (float) segs, startDeg, endDeg) * DEG_TO_RAD;
            float fx0 = -Mth.sin(a0);
            float fz0 = Mth.cos(a0);
            float fx1 = -Mth.sin(a1);
            float fz1 = Mth.cos(a1);
            quad(m, out,
                    fx0 * innerR, yInner, fz0 * innerR,
                    fx1 * innerR, yInner, fz1 * innerR,
                    fx1 * outerR, yOuter, fz1 * outerR,
                    fx0 * outerR, yOuter, fz0 * outerR,
                    innerCol, innerCol, outerCol, outerCol);
        }
    }

    /** Tapered fiery blade crescent with organic flame flicker on the outer edge and smooth tail-to-head fade. */
    private static void drawTaperedSlash(Matrix4f m, VertexConsumer out, float startDeg, float endDeg,
                                         float innerR, float outerR, float yOffset, float bevelY, int segs,
                                         int innerRgb, int outerRgb, float innerAlpha, float outerAlpha, float age) {
        for (int i = 0; i < segs; i++) {
            float f0 = i / (float) segs;
            float f1 = (i + 1) / (float) segs;
            float a0 = Mth.lerp(f0, startDeg, endDeg) * DEG_TO_RAD;
            float a1 = Mth.lerp(f1, startDeg, endDeg) * DEG_TO_RAD;

            // Thick near the leading blade head (f -> 1), razor-tapered at the trailing tip (f -> 0).
            float env0 = (float) Math.pow(Mth.sin(f0 * (float) Math.PI), 0.65) * (0.35F + 0.65F * f0);
            float env1 = (float) Math.pow(Mth.sin(f1 * (float) Math.PI), 0.65) * (0.35F + 0.65F * f1);
            float ripple0 = 1.0F + 0.045F * Mth.sin(f0 * 14.0F - age * 28.0F);
            float ripple1 = 1.0F + 0.045F * Mth.sin(f1 * 14.0F - age * 28.0F);

            float outR0 = outerR * ripple0;
            float outR1 = outerR * ripple1;
            float inR0 = Mth.lerp(env0, outR0 * 0.96F, innerR);
            float inR1 = Mth.lerp(env1, outR1 * 0.96F, innerR);

            float fx0 = -Mth.sin(a0);
            float fz0 = Mth.cos(a0);
            float fx1 = -Mth.sin(a1);
            float fz1 = Mth.cos(a1);

            int cIn0 = argb(innerRgb, innerAlpha * env0);
            int cIn1 = argb(innerRgb, innerAlpha * env1);
            int cOut0 = argb(outerRgb, outerAlpha * env0);
            int cOut1 = argb(outerRgb, outerAlpha * env1);

            quad(m, out,
                    fx0 * inR0, yOffset, fz0 * inR0,
                    fx1 * inR1, yOffset, fz1 * inR1,
                    fx1 * outR1, yOffset + bevelY * env1, fz1 * outR1,
                    fx0 * outR0, yOffset + bevelY * env0, fz0 * outR0,
                    cIn0, cIn1, cOut1, cOut0);
        }
    }

    private static void drawHorizontalRing(Matrix4f m, VertexConsumer out, float y, float innerR, float outerR,
                                           int segs, int innerCol, int outerCol) {
        if (outerR <= innerR) {
            return;
        }
        for (int i = 0; i < segs; i++) {
            float a0 = i * (TAU / segs);
            float a1 = (i + 1) * (TAU / segs);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(m, out,
                    c0 * innerR, y, s0 * innerR,
                    c1 * innerR, y, s1 * innerR,
                    c1 * outerR, y, s1 * outerR,
                    c0 * outerR, y, s0 * outerR,
                    innerCol, innerCol, outerCol, outerCol);
        }
    }

    /** Vertical ring in the local XY plane facing along +Z, centered at (cx, cy, cz). */
    private static void drawVerticalRing(Matrix4f m, VertexConsumer out, float cx, float cy, float cz,
                                         float innerR, float outerR, int segs, int innerCol, int outerCol) {
        for (int i = 0; i < segs; i++) {
            float a0 = i * (TAU / segs);
            float a1 = (i + 1) * (TAU / segs);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(m, out,
                    cx + c0 * innerR, cy + s0 * innerR, cz,
                    cx + c1 * innerR, cy + s1 * innerR, cz,
                    cx + c1 * outerR, cy + s1 * outerR, cz,
                    cx + c0 * outerR, cy + s0 * outerR, cz,
                    innerCol, innerCol, outerCol, outerCol);
        }
    }

    /** Open tapered octagonal/decagonal cone extending along +Z from zStart to zEnd. */
    private static void drawTaperedConeZ(Matrix4f m, VertexConsumer out, float cx, float cy, float zStart, float zEnd,
                                         float rStart, float rEnd, int segs, int startCol, int endCol) {
        for (int i = 0; i < segs; i++) {
            float a0 = i * (TAU / segs);
            float a1 = (i + 1) * (TAU / segs);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(m, out,
                    cx + c0 * rStart, cy + s0 * rStart, zStart,
                    cx + c1 * rStart, cy + s1 * rStart, zStart,
                    cx + c1 * rEnd, cy + s1 * rEnd, zEnd,
                    cx + c0 * rEnd, cy + s0 * rEnd, zEnd,
                    startCol, startCol, endCol, endCol);
        }
    }

    private static void drawBeamPrism(Matrix4f m, VertexConsumer out, float len, float rNear, float rFar,
                                      int segs, int nearCol, int farCol) {
        drawTaperedConeZ(m, out, 0.0F, 0.0F, 0.0F, len, rNear, rFar, segs, nearCol, farCol);
    }

    private static void drawCylinderY(Matrix4f m, VertexConsumer out, float y0, float y1, float r0, float r1,
                                      int segs, int botCol, int topCol) {
        for (int i = 0; i < segs; i++) {
            float a0 = i * (TAU / segs);
            float a1 = (i + 1) * (TAU / segs);
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            quad(m, out,
                    c0 * r0, y0, s0 * r0,
                    c1 * r0, y0, s1 * r0,
                    c1 * r1, y1, s1 * r1,
                    c0 * r1, y1, s0 * r1,
                    botCol, botCol, topCol, topCol);
        }
    }

    private static void drawHemisphere(Matrix4f m, VertexConsumer out, float radius, int slices, int stacks,
                                       int botCol, int topCol) {
        for (int st = 0; st < stacks; st++) {
            float v0 = st / (float) stacks;
            float v1 = (st + 1) / (float) stacks;
            float p0 = v0 * (float) (Math.PI * 0.5);
            float p1 = v1 * (float) (Math.PI * 0.5);
            float y0 = Mth.sin(p0) * radius;
            float y1 = Mth.sin(p1) * radius;
            float r0 = Mth.cos(p0) * radius;
            float r1 = Mth.cos(p1) * radius;
            int col0 = lerpArgb(botCol, topCol, v0);
            int col1 = lerpArgb(botCol, topCol, v1);

            for (int sl = 0; sl < slices; sl++) {
                float a0 = sl * (TAU / slices);
                float a1 = (sl + 1) * (TAU / slices);
                float c0 = Mth.cos(a0);
                float s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1);
                float s1 = Mth.sin(a1);
                quad(m, out,
                        c0 * r0, y0, s0 * r0,
                        c1 * r0, y0, s1 * r0,
                        c1 * r1, y1, s1 * r1,
                        c0 * r1, y1, s0 * r1,
                        col0, col0, col1, col1);
            }
        }
    }

    private static void drawSphere(Matrix4f m, VertexConsumer out, float cx, float cy, float cz, float radius,
                                   int slices, int stacks, int col) {
        for (int st = 0; st < stacks; st++) {
            float p0 = -((float) Math.PI * 0.5F) + (st / (float) stacks) * (float) Math.PI;
            float p1 = -((float) Math.PI * 0.5F) + ((st + 1) / (float) stacks) * (float) Math.PI;
            float y0 = cy + Mth.sin(p0) * radius;
            float y1 = cy + Mth.sin(p1) * radius;
            float r0 = Mth.cos(p0) * radius;
            float r1 = Mth.cos(p1) * radius;
            for (int sl = 0; sl < slices; sl++) {
                float a0 = sl * (TAU / slices);
                float a1 = (sl + 1) * (TAU / slices);
                float c0 = Mth.cos(a0);
                float s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1);
                float s1 = Mth.sin(a1);
                quad(m, out,
                        cx + c0 * r0, y0, cz + s0 * r0,
                        cx + c1 * r0, y0, cz + s1 * r0,
                        cx + c1 * r1, y1, cz + s1 * r1,
                        cx + c0 * r1, y1, cz + s0 * r1,
                        col, col, col, col);
            }
        }
    }

    /** Two orthogonal crossed quads connecting two 3D points so beams, tendrils, and streaks read from every angle. */
    private static void drawRibbonSegment(Matrix4f m, VertexConsumer out,
                                          float x0, float y0, float z0,
                                          float x1, float y1, float z1,
                                          float halfWidth, int col0, int col1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4F) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        // Orthonormal basis perpendicular to segment direction.
        float ux = Math.abs(dy) < 0.9F ? 0.0F : 1.0F;
        float uy = Math.abs(dy) < 0.9F ? 1.0F : 0.0F;
        float uz = 0.0F;
        float ax = dy * uz - dz * uy;
        float ay = dz * ux - dx * uz;
        float az = dx * uy - dy * ux;
        float alen = Mth.sqrt(ax * ax + ay * ay + az * az);
        if (alen < 1.0e-4F) {
            return;
        }
        ax = (ax / alen) * halfWidth;
        ay = (ay / alen) * halfWidth;
        az = (az / alen) * halfWidth;

        float bx = (dy * az - dz * ay);
        float by = (dz * ax - dx * az);
        float bz = (dx * ay - dy * ax);

        quad(m, out,
                x0 - ax, y0 - ay, z0 - az,
                x0 + ax, y0 + ay, z0 + az,
                x1 + ax, y1 + ay, z1 + az,
                x1 - ax, y1 - ay, z1 - az,
                col0, col0, col1, col1);
        quad(m, out,
                x0 - bx, y0 - by, z0 - bz,
                x0 + bx, y0 + by, z0 + bz,
                x1 + bx, y1 + by, z1 + bz,
                x1 - bx, y1 - by, z1 - bz,
                col0, col0, col1, col1);
    }

    private static void drawGroundStrip(Matrix4f m, VertexConsumer out, float x0, float z0, float x1, float z1,
                                        float halfWidth, float y, int col0, int col1) {
        float dx = x1 - x0;
        float dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4F) {
            return;
        }
        float px = (-dz / len) * halfWidth;
        float pz = (dx / len) * halfWidth;
        quad(m, out,
                x0 - px, y, z0 - pz,
                x0 + px, y, z0 + pz,
                x1 + px, y, z1 + pz,
                x1 - px, y, z1 - pz,
                col0, col0, col1, col1);
    }

    /** Three intersecting glowing diamond starbursts centered at (cx, cy, cz) for bright solar flares. */
    private static void drawStarburst(Matrix4f m, VertexConsumer out, float cx, float cy, float cz,
                                      float radius, float spinRad, int centerCol, int edgeCol) {
        float cs = Mth.cos(spinRad);
        float sn = Mth.sin(spinRad);
        float r = radius;
        float w = radius * 0.22F;
        // XY plane cross
        drawStarBlade(m, out, cx, cy, cz, cs * r, sn * r, 0.0F, -sn * w, cs * w, 0.0F, centerCol, edgeCol);
        drawStarBlade(m, out, cx, cy, cz, -sn * r, cs * r, 0.0F, cs * w, sn * w, 0.0F, centerCol, edgeCol);
        // ZY plane cross
        drawStarBlade(m, out, cx, cy, cz, 0.0F, cs * r, sn * r, 0.0F, -sn * w, cs * w, centerCol, edgeCol);
        // XZ plane cross
        drawStarBlade(m, out, cx, cy, cz, cs * r, 0.0F, sn * r, -sn * w, 0.0F, cs * w, centerCol, edgeCol);
    }

    private static void drawStarBlade(Matrix4f m, VertexConsumer out, float cx, float cy, float cz,
                                      float dx, float dy, float dz, float wx, float wy, float wz,
                                      int centerCol, int edgeCol) {
        quad(m, out,
                cx - dx, cy - dy, cz - dz,
                cx + wx, cy + wy, cz + wz,
                cx + dx, cy + dy, cz + dz,
                cx - wx, cy - wy, cz - wz,
                edgeCol, centerCol, edgeCol, centerCol);
    }

    private static void quad(Matrix4f m, VertexConsumer out,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             int c1, int c2, int c3, int c4) {
        out.addVertex(m, x1, y1, z1).setColor(c1);
        out.addVertex(m, x2, y2, z2).setColor(c2);
        out.addVertex(m, x3, y3, z3).setColor(c3);
        out.addVertex(m, x4, y4, z4).setColor(c4);
    }

    // =========================================================================
    // Color & easing helpers
    // =========================================================================

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static int lerpArgb(int c0, int c1, float t) {
        int a = Math.round(Mth.lerp(t, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF));
        int r = Math.round(Mth.lerp(t, (c0 >>> 16) & 0xFF, (c1 >>> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (c0 >>> 8) & 0xFF, (c1 >>> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, c0 & 0xFF, c1 & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float hashSigned(int seed) {
        int h = seed * 0x27d4eb2d;
        h ^= (h >>> 15);
        h *= 0x85ebca6b;
        return ((h & 0xFFFF) / 32767.5F) - 1.0F;
    }

    private static float clamp01(float v) {
        return Mth.clamp(v, 0.0F, 1.0F);
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    private static float easeOutQuad(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static float easeOutQuart(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u * u;
    }
}
