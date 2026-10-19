# Semi-Overt · Android

使用 Kotlin 与 Jetpack Compose 构建的 Semi-Overt 原生 Android 客户端。

> 当前是离线页面与交互版本。文章、作者、历史记录与 Diff 为原创或内置示例；
> 账号页面不连接真实服务，不上传输入，不将密码、邮箱或 token 写入本机偏好；密码不做状态恢复。

## 可体验的页面

- 首页弧形文章队列（11 篇循环，上下滑动选择，卡片按文章顺序叠放，上下留空完整展示中间卡片；上半部向左、下半部向右递退）、阅读、目录、分类与文章 / 作者搜索。
- 分类页按阅读时长展示波浪堆叠：前卡先滑动、后卡逐张跟随，完全滑出底部后从队尾接回；支持反向波浪、阅读返回及分组位置恢复。
- 登录、注册、找回密码、重置密码与结果页：本地表单校验后进入演示身份。
- 未登录我的、已登录演示主页、作者只读主页。
- 编辑昵称、账号与简介；切换内置头像和封面。
- 完整设置、明暗主题、主圆位置调整与持久化；长按滑选、外拉固定快捷入口、拖动与收回。
- 月历记录、年月选择、日期汇总、内置增删 / Diff 和确认清除。
- 加载、空内容、失败、权限与恢复状态的独立预览。

本人点击身份区编辑；作者页只读。阅读返回保留列表位置。
主圆长按滑选不改变背景，深色底色统一 #191a1c。
导航位置、主题和演示资料保存为本机偏好，不是服务器账号数据。

**页面与动效首版不等于真实服务已接入。**
实际安全验证、邮件发送、快照采集与上传均未接入。
像素开屏、主圆编排、曲面网格收卷与独立快捷圆已接入；
最终立体背面 / 侧壁、自遮挡、预测返回与真机性能仍需专项验收。
详见 [动效实现与边界](docs/motion.md)。

## 开发

- Android Studio；安装 Android SDK Platform 37。
- minSdk 26；应用 ID：com.semiovert.app。
- 使用仓库内 Gradle Wrapper；JDK 要求见 gradle/gradle-daemon-jvm.properties。
- 打开仓库根目录并完成 Gradle 同步，选择设备后运行 app。
- SDK 路径由不提交的 local.properties 配置。

~~~powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
# 已启动的模拟器或已授权的设备
.\gradlew.bat :app:connectedDebugAndroidTest
~~~

## 源码

单 app 模块，包根 com.semiovert.app：navigation 管理页面与返回栈；
core/designsystem 提供主题、共享组件和原创图形；core/model 定义最小模型；
core/data 提供离线数据与非凭据本机偏好；feature 按页面职责分包。

页面视觉依据为 SO.pen 的当前页面和共享组件，归档画板不是实施目标。
封面插画与常规线性图标为代码原创；开屏身份素材沿用设计稿，来源见 [第三方素材说明](THIRD_PARTY_NOTICES.md)。示例内容不对应真实用户。系统字体承担当前排版角色，
未宣称已交付最终品牌字体。

[页面入口、完成范围及边界](docs/page-status.md) · [第一阶段历史记录](docs/phase-1-plan.md)
