package com.xiaofan.chatai.aiplayerentity

import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.DefaultAttributeContainer
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity

object ModEntityAttributes {
    // 使怪物主动攻击的关键属性配置
    fun createHostileAttributes(): DefaultAttributeContainer.Builder {
        return MobEntity.createMobAttributes() // 使用MobEntity而非LivingEntity的基础属性
            .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0) // 提高伤害值
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
            .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.1)
            // 添加怪物识别所需的关键属性
            .add(EntityAttributes.GENERIC_ARMOR, 0.0)
            .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 0.5)
    }

    // 注册到实体类型
    fun registerHostileAttributes(entityType: EntityType<out LivingEntity>) {
        FabricDefaultAttributeRegistry.register(entityType, createHostileAttributes())
    }
}