# 卡片动画观察台

用当前 Android 界面的逐帧画面观察首页选卡上浮、首页展开与收起、分类页展开与收起，以及上提到边界后的自动接续。播放器支持 0.1×～4×倍速、暂停、16 ms 单帧移动、拖动定位、循环及同步对照。

## 采集与启动

使用已启动的模拟器或专用测试设备。采集规则会重置该设备上的应用离线演示偏好；请在测试设备上执行。

```pwsh
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.semiovert.app.CardExpansionCaptureTest'
adb pull /sdcard/Android/data/com.semiovert.app/files/card-expansion-viewer/. app/build/card-expansion-viewer
node tools/card-motion-viewer/serve.mjs app/build/card-expansion-viewer 5193
```

打开 `http://127.0.0.1:5193`，默认并排播放两处收起。采集使用 Compose 测试时钟，每次推进 16 ms；截图和编码耗时不计入播放。画面宽 540 px，WebP 质量 92，保留原生布局、封面、阴影和文字。首页展开时标题运动 550 ms，正文在第 250 ms 接入，运动 480 ms，整段于 730 ms 完成，采集至 800 ms。首页收起时正文在 0–480 ms 退场，标题在第 250 ms 开始匀速回落，用 550 ms 归位；整段 800 ms，采集至 880 ms。分类点击展开为 1200 ms，另留 64 ms 结束画面；收起为 1100 ms，采集至 1280 ms。收起片段均实际触发系统返回。选卡上浮与取消选卡回落均沿原弹簧路径在 900 ms 完成；上浮采集至 1280 ms，包含头像接续，回落采集至 1008 ms。分类上提片段从 160 dp 边界开始，记录手指仍按住时自动接续的文案减速与背景淡出。

将此目录的四个播放文件和采集目录（命名为 `assets`）放在一起，即可通过 `node serve.mjs` 独立启动。播放器只改变画面播放速度；调整正式动画需修改 Android 源码并重新采集。

## 对应实现

- 首页选卡上浮与回落：`ArcArticleDeck.kt` 的 `expand()`、`ArcDeckMotion.liftSpec()`，以及 `ArcDeckMath.pullLift()`。
- 展开阅读：`ArticleExpansionState`、`ArticleExpansionHost.kt`、`SceneMotion`。
- 播放器显示的阅读进度来自原生界面的 `ProgressBarRangeInfo`；展开从 0 增至 1，收起从 1 降至 0。阶段按钮按各片段的实际方向查找帧；同步开启时，用对应首页或分类阶段的实际时间定位两段，关闭同步后分别定位。
- 两个下拉框均可切换展开与收起；首页还可查看选卡上浮和取消回落，分类可查看上提边界后的动作。边界后的片段会关闭同步，便于独立查看。
- 原生采集同时核对：展开及收起时容器宽高保持固定、文案交接时容器位置固定、首页文案匀速上升及回落、选卡上浮与回落在 900 ms 到位；另覆盖手柄点按及低于边界的快速松手。
