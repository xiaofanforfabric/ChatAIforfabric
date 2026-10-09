
# ChatAI for Fabric

一个基于 DeepSeek 模型的 Minecraft 聊天 AI 模组，让您在单人游戏中也能与AI智能互动！

## 🎯 模组简介

ChatAI 是一个为 Minecraft Fabric 1.20.1 开发的智能聊天模组，通过集成 DeepSeek 模型 API，让玩家在单人游戏中可以与AI进行自然语言交互，解决无聊时刻的孤独感。

## ✨ 主要功能

### 🤖 智能聊天
- **命令交互**：在世界聊天栏输入 `/ai [你的问题]` 即可与AI对话
- **实时反馈**：提示"AI 思考中"表示模组加载成功
- **安全限制**：回复内容限制500字符，避免内存溢出

### 👥 AI实体伙伴
- **新增实体**：AIPlayer - 您的智能生存伙伴
- **生存技能**：具备砍树、打怪、挖矿、合成等完整生存能力
- **实用功能**：可作为保镖、助手或纯粹的聊天伙伴
- **背包管理**：支持查看和清空AI实体背包

## ⚙️ 配置命令

### API 配置
```minecraft
/ai-config set-key <your-api-key>    # 设置DeepSeek API密钥
/ai-config set-url <api-url>         # 自定义API接口地址
/ai-config show                      # 查看当前配置（密钥脱敏显示）
```

### 调试命令
```minecraft
/aiplayer-debug on                   # 开启调试信息
/aiplayer-debug off                  # 关闭调试信息  
/aiplayer-debug inventory            # 查看AI实体背包
/aiplayer-debug clear                # 清空AI实体背包
```

### 帮助命令
```minecraft
/ai-help                             # 查看完整帮助文档
```

## 🚀 快速开始

1. **获取API密钥**
    - 前往 https://platform.deepseek.com/ 注册并获取API密钥

2. **安装模组**
    - 下载最新版本模组
    - 放入 Minecraft Fabric 1.20.1 的 mods 文件夹

3. **配置使用**
    - 进入游戏后使用 `/ai-config set-key <你的密钥>` 设置API
    - 使用 `/ai [问题]` 开始与AI对话

## 🛠️ 开发者信息

- **开源协议**：[LGPL-3.0-only](LICENSE) —— 可自由使用/修改/分发；**修改后分发须同样以 LGPL-3.0 开源并保留版权声明**。GPL-3.0 全文见 [COPYING](COPYING)。
- **版权**：Copyright (c) 2025–2026 xiaofanforfabric。本项目自 2025-06-05 起开发。
- **许可沿革**：v1–v3.0 历史版本曾以 CC0-1.0 / CC BY-NC-4.0 发布，该部分按原条款继续有效；自本版本起统一为 LGPL-3.0-only。
- **适配扩展**：有能力的开发者可适配其他AI模型接口
- **版本支持**：当前支持 Fabric 1.20.1，新版MC适配进行中

## ⚠️ 重要说明

- **使用范围**：本项目以 LGPL-3.0 授权，**允许商业使用**；修改并分发时须同样以 LGPL-3.0 开源并保留版权声明。
- **密钥责任**：用户需自行承担API调用费用，请合理使用
- **更新提示**：v1版本内置API密钥已失效，请及时更新到v2版本

## 🔮 未来计划

- [ ] AIPlayer聊天功能开发
- [ ] 更高版本Minecraft适配
- [ ] 更多AI模型支持
- [ ] 图形化配置界面

## 📞 支持与贡献

如果您在使用过程中遇到问题或有改进建议，欢迎提交Issue或Pull Request！

---

*让AI为您的Minecraft冒险增添更多乐趣！*

