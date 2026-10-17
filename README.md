# 24 Solver (Wakusei)

$24 Solver$，是一个 Android 上的「算 24」计算器：输入若干个数，它给出**全部**解法，并可以只看**本质不同**的那几条。
默认目标是 24，也可以算 10、算 -24、或任意目标。

除了必要的系统 API，它不联网、不收集任何数据、**不含任何广告**，全部计算在本地完成。

- 语言：中文 / English / 日本語（英文兜底）
- 按键音效：运行时合成，无音频素材
- 最低 Android 7.0（API 24），目标 API 37

---

## 功能

### 输入界面

```
TARGET: 24
NUMBERS: 1, 2, 3
INPUT: 4

[ 1 ] [ 2 ] [ 3 ]
[ 4 ] [ 5 ] [ 6 ]
[ 7 ] [ 8 ] [ 9 ]
[+/-] [ 0 ] [NEXT]
[CLEAR][DEL][TARGET]

[     SOLVE      ]
```

| 键 | 作用 |
| --- | --- |
| `0`–`9` | 输入数字（整数，可先按 `[+/-]` 输入负数） |
| `[+/-]` | 切换当前输入的正负号；空输入时按它会开始一个负数 |
| `NEXT` | 把当前输入存为一个候选数（最多 5 个） |
| `CLEAR` | 点按：清空当前模式归属的那一份；长按：全部复位 |
| `DEL` | 点按：退格；长按：三段式（见下） |
| `TARGET` | 切换「编辑目标」模式（拉杆）：再按一次存回目标 |
| `SOLVE` | 求解，进入结果界面 |

- **两个缓冲**：候选数列表（`NUMBERS`）与「正在输入」的缓冲（`INPUT`）。`TARGET` 拉杆打开时，数字键、`CLEAR` 与 `DEL` 编辑的都是**目标输入**而不是候选数。
- **校验**：1–5 个候选数，$|x| < 256$，$|target| \leq 16384$；不合法时在界面上说明原因，不会开始求解。
- 旋转屏幕不丢已输入的内容（存在 `savedInstanceState` 里）。

#### 长按手势

| 手势 | 行为 |
| --- | --- |
| `CLEAR` 点按 | 清空当前作用域的那一份（候选数，或目标回到默认值），**完全静默** |
| `CLEAR` 长按 | 候选数与目标全部复位 + Toast + 轻振动（`LONG_PRESS`） |
| `DEL` 长按 | ① 当前输入不为空 → 清空输入；② 输入为空但已有候选数 → 撤销最后一个；③ 都为空 → 什么都不做（不弹提示、不振动） |

两个长按共用**同一个可配置阈值**（设置页「长按阈值」，100–2000 毫秒，默认 500），因此不依赖平台固定的 `ViewConfiguration` 超时——按住多久算长按由应用自己计时（`LongPressGesture`）。振动走系统托管的 `LONG_PRESS`：无需任何权限，用户关掉系统触感反馈后即静默；「有振动」恰好等价于「动作真的发生了」。
- 已发现并解决的 bug：此前长按**不会**振动。原因有二 —— 请求的是几乎无感的 `KEYBOARD_TAP` 常量，且请求发在窗口根视图上（两个键自己声明了 `hapticFeedbackEnabled="false"`）。现在改为在**被按下的那个键**上请求 `LONG_PRESS`，并让键保持可振动（测试守着这一点）。

### 结果界面

- 列出全部解，每条是一个**括号最少**的表达式。
- 可切换「全部解 / 本质解」：本质解按 `+`/`*` 的交换律与结合律归一（`a-b` 与 `a+(-b)` 这类不合并）。
- 返回再进来，选择仍然保留（存在 ViewModel 里）。
- 解数上千时是单个 `TextView` + 滚动（见「已知取舍」）。

### 按键音效

| 触发 | 声音 |
| --- | --- |
| 15 个键盘键 | 每键一个音，取自当前音集（洗牌袋，每轮以固定开头音起） |
| `SOLVE` | 当前音阶，音与音相隔 120ms **起音**、叠成滚奏，最后一个音拖长到 1.2s |
| 长按 `CLEAR` / `DEL` | 当前琶音，同样叠奏 + 长尾音 |
| 其它按钮 | 不发声 |

- 全部**运行时合成 PCM**（基频 + 两个高次模式 + 衰减包络），项目里没有任何音频文件。
- 六个声部，音符互不打断；一条音阶在**一个缓冲区**内混音并过软限幅，所以只占 1 个声部且不破音；
  单独一个音的波形与合成结果**逐样本一致**。
- 合成在后台线程 + 乐句缓存，按键那一刻没有排队等待。
- 设置里有总开关（默认开）。

### 设置

