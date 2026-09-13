# Pitchee Android

Android 端已集成 [project-pitchee/Pitchee-core](https://github.com/project-pitchee/Pitchee-core)，
使用 Jetpack Compose Material 3 和 MVVM：

- **实时基频**：入口和权限说明已就位，实时检测将在后续版本实现。
- **声音分析**：未出结果时保持极简录音界面；录音键会动画切换为红色停止键，固定幅度波形以约 60 FPS 从右侧进入。界面提供《北风与太阳》等可切换语料；有效语音不足 5 秒时不展示结果。录音结束后可播放、缩放和拖动时间轴，时间级指数使用五档颜色，灰色表示未识别到语音。
- **结果解释**：突出综合分，显示命中的提升/封顶规则、实际压低分数、主要短板和指标拆解。
- **关于**：应用版本、隐私、模型和免责声明。

```text
app/src/main/java/io/rovly/pitchee/
├── data/       Repository、录音、音频解码、强类型结果模型
├── ui/         Compose 页面、RecordViewModel、Material 3 主题
└── MainActivity.kt
```

公开 Kotlin 入口是 `io.rovly.pitchee.data.PitcheeRepository`：

```kotlin
val repository = PitcheeRepository(context)
viewModelScope.launch {
    try {
        val result = repository.analyze(audioUri) // 或 analyzePcm(...)
        println(result.composite.finalScore)
    } finally {
        repository.close()
    }
}
```

## 构建

需要 JDK 17、Android SDK 37、NDK `26.3.11579264`、CMake `3.22.1`。

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

首次构建会从 Maven Central 下载 ONNX Runtime Android AAR，并自动提取构建所需的头文件和 native library。录音分析只申请 `RECORD_AUDIO`，音频不会上传。

详细的集成说明、许可证风险和已遇到问题记录见
[`docs/PITCHEE_CORE_INTEGRATION.md`](docs/PITCHEE_CORE_INTEGRATION.md)。
