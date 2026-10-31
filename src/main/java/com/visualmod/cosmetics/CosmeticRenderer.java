package com.visualmod.cosmetics;

import com.visualmod.cosmetics.CosmeticModel.Bone;
import com.visualmod.cosmetics.CosmeticModel.Face;
import com.visualmod.cosmetics.CosmeticModel.Part;
import com.visualmod.module.modules.cosmetics.CosmeticsManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CosmeticRenderer {

    private static final Vector3f SUN = new Vector3f(0.35f, 0.85f, 0.4f).normalize();

    /**
     * QUADS без отсечения граней. Слой getDebugFilledBox, на котором косметика сидела раньше,
     * рисуется треугольной лентой, режет грани по нормали и сдвигает глубину polygon-offset'ом
     * (VIEW_OFFSET_Z_LAYERING) — отсюда дыры ровно там, где косметика касается кожи или брони.
     */
    private static final RenderLayer LAYER = RenderLayers.debugQuads();

    private static float lightMul = 1f;

    /** Матрицы костей своей модели: модельное пространство -> смещение относительно камеры. */
    private static final Matrix4f[] BONE_MAT = new Matrix4f[Bone.values().length];
    private static final Matrix4f M_ROOT = new Matrix4f();
    private static final Matrix4f M_HEAD_POS = new Matrix4f();
    private static boolean camInHead;
    /**
     * Направление взгляда камеры (мировые оси) и готовность камеры. Ваниль в виде спереди ставит
     * камеру в 4 блоках перед лицом (Camera.update: moveBy(-clipToSpace(4*scale),0,0)) и придвигает
     * её вплотную, если там блок, — тогда геометрия головы за глазной плоскостью проецируется через
     * ноль и мажет по всему экрану. Отсекаем такие грани, а не весь слот.
     */
    private static float lookX, lookY, lookZ;
    private static boolean eyeReady;
    /** Включается только на тех рисованиях, где глаза касаются (голова, нимб, питомец). */
    private static boolean cullNearEye;
    /** Ближе половины блока по направлению взгляда грань всё равно закрыла бы экран. */
    private static final float EYE_CLEAR = 0.5f;
    /** Матрицы костей уже в камерном пространстве (слой прохода модели) — камеру не прибавлять. */
    private static boolean inModelSpace;
    /** Верхний слой нарисовал косметику в этом кадре — нижний (проход мира) не нужен. */
    private static boolean attachedDone;

    private CosmeticRenderer() {}

    public static void initWorldPass() {
        // Страховочный слой живёт один кадр: иначе проход мира нарисовал бы косметику второй раз
        // по матрицам, оставшимся от предыдущего кадра.
        WorldRenderEvents.END_EXTRACTION.register(ctx -> attachedDone = false);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            try {
                renderEquipped();
            } catch (Throwable t) {
                com.visualmod.util.Diag.once("CosmeticRenderer.body", t);
            }
            try {
                renderFirstPersonPet();
            } catch (Throwable t) {
                com.visualmod.util.Diag.once("CosmeticRenderer.fppet", t);
            }
            try {
                renderFirstPersonNimbus();
            } catch (Throwable t) {
                com.visualmod.util.Diag.once("CosmeticRenderer.fpnimbus", t);
            }
        });
    }

    /**
     * Страховочный слой: поза и положение считаются здесь же, где и геометрия — по образцу крыльев.
     * Позиция из сущности, разворот тела из её же render state, повороты костей из модели.
     */
    private static boolean buildPose(MinecraftClient mc, float td) {
        ClientPlayerEntity p = mc.player;
        EntityRenderer<? super ClientPlayerEntity, ?> r = mc.getEntityRenderDispatcher().getRenderer(p);
        if (!(r instanceof LivingEntityRenderer<?, ?, ?> ler)) return false;
        if (!(ler.getModel() instanceof PlayerEntityModel model)) return false;
        EntityRenderState raw = r.getAndUpdateRenderState(p, td);
        if (!(raw instanceof PlayerEntityRenderState ps)) return false;
        model.setAngles(ps);

        Vec3d pos = p.getLerpedPos(td);
        // Ваниль считает state.bodyYaw из clampBodyYaw по наклону головы (LivingEntityRenderer
        // .updateRenderState), а не из lerp(prevBodyYaw, bodyYaw): пересчёт руками отставал от
        // модели на поворотах, и косметика ехала вслед за телом.
        float yaw = ps.bodyYaw;
        MatrixStack s = new MatrixStack();
        s.translate(pos.x - camX, pos.y - camY, pos.z - camZ);
        s.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        s.scale(-1.0F, -1.0F, 1.0F);
        s.translate(0.0F, -1.501F, 0.0F);
        M_ROOT.set(s.peek().getPositionMatrix());
        captureBone(Bone.HEAD, model.head);
        captureBone(Bone.BODY, model.body);
        captureBone(Bone.LEFT_ARM, model.leftArm);
        captureBone(Bone.RIGHT_ARM, model.rightArm);
        captureBone(Bone.LEFT_LEG, model.leftLeg);
        captureBone(Bone.RIGHT_LEG, model.rightLeg);

        s = new MatrixStack();
        s.multiplyPositionMatrix(M_ROOT);
        s.translate(model.head.originX / 16f, model.head.originY / 16f, model.head.originZ / 16f);
        M_HEAD_POS.set(s.peek().getPositionMatrix());
        return true;
    }

    private static void captureBone(Bone bone, ModelPart part) {
        MatrixStack s = new MatrixStack();
        s.multiplyPositionMatrix(M_ROOT);
        if (part != null) part.applyTransform(s);
        Matrix4f dst = BONE_MAT[bone.ordinal()];
        if (dst == null) dst = BONE_MAT[bone.ordinal()] = new Matrix4f();
        dst.set(s.peek().getPositionMatrix());
        com.visualmod.module.modules.cosmetics.CosmeticReaction.deform(dst, bone);
    }

    /**
     * Вектор взгляда камеры в мировых осях: стек прохода сущности не повёрнут на камеру, поэтому
     * сравнение «грань перед глазом или за» считаем здесь, а не в модельных координатах.
     */
    private static boolean setEyeLook(Camera cam) {
        if (cam == null || !cam.isReady()) return false;
        Vec3d d = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        lookX = (float) d.x;
        lookY = (float) d.y;
        lookZ = (float) d.z;
        return true;
    }

    /**
     * Верхний слой: косметика ставится в том же стеке, в котором кадр назад рисовалась модель,
     * — поза, поворот тела и приседание берутся у самой ванили, поэтому рассинхрона нет физически.
     * Прежний вариант снимал эти матрицы здесь, а рисовал в проходе мира следующим кадром: на
     * поворотах косметика отставала от тела.
     */
    public static void renderAttached(PlayerEntityRenderState state, PlayerEntityModel model,
                                      MatrixStack ms, OrderedRenderCommandQueue queue) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (state.id != mc.player.getId()) return;
        if (mc.options.getPerspective().isFirstPerson()) return;
        if (state.invisible || mc.player.isSpectator() || mc.player.isSleeping()) return;
        if (!anythingEquipped()) return;

        inModelSpace = true;
        double sec = System.currentTimeMillis() / 1000.0;
        try {
            camInHead = mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT;
            eyeReady = setEyeLook(mc.gameRenderer.getCamera());
            MatrixStack root = new MatrixStack();
            root.multiplyPositionMatrix(ms.peek().getPositionMatrix());
            M_ROOT.set(root.peek().getPositionMatrix());
            captureBone(Bone.HEAD, model.head);
            captureBone(Bone.BODY, model.body);
            captureBone(Bone.LEFT_ARM, model.leftArm);
            captureBone(Bone.RIGHT_ARM, model.rightArm);
            captureBone(Bone.LEFT_LEG, model.leftLeg);
            captureBone(Bone.RIGHT_LEG, model.rightLeg);
            root.translate(model.head.originX / 16f, model.head.originY / 16f, model.head.originZ / 16f);
            M_HEAD_POS.set(root.peek().getPositionMatrix());

            // Очередь копирует матрицу в момент submit, поэтому отложенная команда рисует в том же базисе.
            queue.submitCustom(ms, LAYER, (entry, buf) -> drawEquipped(mc, buf, sec));
            attachedDone = true;
        } finally {
            // Флаг пространства left=true после исключения смазало бы нижний слой навсегда:
            // проход мира рисовал бы косметику без возврата камеры.
            inModelSpace = false;
        }
    }

    /** Один обход надетого для обоих слоёв: различаются только базовые матрицы (см. boneStack). */
    private static void drawEquipped(MinecraftClient mc, VertexConsumer vc, double sec) {
        Cosmetics.ensureLoaded();
        CosmeticModel hat = CosmeticsManager.equippedHat >= 0 ? Cosmetics.get("hats", CosmeticsManager.equippedHat) : null;
        CosmeticModel armor = CosmeticsManager.equippedArmor >= 0 ? Cosmetics.get("armors", CosmeticsManager.equippedArmor) : null;
        CosmeticModel face = CosmeticsManager.equippedFace >= 0 ? Cosmetics.get("faces", CosmeticsManager.equippedFace) : null;
        CosmeticModel gloves = CosmeticsManager.equippedGloves >= 0 ? Cosmetics.get("gloves", CosmeticsManager.equippedGloves) : null;
        CosmeticModel horns = CosmeticsManager.equippedHorns >= 0 ? Cosmetics.get("horns", CosmeticsManager.equippedHorns) : null;
        CosmeticModel mask = CosmeticsManager.equippedMask >= 0 ? Cosmetics.get("masks", CosmeticsManager.equippedMask) : null;
        CosmeticModel tail = CosmeticsManager.equippedTail >= 0 ? Cosmetics.get("tails", CosmeticsManager.equippedTail) : null;
        CosmeticModel legs = CosmeticsManager.equippedLegs >= 0 ? Cosmetics.get("legs", CosmeticsManager.equippedLegs) : null;
        CosmeticModel back = CosmeticsManager.equippedBack >= 0 ? Cosmetics.get("backs", CosmeticsManager.equippedBack) : null;
        CosmeticModel pet = CosmeticsManager.equippedPet >= 0 ? Cosmetics.get("pets", CosmeticsManager.equippedPet) : null;
        Capes.CapeConfig cape = CosmeticsManager.equippedCape >= 0 ? Cosmetics.getCape(CosmeticsManager.equippedCape) : null;
        int nimbus = CosmeticsManager.equippedNimbus;

        lightMul = 0.22f + 0.78f * Math.max(0f, Math.min(1f, mc.player.getBrightnessAtEyes()));

        // В виде спереди голова больше не запрещается целиком: грани у глаза отсекает emit.
        // Без готовой камеры оставляем прежнее молчание, чтобы не получить мусор по экрану.
        cullNearEye = camInHead && eyeReady;
        if (!camInHead || eyeReady) drawBone(Bone.HEAD, vc, sec, hat, face, armor, horns, mask);
        cullNearEye = false;
        drawBone(Bone.BODY, vc, sec, armor, tail, back);
        if (!camInHead) {
            drawBone(Bone.LEFT_ARM, vc, sec, gloves, armor);
            drawBone(Bone.RIGHT_ARM, vc, sec, gloves, armor);
        } else {
            drawBone(Bone.LEFT_ARM, vc, sec, armor);
            drawBone(Bone.RIGHT_ARM, vc, sec, armor);
        }
        drawBone(Bone.LEFT_LEG, vc, sec, armor, legs);
        drawBone(Bone.RIGHT_LEG, vc, sec, armor, legs);
        // Питомец в виде спереди залетает за спину камеры: его грани тоже режем по глазу.
        cullNearEye = camInHead && eyeReady;
        drawPet(pet, vc, sec);
        cullNearEye = false;

        if (cape != null) {
            Matrix4f bodyCamRel = BONE_MAT[Bone.BODY.ordinal()];
            MatrixStack ms = boneStack(bodyCamRel);
            if (ms != null) {
                Matrix4f mat = ms.peek().getPositionMatrix();
                for (Face f : Capes.build(cape, sec)) {
                    if (capeAtCamera(bodyCamRel, f)) continue;
                    emit(vc, mat, f);
                }
            }
        }

        if (nimbus >= 0 && (!camInHead || eyeReady) && !nimbusHidden()) {
            // Позиция кости головы без её поворота: кольцо остаётся горизонтальным и повторяет
            // приседание, не дёргаясь от резких движений мышью.
            MatrixStack ms = boneStack(M_HEAD_POS);
            if (ms != null) {
                ms.translate(0f, (float) Math.sin(sec * 1.15) * 0.30f / 16f, 0f);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (sec * 16.0)));
                Matrix4f mat = ms.peek().getPositionMatrix();
                cullNearEye = camInHead && eyeReady;
                for (Face f : Nimbus.build(nimbus, CosmeticsManager.nimbusColor)) emit(vc, mat, f);
                cullNearEye = false;
            }
        }
    }

    private static boolean anythingEquipped() {
        return CosmeticsManager.equippedHat >= 0 || CosmeticsManager.equippedArmor >= 0
                || CosmeticsManager.equippedFace >= 0 || CosmeticsManager.equippedGloves >= 0
                || CosmeticsManager.equippedHorns >= 0 || CosmeticsManager.equippedMask >= 0
                || CosmeticsManager.equippedTail >= 0 || CosmeticsManager.equippedLegs >= 0
                || CosmeticsManager.equippedBack >= 0 || CosmeticsManager.equippedPet >= 0
                || CosmeticsManager.equippedCape >= 0 || CosmeticsManager.equippedNimbus >= 0;
    }

    /** Нижний слой: всё надето на себя, но рисуется в проходе мира — если верхний молчит. */
    private static void renderEquipped() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.getBufferBuilders() == null) return;
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        Camera cam = mc.gameRenderer.getCamera();
        // Верхний слой уже нарисовал cosmetics в этом кадре: второй раз тем же слоем debugQuads
        // дало бы двойное смешивание на полупрозрачных гранях и лишний polygon offset.
        if (attachedDone) return;
        // Своя модель в первом лице не рисуется вовсе, а камера стоит в черепе: косметика на голове
        // легла бы мусором по всему экрану. Для первого лица есть отдельные проходы.
        if (mc.options.getPerspective().isFirstPerson()) return;
        if (mc.player.isSpectator() || mc.player.isSleeping() || mc.player.isInvisible()) return;
        if (com.visualmod.util.IrisCompat.skipWorldPass()) return;

        Vec3d camPos = cam.getCameraPos();
        camX = (float) camPos.x;
        camY = (float) camPos.y;
        camZ = (float) camPos.z;
        if (!buildPose(mc, mc.getRenderTickCounter().getTickProgress(false))) return;
        camInHead = mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT;
        eyeReady = setEyeLook(cam);

        drawEquipped(mc, imm.getBuffer(LAYER), System.currentTimeMillis() / 1000.0);
        com.visualmod.util.RenderFlush.flush(imm, LAYER);
    }

    private static MatrixStack at(Matrix4f base) {
        if (base == null) return null;
        MatrixStack s = new MatrixStack();
        s.multiplyPositionMatrix(base);
        return s;
    }

    /**
     * Базовая матрица кости для текущего слоя: проход модели уже стоит в камерном пространстве,
     * а стек прохода мира — в абсолютном начале координат, поэтому во втором случае камеру нужно
     * вернуть (крылья делают translate(-cam) и рисуют мировые позиции).
     */
    private static MatrixStack boneStack(Matrix4f base) {
        if (base == null) return null;
        return inModelSpace ? at(base) : atWorld(base);
    }

    /**
     * Стек прохода мира стоит в абсолютном начале координат (крылья делают translate(-cam) и рисуют
     * мировые позиции), а матрицы поз — смещение «позиция − камера». Без возврата камеры сюда
     * косметика уехала бы на двойное расстояние от начала мира.
     */
    private static MatrixStack atWorld(Matrix4f base) {
        if (base == null) return null;
        MatrixStack s = new MatrixStack();
        s.translate(camX, camY, camZ);
        s.multiplyPositionMatrix(base);
        return s;
    }

    private static float camX, camY, camZ;

    /** Части выбираются обходом моделей по кости — на кадр не создаётся ни одного списка. */
    private static void drawBone(Bone bone, VertexConsumer vc, double sec, CosmeticModel... models) {
        MatrixStack ms = boneStack(BONE_MAT[bone.ordinal()]);
        if (ms == null) return;
        for (CosmeticModel m : models) {
            if (m == null) continue;
            for (Part p : m.parts) {
                if (p.bone == bone) drawPart(p, ms, vc, sec);
            }
        }
    }

    private static final float FP_PET_SCALE = 0.14f;
    private static final float FP_PET_REACH = 1.7f;
    private static final double FP_PET_FORWARD = 1.15;
    private static final double FP_PET_SIDE = 0.5;
    private static final double FP_PET_UP = 0.2;

    private static final Matrix4f FP_ARM_RIGHT = new Matrix4f();
    private static final Matrix4f FP_ARM_LEFT = new Matrix4f();
    private static boolean fpArmRight, fpArmLeft;
    private static int fpLight = -1;

    public static void captureFirstPersonArm(MatrixStack ms, ModelPart arm, boolean right, int light) {
        if (arm == null || CosmeticsManager.equippedGloves < 0) return;
        MatrixStack s = new MatrixStack();
        s.multiplyPositionMatrix(ms.peek().getPositionMatrix());
        arm.applyTransform(s);
        (right ? FP_ARM_RIGHT : FP_ARM_LEFT).set(s.peek().getPositionMatrix());
        if (right) fpArmRight = true; else fpArmLeft = true;
        fpLight = light;
    }

    public static void renderFirstPersonHands(MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (!fpArmRight && !fpArmLeft) return;
        boolean right = fpArmRight, left = fpArmLeft;
        fpArmRight = fpArmLeft = false;
        Cosmetics.ensureLoaded();
        CosmeticModel gloves = CosmeticsManager.equippedGloves >= 0
                ? Cosmetics.get("gloves", CosmeticsManager.equippedGloves) : null;
        if (gloves == null) return;
        lightMul = envBrightness(fpLight);
        double sec = System.currentTimeMillis() / 1000.0;
        queue.submitCustom(matrices, LAYER, (entry, buf) -> {
            // Перчатки рисуем тем же альфа-профилем, что и ванильную руку: иначе непрозрачный
            // косметический рукав снова закрывает «стекло».
            VertexConsumer vc = com.visualmod.module.modules.render.GlassItem.ofHands(buf);
            if (right) drawArmParts(Bone.RIGHT_ARM, FP_ARM_RIGHT, gloves, vc, sec);
            if (left) drawArmParts(Bone.LEFT_ARM, FP_ARM_LEFT, gloves, vc, sec);
        });
    }

    private static void drawArmParts(Bone bone, Matrix4f armMat, CosmeticModel model,
                                     VertexConsumer vc, double sec) {
        MatrixStack s = at(armMat);
        for (Part p : model.parts) {
            if (p.bone == bone) drawPart(p, s, vc, sec);
        }
    }

    private static void renderFirstPersonPet() {
        if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (CosmeticsManager.equippedPet < 0) return;
        Cosmetics.ensureLoaded();
        CosmeticModel pet = Cosmetics.get("pets", CosmeticsManager.equippedPet);
        if (pet == null) return;

        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getCameraPos();
        Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d side = look.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
        Vec3d anchor = camPos.add(look.multiply(FP_PET_FORWARD))
                .add(side.multiply(FP_PET_SIDE)).add(0.0, FP_PET_UP, 0.0);

        MatrixStack ms = new MatrixStack();
        ms.translate(anchor.x - camPos.x, anchor.y - camPos.y, anchor.z - camPos.z);
        ms.multiply(cam.getRotation());
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cam.getYaw()));
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0F, -1.501F, 0.0F);
        ms.scale(FP_PET_SCALE, FP_PET_SCALE, FP_PET_SCALE);

        double sec = System.currentTimeMillis() / 1000.0;
        PetAI.Pose p = PetAI.pose(sec, CosmeticsManager.equippedPet);
        // Камерное пространство повёрнуто на 180° относительно рендера сущности,
        // поэтому боковые и продольные смещения разворачиваем вместе с курсом.
        float ox = clampF(-p.x * FP_PET_REACH, -34f, 34f);
        float oy = clampF((p.y - 24.0f) * 1.2f, -16f, 22f);
        float oz = clampF(-p.z * FP_PET_REACH, -34f, 34f);
        ms.translate(ox / 16f, oy / 16f, oz / 16f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotation(p.yaw + (float) Math.PI));

        lightMul = 1f;
        VertexConsumer vc = imm.getBuffer(LAYER);
        emitModel(pet, ms, vc, PetAI.animTime());
        com.visualmod.util.RenderFlush.flush(imm, LAYER);
    }

    /**
     * Нимб от первого лица. Своя модель игрока в этом режиме не рисуется вообще, поэтому кольца
     * над головой не было видно, даже если посмотреть вверх. Здесь оно ставится в мир по тем же
     * матрицам, что и в третьем лице: позиция ног -> тот же корневой разворот, что у модели.
     * Сверху добавляется полблока: у кольца радиус ~0,4 блока, и чем выше оно над глазом, тем
    /**
     * Кольцо висит почти над глазами, поэтому при взгляде вверх ложится ровно на центр экрана и
     * мешает целиться. Прячем его, как только камера задрана выше ~30°.
     */
    private static boolean nimbusHidden() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.gameRenderer != null && mc.gameRenderer.getCamera().getPitch() < -30f;
    }

    /**
     * ближе оно к зениту и тем лучше влезает в FOV при взгляде вверх. Целый блок подъёма — как
     * было раньше — задирал нимб заметно выше, чем он сидит в третьем лице.
     */
    private static void renderFirstPersonNimbus() {
        if (com.visualmod.util.IrisCompat.skipWorldPass()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (CosmeticsManager.equippedNimbus < 0 || mc.player.isSpectator() || mc.player.isSleeping()) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;
        if (nimbusHidden()) return;
        Cosmetics.ensureLoaded();

        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        Camera cam = mc.gameRenderer.getCamera();
        float td = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d pos = mc.player.getLerpedPos(td);
        float bodyYaw = MathHelper.lerpAngleDegrees(td, mc.player.lastBodyYaw, mc.player.bodyYaw);
        double sec = System.currentTimeMillis() / 1000.0;

        MatrixStack ms = new MatrixStack();
        ms.translate(pos.x - cam.getCameraPos().x, pos.y - cam.getCameraPos().y, pos.z - cam.getCameraPos().z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - bodyYaw));
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0F, -1.501F, 0.0F);
        ms.translate(0.0F, -0.5F, 0.0F);
        ms.translate(0f, (float) Math.sin(sec * 1.15) * 0.30f / 16f, 0f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) (sec * 16.0)));
        lightMul = 1f;
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = imm.getBuffer(LAYER);
        // Кольцо стоит почти над самым глазом: грани, ушедшие за плоскость камеры, без этого
        // теста проецируются «через ноль» и мажут garbage по всему экрану.
        Vec3d dir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        for (Face f : Nimbus.build(CosmeticsManager.equippedNimbus, CosmeticsManager.nimbusColor)) {
            Vector3f p = faceCenter(mat, f);
            if (p.x() * dir.x + p.y() * dir.y + p.z() * dir.z < 0.12f) continue;
            emit(vc, mat, f);
        }
        com.visualmod.util.RenderFlush.flush(imm, LAYER);
    }

    private static final Vector3f CENTER = new Vector3f();

    /** Центр грани в мировых смещениях от камеры (модельные координаты — это 1/16 блока). */
    private static Vector3f faceCenter(Matrix4f mat, Face f) {
        CENTER.set((f.v[0][0] + f.v[1][0] + f.v[2][0] + f.v[3][0]) / 64f,
                (f.v[0][1] + f.v[1][1] + f.v[2][1] + f.v[3][1]) / 64f,
                (f.v[0][2] + f.v[1][2] + f.v[2][2] + f.v[3][2]) / 64f);
        return mat.transformPosition(CENTER);
    }

    /**
     * Грань на уровне глаза или за ним — в виде спереди она залила бы экран мусором вместо ткани.
     * Матрица слоя прохода модели уже камерная, а стек прохода мира абсолютный, поэтому во втором
     * случае камеру сначала вычитаем.
     */
    private static boolean faceAtEye(Matrix4f mat, Face f) {
        Vector3f p = faceCenter(mat, f);
        if (!inModelSpace) p.sub(camX, camY, camZ);
        return p.x() * lookX + p.y() * lookY + p.z() * lookZ < EYE_CLEAR;
    }

    /** Грань плаща у самого глаза или за ним: на экране она дала бы мусор вместо ткани. */
    private static boolean capeAtCamera(Matrix4f bodyCamRel, Face f) {
        return faceCenter(bodyCamRel, f).lengthSquared() < 1.0f;
    }

    private static void drawPart(Part p, MatrixStack ms, VertexConsumer vc, double sec) {
        if (p.anim != null) {
            ms.push();
            applyAnim(p, ms, sec);
            Matrix4f mat = ms.peek().getPositionMatrix();
            for (Face f : p.faces) emit(vc, mat, f);
            ms.pop();
        } else {
            Matrix4f mat = ms.peek().getPositionMatrix();
            for (Face f : p.faces) emit(vc, mat, f);
        }
    }

    private static void applyAnim(Part p, MatrixStack ms, double sec) {
        float t = (float) (sec * p.animSpeed + p.animPhase);
        switch (p.anim) {
            case "flap", "sway" -> {
                float ang = (float) Math.sin(t) * p.animAmp;
                ms.translate(p.animPivot[0] / 16f, p.animPivot[1] / 16f, p.animPivot[2] / 16f);
                ms.multiply(switch (p.animAxis) {
                    case 'x' -> RotationAxis.POSITIVE_X.rotation(ang);
                    case 'y' -> RotationAxis.POSITIVE_Y.rotation(ang);
                    default -> RotationAxis.POSITIVE_Z.rotation(ang);
                });
                ms.translate(-p.animPivot[0] / 16f, -p.animPivot[1] / 16f, -p.animPivot[2] / 16f);
            }
            case "bob" -> ms.translate(0f, (float) Math.sin(t) * p.animAmp / 16f, 0f);
            default -> { }
        }
    }

    private static void drawPet(CosmeticModel pet, VertexConsumer vc, double sec) {
        if (pet == null) return;
        MatrixStack ms = boneStack(M_ROOT);
        if (ms == null) return;
        PetAI.Pose p = PetAI.pose(sec, CosmeticsManager.equippedPet);
        ms.translate(p.x / 16f, p.y / 16f, p.z / 16f);
        ms.multiply(RotationAxis.POSITIVE_Y.rotation(p.yaw));
        emitModel(pet, ms, vc, PetAI.animTime());
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private static void emitModel(CosmeticModel m, MatrixStack ms, VertexConsumer vc, double sec) {
        Matrix4f mat = ms.peek().getPositionMatrix();
        for (Part p : m.parts) {
            if (p.anim == null) {
                for (Face f : p.faces) emit(vc, mat, f);
            } else {
                ms.push();
                applyAnim(p, ms, sec);
                Matrix4f m2 = ms.peek().getPositionMatrix();
                for (Face f : p.faces) emit(vc, m2, f);
                ms.pop();
            }
        }
    }

    private static float envBrightness(int light) {
        if (light < 0) return 1f;
        float sky = ((light >> 4) & 15) / 15f;
        float block = ((light >> 20) & 15) / 15f;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            float day = 0.2f + 0.8f * Math.max(0f, Math.min(1f,
                    (float) Math.cos((mc.world.getTimeOfDay() % 24000L) / 24000.0 * Math.PI * 2.0) * 0.5f + 0.5f));
            sky *= day;
        }
        return 0.22f + 0.78f * Math.max(sky, block);
    }

    private static final Vector3f NORMAL = new Vector3f();

    /**
     * Грань одним обходом: слой рисует QUADS с DISABLE_CULLING, поэтому обе стороны видны и без
     * дубляжа вершин — раньше приходилось писать каждый квад дважды.
     */
    private static void emit(VertexConsumer vc, Matrix4f mat, Face f) {
        if (cullNearEye && faceAtEye(mat, f)) return;
        Vector3f n = NORMAL.set(f.nx, f.ny, f.nz).mulDirection(mat).normalize();
        float lm = f.emissive ? 1f : lightMul;
        float bright = (f.emissive ? 1.0f : 0.60f + 0.40f * Math.max(0f, n.dot(SUN))) * lm;
        float rT = Math.min(1f, f.top[0] * bright), gT = Math.min(1f, f.top[1] * bright), bT = Math.min(1f, f.top[2] * bright);
        float rB = Math.min(1f, f.bottom[0] * bright), gB = Math.min(1f, f.bottom[1] * bright), bB = Math.min(1f, f.bottom[2] * bright);
        float a = f.top[3];

        float bloom = f.emissive ? com.visualmod.module.modules.cosmetics.CosmeticBloom.amount() : 0f;
        if (bloom > 0f) halo(vc, mat, f, rT, gT, bT, bloom);

        vx(vc, mat, f.v[0], rT, gT, bT, a);
        vx(vc, mat, f.v[1], rT, gT, bT, a);
        vx(vc, mat, f.v[2], rB, gB, bB, a);
        vx(vc, mat, f.v[3], rB, gB, bB, a);
    }

    private static void halo(VertexConsumer vc, Matrix4f mat, Face f, float r, float g, float b, float amount) {
        float d = com.visualmod.module.modules.cosmetics.CosmeticBloom.offsetPx();
        float cx = (f.v[0][0] + f.v[1][0] + f.v[2][0] + f.v[3][0]) / 4f;
        float cy = (f.v[0][1] + f.v[1][1] + f.v[2][1] + f.v[3][1]) / 4f;
        float cz = (f.v[0][2] + f.v[1][2] + f.v[2][2] + f.v[3][2]) / 4f;
        for (int i = 0; i < 4; i++) {
            float[] p = f.v[i], q = f.v[(i + 1) & 3];
            float ex = q[0] - p[0], ey = q[1] - p[1], ez = q[2] - p[2];
            float ox = f.ny * ez - f.nz * ey;
            float oy = f.nz * ex - f.nx * ez;
            float oz = f.nx * ey - f.ny * ex;
            float len = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (len < 1e-6f) continue;
            ox /= len; oy /= len; oz /= len;
            float mx = (p[0] + q[0]) / 2f - cx, my = (p[1] + q[1]) / 2f - cy, mz = (p[2] + q[2]) / 2f - cz;
            if (ox * mx + oy * my + oz * mz < 0f) { ox = -ox; oy = -oy; oz = -oz; }
            float a1 = amount * 0.42f, a2 = 0f;
            float px1 = p[0] + ox * d, py1 = p[1] + oy * d, pz1 = p[2] + oz * d;
            float qx1 = q[0] + ox * d, qy1 = q[1] + oy * d, qz1 = q[2] + oz * d;
            vx(vc, mat, p[0], p[1], p[2], r, g, b, a1);
            vx(vc, mat, q[0], q[1], q[2], r, g, b, a1);
            vx(vc, mat, qx1, qy1, qz1, r, g, b, a2);
            vx(vc, mat, px1, py1, pz1, r, g, b, a2);
            vx(vc, mat, px1, py1, pz1, r, g, b, a2);
            vx(vc, mat, qx1, qy1, qz1, r, g, b, a2);
            vx(vc, mat, q[0], q[1], q[2], r, g, b, a1);
            vx(vc, mat, p[0], p[1], p[2], r, g, b, a1);
        }
    }

    private static void vx(VertexConsumer vc, Matrix4f mat, float[] p, float r, float g, float b, float a) {
        vc.vertex(mat, p[0] / 16f, p[1] / 16f, p[2] / 16f).color(r, g, b, a);
    }

    private static void vx(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(mat, x / 16f, y / 16f, z / 16f).color(r, g, b, a);
    }
}