| 项 | 说明 |
| --- | --- |
| 语言 | 跟随系统 / 中文 / English / 日本語；**离开设置页时**生效（旋转屏幕不算离开） |
| 默认目标数 | 滑杆节点 `CUSTOMIZE` `-24` `-10` `0` `10` `24` `60` + 自定义数字输入 |
| 按键音效 | 总开关 |
| 长按阈值 | 数字输入框，CLEAR 与 DEL 共用，100–2000 毫秒（默认 500）；越界只在输入框报错、不写入 |
| 帮助 | 打开帮助页面 |

默认目标只决定「新开界面从哪开始」与「长按 CLEAR 回到哪」，**不应该改动当前正在使用的目标**。
- 已发现并解决的 bug：此前从设置页返回时会重新读取默认目标，从而把当前目标覆盖掉。原因是「界面被重建」被当成了「新的一次计算」；现在只有本次计算**首次创建**时才读默认值（`FirstFragment.targetInitialised`），重建只重建界面、不重开这局。

### 帮助页面

- 首次启动时**自动打开一次**，之后可从设置再打开。
- 正文来自 `docs/help/*.md`（构建时作为 assets 打包，只有这一份源文件），跟随应用语言。
- 支持的分隔语法只有三种：`# ` 标题、空行分段、`**加粗**`——够用，且不需要引入 Markdown 依赖。

### 外观

- 亮色模式的窗口底色是**灰白** `#F3F3F1`（Material3 的默认纯白 `#FFFBFE` 在整屏大色块按键上偏刺眼），
  定义在 `values/colors-background.xml`；`values-night` 里有对应的深色值，所以跟随系统深色时不会突然变亮。
- 暗色模式**尚未完成**（见 TODO）：主题本身跟随系统，但配色、按键与图标还没有针对深色校对。

### 语言
- 界面文案三语齐全（英文是默认与兜底），**漏翻会让 lint 失败**。
- 帮助页文本三语各一份 `docs/help/help_doc*.md`。
- 默认跟随系统；`AppLocalesMetadataHolderService` 负责 API 33 以下的持久化，API 33+ 交给系统。

---

## 构建与测试

需要 JDK 17+ 与 Android SDK。

```bash
./gradlew.bat :app:testDebugUnitTest          # 单元测试（241 例，Robolectric）
./gradlew.bat :app:lintDebug                  # 必须输出 "No issues found."
./gradlew.bat :app:assembleDebug              # debug APK
./gradlew.bat :app:assembleDebugAndroidTest   # instrumentation APK
./gradlew.bat :app:connectedDebugAndroidTest  # 真机/模拟器上的启动冒烟测试
```

### 测试清单（241 例）

| 测试类 | 覆盖内容 | 数量 |
| --- | --- | --- |
| `solver.SolverTest` | 解数（独立验证）、每解等于目标且每数用一次、去重排序、本质解、与暴力枚举差分、预算与校验 | 15 |
| `solver.RationalTest` | 精确有理数的构造/约分/四则/比较 | 8 |
| `solver.ExpressionTest` | 括号最少化、规范形、打印往返 | 6 |
| `solver.SolverPurityTest` | `solver` 包不引用 `android`/`androidx`（目录与 jar 两种产物都扫） | 1 |
| `FirstFragmentTest` | 布局、按键接线、拉杆、CLEAR/DEL 点按与长按（含自定义阈值、短按仍是点按）、振动常量、校验、求解往返、切换、旋转不丢输入 | 34 |
| `SettingsFragmentTest` | 语言四项与「只记录不生效」、离开设置页才切换、旋转不算离开、预设与自定义、音效开关、长按阈值输入（含越界与回显）、帮助入口 | 19 |
| `LanguageDeferralTest` | 选语言不改当前语言、应用那一步才切换、应用内选择优先、manifest 指向自己的 Application | 8 |
| `settings.SettingsTest` | 默认值、预设取值、±16384 校验、长按阈值默认值与范围、持久化、语言标签匹配 | 10 |
| `DefaultTargetTest` | 新界面从默认目标开始、长按 CLEAR 回到默认、改设置不动当前目标、**从设置页返回后仍不动当前目标**、设置入口与返回 | 8 |
| `KeypadSoundTest` | 15 键发声、一轮用遍音集、音符不截断、音阶起音位置、其它界面不发声音、总开关 | 12 |
| `HelpFragmentTest` | 帮助页渲染标题/段落/加粗、跟随语言、首启自动打开且只一次、设置入口 | 9 |
| `help.HelpMarkdownTest` | 标题/分段/单换行/空行折叠/CRLF/空文档/非标题 `#`/加粗/未闭合标记 | 10 |
| `help.HelpTextTest` | 语言→文件映射、三份文档都打包且可解析、按钮名与界面一致、兜底英文、缺文件返回空 | 10 |
| `KeypadLabelFitTest` | 键盘键内边距 4dp、`maxLines=1`、自动缩小范围、三语文案在 12sp 下放得下 | 7 |
| `BottomControlsTest` | 系统栏 insets 生效、`SOLVE`/`BACK` 落在导航栏之上 | 4 |
| `sound.ToneSynthesizerTest` | 长度、峰值、衰减、起音无爆音、自相关测基频、起音间隔布局、单音逐样本相等 | 13 |
| `sound.SoftLimiterTest` | 膝点以下原样通过、连续、不越界、单调、奇对称、五音叠加 | 8 |
| `sound.PolyphonicAudioSinkTest` | 短音不得写进长音的音轨、等长音复用、起新音不停旧音、占满时复用最快消失的声部 | 9 |
| `sound.VoicePoolTest` | 空闲优先、超时复用、计数与复位 | 9 |
| `sound.PitchTest` | A♭3→F#5 半音列无缺口、拼写别名、频率与八度 | 9 |
| `sound.PhraseCacheTest` | 同乐句只合成一次、容量有界 LRU | 6 |
| `sound.ToneEngineTest` | 一条乐句一次写入、不阻塞调用方、失败被吞、shutdown 释放 | 7 |
| `sound.NoteQueueTest` | 用完才重复、每轮重洗、开头音打头 | 7 |
| `sound.MelodiesTest` | 音集/音阶/琶音逐音对齐、十二平均律 | 5 |
| `SolveResultViewModelTest` | 显示哪一份解集、两组相同时回落、选择保留 | 4 |
| `IconTest` | 图标资源可解析、标记在安全圆内 | 3 |
| `LaunchSmokeTest`（androidTest） | 真机启动并显示输入界面 | 1 |

