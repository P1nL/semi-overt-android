# Semi-Overt · Android

使用 Kotlin 与 Jetpack Compose 构建的 Semi-Overt 原生 Android 客户端。

默认使用 `https://api.semi-overt.com/api/v1`，和网站共用账户、文章、资料和审核流程。
`preview` 构建与默认界面仪器测试使用独立演示数据，适合离线看页面和动效。

## 可体验的页面

- 首页弧形文章队列、分类、文章与作者搜索；正文支持 Markdown、目录、图片、表格和代码块。
- 分类页按阅读时长展示波浪堆叠，分类标签居中放在大号计数上方；支持反向波浪、阅读返回及分组位置恢复。
- 账号或邮箱登录、保持登录、邮箱验证码注册、找回密码与退出设备会话。
- 本人和作者主页读取真实公开文章与创作日历；本人可编辑昵称、简介、头像和封面。账号名在资料页只读。
- 草稿在本机自动保存并同步云端，支持图片、打开和删除草稿、预览、送审、撤回及退回修改。管理员继续使用 Web 审核。
- 完整设置、明暗主题、主圆位置调整与持久化；长按滑选、外拉固定快捷入口、拖动与收回。
- 创作月历使用服务器每日汇总；开启本地修改记录后，可查看本机成功保存的正文增删并清除快照。

本人点击身份区编辑；作者页只读。阅读返回保留列表位置。
主圆长按滑选不改变背景，深色底色统一 #191a1c。
首页与界面配色沿用 `palette.scss` 的十色色卡，蓝色调整为 `#1F50D5`。浅色暖白底配森林绿，深色炭灰底配薄荷绿；导航按页面使用绿、橙、蓝、紫。
导航位置、主题和封面取景位置保存在本机；头像和封面图片随资料保存上传。

星芒连笔开屏、主圆编排、曲面网格收卷与独立快捷圆已接入；
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

### 启动和动效体验

体验启动速度时使用 `preview`，不要用调试器或 Debug 包判断最终的等待时间：

~~~powershell
.\gradlew.bat :app:assemblePreview
# 安装到已连接的设备；与本机 debug 签名一致，可覆盖安装并保留数据
adb install -r app/build/outputs/apk/preview/app-preview.apk
~~~

Android Studio 可在 Build Variants 中选择 `preview` 后正常 Run（不是 Debug）。
该构建继承 Release 的完整代码 / 资源优化，关闭调试开销，使用本机 debug 密钥签名；
只用于离线体验和性能核对。`debug` 正常启动会连接生产；要写入测试数据，使用独立测试 API。
启动图片和示例数据在 Activity 创建时后台准备，动画各段时长不因构建类型改变。
应用内动画固定按设计时长播放，不跟随系统的非零动画倍率；系统“关闭动画”仍然有效，不修改手机的全局设置。桌面进出应用等系统转场不受此策略控制。
触摸与前景动画期间请求 120Hz；全部停稳 400ms 后释放高刷请求，由系统选档。浏览页背景曲线按 30 fps 播放，离开时在后台停帧，返回后续播。

## GitHub 发包

在仓库 **Settings → Environments → android-release** 中，只允许类型为 **Tag**、匹配 `v*` 的规则。
配置环境 Secrets `ANDROID_KEYSTORE_B64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_PASSWORD`，
以及环境 Variable `ANDROID_KEY_ALIAS`。沿用上一个正式 APK 的签名密钥。

发包时修改 `app/build.gradle.kts` 的 `versionName`，并将 `versionCode` 改为更大的整数，再随本版代码推送到 `main`。
工作流比较推送前后的版本配置；仅在两个条件同时满足时，为推送的提交创建 `v<versionName>` 标签并触发打包。
版本可跳号。常规代码提交或只改一个版本字段不会发包。

仍然可以手动为 `main` 上版本匹配的提交推送标签；例如当前版本为 `1.0.1` 时：

~~~powershell
git fetch origin
git tag -a v1.0.1 origin/main -m "Semi-Overt 1.0.1"
git push origin v1.0.1
~~~

[Android release draft](.github/workflows/android-release.yml) 从版本标签运行单测、构建无签名 APK，再使用环境密钥签名和验签。
成功后在 **Releases** 中下载草稿附件（如 `Semi-Overt-v1.0.1.apk`）进行真机验收，通过后在同一草稿点击 **Publish release**。
工作流不会移动已有标签或覆盖已有 Release 附件。

## 源码

单 app 模块，包根 com.semiovert.app：navigation 管理页面与返回栈；
core/designsystem 提供主题、共享组件和原创图形；core/model 定义最小模型；
`core/data` 管理业务 API、设备会话、本机修改记录及演示数据；`feature` 按页面职责分包。

页面视觉依据为 SO.pen 的当前页面和共享组件，归档画板不是实施目标。
封面插画与常规线性图标为代码原创；开屏字形依据确认的连笔草稿原生绘制，访客身份素材沿用设计稿，来源见 [第三方素材说明](THIRD_PARTY_NOTICES.md)。示例内容不对应真实用户。系统字体承担当前排版角色，
未宣称已交付最终品牌字体。

[服务器接入与联调](docs/live-business.md) · [页面入口与历史验收](docs/page-status.md) · [第一阶段历史记录](docs/phase-1-plan.md)
