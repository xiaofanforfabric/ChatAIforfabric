# 3.0版本更新
这是一个接入 DeepSeek 模型 API 的 MC 聊天 AI，当你在单人游戏感到无聊时，你可以使用此模组与 AI 聊天。
模组完全开源，基于 DeepSeek 模型 API 调用，有能力的开发者可以尝试适配其他 AI 模型的调用，没能力的普通人建议自行购买 DeepSeek API 密钥。
模组目前为第二个版本，第一个版本的内置 API 密钥已经失效，请及时更新。
模组目前支持 Fabric1.20.1（后续有其他分支会另外公告）。
3.0更新版本
添加了一个新实体：AIPlayer，具有砍树，打怪，挖矿，合成等玩家基本生存技能
可以当你的生存的保镖，伙伴。
后续加入聊天功能
模组开源，但是仅供个人学习研究，严禁商用。
[h2=使用方法]
1. 在世界聊天栏输入命令：
/ai (提问内容）
2. 如提示 AI 思考中则证明模组加载成功；
3. 避免生成过长（为了避免内容过长导致导致 JVM 内存溢出导致崩溃，已经限制 500 个字符）；
4. 新增命令：
/ai-help
/ai-config
5. 通过：/ai-config set-key命令设置你的 API 密钥（如果没有请到 DeepSeek 开发平台获取，或通过 /ai-config set-url 改用其他平台 DeepSeek 模型的接口地址，以对应开发平台接口文档为准）。
6. 添加：/ai-config show显示当前配置的 API 的 URL 和密钥，以检查是否正确（API 密钥经过脱敏处理显示）。
7.AI实体命令:
/aiplayer-debug clear
清空AI实体的背包
8.AI实体命令：
/aiplayer-debug inventory
查看AI实体背包
9.AI实体命令：
/aiplayer-debug on
开启AI实体的调试信息（默认关）
10.AI实体命令：
/aiplayer-debug off
关闭AI实体的调试信息（默认关）
![2025-06-26_16 42 40](https://github.com/user-attachments/assets/2be9df02-98c4-440f-8877-6828afc27a1d)
![2025-06-26_17 02 00](https://github.com/user-attachments/assets/e7bc9ecc-7513-4cdf-9881-a57cae5a507b)
![2025-06-26_16 58 10](https://github.com/user-attachments/assets/00e1c810-13bb-4a52-aa8d-64a565d64872)
![2025-06-26_16 58 07](https://github.com/user-attachments/assets/16decb01-d574-4cef-ae35-0f5ebb8bc463)
![2025-06-26_16 58 03](https://github.com/user-attachments/assets/d6d997c3-7068-4ffe-8237-840fd2b80565)
![2025-06-26_16 58 02](https://github.com/user-attachments/assets/e5be2f5d-94a0-429e-80bb-a9a99c526f37)
![2025-06-26_16 57 26](https://github.com/user-attachments/assets/0f1e04fa-7d1d-43ef-95b2-afeee36abda2)
![2025-06-26_16 57 23](https://github.com/user-attachments/assets/ef48ca4a-c4a4-431f-a36d-3a40d2bb8327)
![2025-06-26_16 57 22](https://github.com/user-attachments/assets/906a4259-e431-4116-a939-78e51a8d579d)
![2025-06-26_16 57 14](https://github.com/user-attachments/assets/3d8b1fd2-e74b-4624-a738-b3d9cef1b9cd)
![2025-06-26_16 57 09](https://github.com/user-attachments/assets/0b206fa9-6f77-462f-aae1-3c7a7c6c56ce)
![2025-06-26_16 57 05](https://github.com/user-attachments/assets/100476c0-341b-46c9-8669-70c84dfd6a1e)
![2025-06-26_16 50 00](https://github.com/user-attachments/assets/3edab4a0-071b-4d49-aa30-4b829d2ab650)
![2025-06-26_16 49 55](https://github.com/user-attachments/assets/2925e728-ba52-40d8-9889-915b235cbd7f)
![2025-06-26_16 47 44](https://github.com/user-attachments/assets/cfe560d7-2ecd-40b5-885f-94b900f9f042)
![2025-06-26_16 46 53](https://github.com/user-attachments/assets/c4059d6d-6139-491b-b385-d409505fe769)
![2025-06-26_16 46 41](https://github.com/user-attachments/assets/a3e9dfe7-c3f5-43e2-8b65-16b0ef773e97)
![2025-06-26_16 45 29](https://github.com/user-attachments/assets/669ff5fe-b3c9-4197-b77d-9e4558c753db)
![2025-06-26_16 42 41](https://github.com/user-attachments/assets/f118ed09-c457-44d3-84a3-4b2fa6945f82)
