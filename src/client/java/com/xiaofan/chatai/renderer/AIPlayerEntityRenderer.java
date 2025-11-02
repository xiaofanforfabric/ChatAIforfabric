package com.xiaofan.chatai.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class AIPlayerEntityRenderer extends LivingEntityRenderer<LivingEntity, PlayerEntityModel<LivingEntity>> {
    private static final Identifier DEFAULT_TEXTURE = new Identifier("chatai", "/textures/entity/ai_worker/body.png");

    public AIPlayerEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5F);

        // 强制使用标准玩家模型（Steve）的盔甲
        this.addFeature(new ArmorFeatureRenderer<>(
                this,
                new ArmorEntityModel<LivingEntity>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
                new ArmorEntityModel<LivingEntity>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()
        ));

        // 手持物品渲染
        this.addFeature(new HeldItemFeatureRenderer<>(this, ctx.getHeldItemRenderer()));
    }

    @Override
    public void render(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        this.setModelPose(entity);
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private void setModelPose(LivingEntity entity) {
        PlayerEntityModel<LivingEntity> model = this.getModel();
        model.setVisible(true);
        model.sneaking = entity.isInSneakingPose();

        BipedEntityModel.ArmPose mainHandPose = getArmPose(entity, Hand.MAIN_HAND);
        BipedEntityModel.ArmPose offHandPose = getArmPose(entity, Hand.OFF_HAND);

        if (mainHandPose.isTwoHanded()) {
            offHandPose = entity.getStackInHand(Hand.OFF_HAND).isEmpty() ?
                    BipedEntityModel.ArmPose.EMPTY :
                    BipedEntityModel.ArmPose.ITEM;
        }

        if (entity.getMainArm() == Arm.RIGHT) {
            model.rightArmPose = mainHandPose;
            model.leftArmPose = offHandPose;
        } else {
            model.rightArmPose = offHandPose;
            model.leftArmPose = mainHandPose;
        }
    }

    private static BipedEntityModel.ArmPose getArmPose(LivingEntity entity, Hand hand) {
        ItemStack itemStack = entity.getStackInHand(hand);
        if (itemStack.isEmpty()) {
            return BipedEntityModel.ArmPose.EMPTY;
        }

        if (entity.getActiveHand() == hand && entity.getItemUseTimeLeft() > 0) {
            UseAction useAction = itemStack.getUseAction();
            switch (useAction) {
                case BLOCK: return BipedEntityModel.ArmPose.BLOCK;
                case BOW: return BipedEntityModel.ArmPose.BOW_AND_ARROW;
                case SPEAR: return BipedEntityModel.ArmPose.THROW_SPEAR;
                case CROSSBOW: return (hand == entity.getActiveHand()) ?
                        BipedEntityModel.ArmPose.CROSSBOW_CHARGE :
                        BipedEntityModel.ArmPose.ITEM;
                case SPYGLASS: return BipedEntityModel.ArmPose.SPYGLASS;
                case TOOT_HORN: return BipedEntityModel.ArmPose.TOOT_HORN;
                case BRUSH: return BipedEntityModel.ArmPose.BRUSH;
                default: return BipedEntityModel.ArmPose.ITEM;
            }
        }

        if (!entity.handSwinging && itemStack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(itemStack)) {
            return BipedEntityModel.ArmPose.CROSSBOW_HOLD;
        }

        return BipedEntityModel.ArmPose.ITEM;
    }

    @Override
    public Identifier getTexture(LivingEntity entity) {
        return DEFAULT_TEXTURE;
    }

    @Override
    protected void scale(LivingEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupTransforms(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta) {
        float leaningPitch = entity.getLeaningPitch(tickDelta);
        if (entity.isFallFlying()) {
            super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);
            float roll = entity.getRoll() + tickDelta;
            float clampedRoll = MathHelper.clamp(roll * roll / 100.0F, 0.0F, 1.0F);
            if (!entity.isUsingRiptide()) {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(clampedRoll * (-90.0F - entity.getPitch())));
            }
        } else if (leaningPitch > 0.0F) {
            super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);
            float pitch = entity.isTouchingWater() ? -90.0F - entity.getPitch() : -90.0F;
            float lerpedPitch = MathHelper.lerp(leaningPitch, 0.0F, pitch);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lerpedPitch));
            if (entity.isInSwimmingPose()) {
                matrices.translate(0.0F, -1.0F, 0.3F);
            }
        } else {
            super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);
        }
    }
}