单元测试共 **241** 例（`LaunchSmokeTest` 是 instrumentation 测试、需要真机，不计入这个数字）。

---

## 目录结构

```
app/src/main/java/com/a24solver/wakusei/
├── MainActivity.kt             # 单一 Activity + Navigation
├── FirstFragment.kt            # 输入界面
├── SecondFragment.kt           # 结果界面
├── SettingsFragment.kt         # 设置界面
├── HelpFragment.kt             # 帮助页面
├── SolveResultViewModel.kt     # 两个界面之间共享解集
├── WakuseiApplication.kt       # 启动时应用语言选择
├── help/                       # 帮助文本的读取与 Markdown 渲染
├── settings/                   # 设置项 + 语言枚举
├── solver/                     # 求解器（纯 Kotlin，无 Android 依赖）
└── sound/                      # 合成、声部、乐句缓存、音高表

app/src/main/res/               # 布局、三语文案、样式、导航图

docs/
├── help/                       # 帮助文本（构建时打包为 assets，唯一来源）
└── README-archive.md           # 旧版 README（设计取舍与历史）

TUTORIAL.md                     # 英文快速上手
```

---

## 已知取舍

- 求解器本身的输入限制为 `1..8` 且 `|x| < 256`；但是在界面进一步限制为 $\leq 5$ 个、$|x| < 256$、$|target| \leq 16384$。
- 这是因为：$n \geq 6$ 时穷尽枚举的输出规模超过内存（n=6 已达 $3.1 \times 10^7$ 条），所以，即使尝试在此情况下工作，也将会在半秒内抛出
  `SolverLimitException`。
- 结果列表是单个 `TextView` + `ScrollView`（未用 RecyclerView），解数上千时内存与滚动会吃紧。
- 音效参数（音长、起音间隔、限幅膝点）是按听感做的细调。
- release 构建的 R8 优化默认关闭：只能靠真机验证，本仓库的验证手段停在「能编译、能过测试」。
- 跨声部叠加的混音由系统完成，应用内软限幅只作用于一条乐句内部。

---

## TODO

- [x] 解决 bug：长按 [CLEAR] 或 [DEL] 的振动不触发（见「长按手势」的说明）
- [x] 解决 bug：在设置中改动默认 TARGET 会连带改掉当前 TARGET（见「设置」的说明）
- [x] 长按阈值可配置：设置页数字输入框，`CLEAR` 与 `DEL` 共用，默认 500、范围 100–2000 毫秒
- [ ] 请母语者校对界面文案与帮助文本（完整性由 lint 与测试保证，用词没有）
- [ ] 答案列表换成 `RecyclerView`
- [ ] 把 `solver/` 抽成纯 Kotlin JVM 模块（`SolverPurityTest` 已守住依赖边界）
- [ ] 在真机上细调音效参数
- [ ] 打开 release 的 R8（`optimization.enable = true`）+ keep 规则，并真机冒烟
- [ ] 帮助页面：考虑加入交互式分步引导（遮罩高亮 + 分步说明）
- [ ] **添加暗色模式支持**：目前主题虽然跟随系统（`Theme.Material3.DayNight`，窗口背景已在 `values-night` 给了深色），但整套配色、按键与图标尚未针对暗色校对，尚未完成
