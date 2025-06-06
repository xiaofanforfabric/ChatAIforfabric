# ChatAIforfabric(第二版本分支源码）
这是一个接入以deepseek模型API开发的我的世界聊天AI，当你在单人游戏感到无聊时，你可以通过此模组与AI聊天
模组完全开源，基于deepseek模型API调用，有能力的开发者可以适配其他AI模型的调用，没能力的普通人自己去买一个deepseekAPI密钥
（此为第二个版本，第一个版本的内置API密钥已经失效，请及时更新）
模组适配我的世界fabric1.20.1（后续有其他分支会公告）
# 模组开源，但是仅供个人学习研究，严禁商用
以下是调用方法：
1.在世界聊天栏输入命令/ai (提问内容）
2.如提示AI思考中则证明模组加载成功
3.避免生成过长（为了避免内容过长导致导致JVM内存溢出导致崩溃，已经限制500个字符）
4.新增命令/ai-help,/ai-config
5.通过/ai-config set-key 命令设置你的API密钥（没有去deepseek开发平台充10人民币获取一个）（或者通过/ai-config set-url 改用其他平台deepseek模型的接口地址，以他的开发平台接口文档为准）
6.添加/ai-config show命令，显示当前配置的API的URL和密钥，以检查是否正确（API密钥经过脱敏处理显示）

![2025-06-06_17 16 25](https://github.com/user-attachments/assets/8da0826b-ea20-4133-8124-156056b0b6ad)
![2025-06-06_17 16 56](https://github.com/user-attachments/assets/2b5ea3eb-be6c-4dfd-bc66-277a7b9547da)
![2025-06-06_17 17 44](https://github.com/user-attachments/assets/93bfdc67-ad01-4c6c-8b97-02b2b409cdc2)
![2025-06-06_17 18 00](https://github.com/user-attachments/assets/3b640f57-16f1-476c-bab8-3383ac4be036)
![2025-06-06_17 18 31](https://github.com/user-attachments/assets/c438c699-7e3e-4006-8f3b-9e9ab4e2dca4)
![2025-06-06_17 18 38](https://github.com/user-attachments/assets/bf30919b-22ba-4d7d-83ce-ac7070b27d83)
![2025-06-06_17 19 32](https://github.com/user-attachments/assets/9ab2aa43-dd7d-488b-88b5-40b7af4ef8e2)
