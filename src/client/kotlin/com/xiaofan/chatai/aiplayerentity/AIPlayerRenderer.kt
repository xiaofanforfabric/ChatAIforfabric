package com.xiaofan.chatai.aiplayerentity

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.render.entity.feature.*
import net.minecraft.client.render.entity.model.BipedEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.PlayerEntityModel
import net.minecraft.client.render.model.BakedModelManager
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Arm
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper

@Environment(EnvType.CLIENT)
class AIPlayerRenderer(ctx: EntityRendererFactory.Context) :
    LivingEntityRenderer<AIPlayerEntity, PlayerEntityModel<AIPlayerEntity>>(
        ctx,
        PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER), false),
        0.5f  // 已添加shadowRadius参数
    ) {

    init {
        // 修正盔甲渲染器类型
        addFeature(object : ArmorFeatureRenderer<AIPlayerEntity, PlayerEntityModel<AIPlayerEntity>, PlayerEntityModel<AIPlayerEntity>>(
            this,
            PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR), false),
            PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR), false),
            ctx.modelLoader as BakedModelManager?
        ) {})

        // 修正手持物品渲染器
        addFeature(HeldItemFeatureRenderer(this, ctx.heldItemRenderer))

        // 修正箭矢渲染器
        addFeature(StuckArrowsFeatureRenderer(ctx, this))

    }

    override fun render(
        entity: AIPlayerEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int
    ) {
        setModelProperties(entity)
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    private fun setModelProperties(entity: AIPlayerEntity) {
        model.apply {
            sneaking = entity.isSneaking
            riding = entity.hasVehicle()
            rightArmPose = getArmPose(entity, entity.mainArm.opposite)
            leftArmPose = getArmPose(entity, entity.mainArm)
        }
    }

    private fun getArmPose(entity: AIPlayerEntity, arm: Arm): BipedEntityModel.ArmPose {
        val stack = if (arm == Arm.RIGHT) entity.mainHandStack else entity.offHandStack
        return when {
            stack.isEmpty -> BipedEntityModel.ArmPose.EMPTY
            entity.isUsingItem && entity.activeHand == (if (arm == Arm.RIGHT) Hand.MAIN_HAND else Hand.OFF_HAND) ->
                BipedEntityModel.ArmPose.ITEM
            entity.isBlocking -> BipedEntityModel.ArmPose.BLOCK
            else -> BipedEntityModel.ArmPose.EMPTY
        }
    }

    override fun getTexture(entity: AIPlayerEntity): Identifier {
        return entity.customSkin ?: Identifier("textures/entity/player/wide/steve.png")
    }
}