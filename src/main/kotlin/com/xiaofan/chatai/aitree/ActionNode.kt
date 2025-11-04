package com.xiaofan.chatai.aitree

import com.xiaofan.chatai.aiplayerentity.AIPlayerEntity

/**
 * 行为树节点基类
 * 所有行为节点都需要继承此类
 */
sealed class ActionNode(val name: String) {
    enum class Status { SUCCESS, FAILURE, RUNNING }
    
    var status: Status = Status.RUNNING
        protected set

    abstract fun start()
    abstract fun tick()
}

