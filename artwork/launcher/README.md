# 软件图标原稿

`approved-icon.png` 是确认的原图，保留原有线条、转角、断口和红点位置。

Android 使用 `app/src/main/res/drawable-xxxhdpi/ic_launcher_artwork.png`：从原稿直接导出透明前景，移除展示底板与阴影，保留原图笔画。图片以 864 × 864 像素保存，由 `bitmap` 的 `fill` 重力填入自适应图标前景图层。

`ic_launcher_foreground.xml` 四边各内缩 2.5%，将完整图案和红点统一缩放至 95%。底板为纯白，圆角、圆形和单色图标沿用桌面的自适应图标处理。
