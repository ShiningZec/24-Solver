# 24 Solver (Wakusei)

一个 Android + Kotlin 的「24 点」求解器：给定一组数字和一个目标值，**穷举出全部可能的解表达式**，
并可以把结果收拢成「本质不同」的解。

- 模块：`:app`（Android Application），包名 `com.a24solver.wakusei`
- 语言 / 工具链：Kotlin（AGP 9 内置 Kotlin 支持），Gradle (KTS)，AGP 9.4 / Gradle 9.6，
  `minSdk 24`，`compileSdk/targetSdk 37`，Java 11（字节码），viewBinding，Material 3
- 应用名（`app_name`）：**24 Solver**；Gradle 根项目名：`24 Solver`
- 文档：
  - 本文件：项目状态、求解原理、界面实现、局限与 TODO
  - [`TUTORIAL.md`](TUTORIAL.md)：面向使用者的简短交互说明书（en-US）
- 文档日期：2026-10-16

---

## 1. 项目介绍

### 1.1 目标

求解器要回答的问题：

> 给定 `n` 个数（`1 ≤ n ≤ 8`，每个数的绝对值 `< 256`）和一个目标值 `target`，
> **把每个数恰好使用一次**，用 `+ - * /` 和任意加括号方式，列出**所有**能得到 `target` 的表达式。

名字叫 24 Solver（默认目标是 24），但 `target` 是入参，任何整数目标都能求解（例如 `target=60`）。
数字在 API 层面允许负数（界面用 `[+/-]` 键输入）。

关键词是「**全部**」：不是「找到一个就返回」，而是**完全枚举**——不剪枝、不启发式，只要不漏解。
运算全程使用自己实现的**精确有理数**（`BigInteger` 支撑），因此 `(1 + 1/9) * 9` 这类依赖分数中间
结果的解不会因浮点误差被漏掉或误判。

界面侧对输入有更严格的限制（见 §3.3）：**候选数最多 5 个**、`|候选数| < 256`（严格小于，与求解器
的 `require` 一致）、`|target| ≤ 16384`。目标值的**默认值**可以在设置里改（见 §3.9）。

界面之外，这一版还带着三件与求解无关、但与「能不能用」有关的东西：

- **按键音效**：十五个按键各发一个木琴音（八个音洗牌轮转），`SOLVE` 与长按手势发音阶；全部是
  运行时**自己合成**的 PCM，不含任何外部音频素材或授权（见 §3.8）。
- **三语界面**：中文 / English / 日本語，英文为默认与 fallback，可在设置里实时切换（见 §3.9）。
- **设置页**：标题栏右上角的三横线按钮进入，含语言、默认目标数、按键音效开关（见 §3.9）。

### 1.2 目录结构

```
Wakusei/
├── README.md                       # 本文件
├── TUTORIAL.md                     # 交互说明书（英文）
├── .github/workflows/android.yml   # CI：单测 + lint + 打包（见 §4.4）
├── tools/reference-solver.js       # 独立的参考实现，产出测试里的期望解数（见附录 B）
├── settings.gradle.kts             # 根项目 "24 Solver"，include(":app")
├── build.gradle.kts / gradle/      # 版本目录 (libs.versions.toml)、wrapper
└── app/
    ├── build.gradle.kts            # 模块配置（viewBinding、依赖、单测 JVM、SDK 版本）
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml        # Application、localeConfig、AppLocalesMetadataHolderService
        │   ├── java/com/a24solver/wakusei/
        │   │   ├── solver/                # ★ 求解核心：纯 Kotlin，不引用任何 android.*
        │   │   │   ├── Rational.kt        # ★ 精确有理数（BigInteger，构造即最简）
        │   │   │   ├── Expression.kt      # ★ 表达式树：求值 / 打印 / 规范形 key
        │   │   │   └── Solver.kt          # ★ 子集 DP、内存预算、两种解集
        │   │   ├── sound/                 # ★ 按键音效（见 §3.8）
        │   │   │   ├── Pitch.kt           # 音高与频率（十二平均律）
        │   │   │   ├── Melodies.kt        # 8 音洗牌队列 + 两条音阶
        │   │   │   ├── ToneSynthesizer.kt # ★ 纯函数：把音高合成成 PCM 波形
        │   │   │   ├── ToneEngine.kt      # 后台线程播放 + AudioTrack 输出（可注入 sink）
        │   │   │   └── SoundEffects.kt    # 门面：playKeypadTone / playSolveScale / playResetScale
        │   │   ├── settings/Settings.kt   # ★ 设置存储（语言 / 默认目标 / 音效开关）
        │   │   ├── FirstFragment.kt       # ★ 输入界面：键盘、校验、后台求解、状态恢复
        │   │   ├── SecondFragment.kt      # ★ 结果界面：答案列表 + 全部/本质不同切换
        │   │   ├── SettingsFragment.kt    # ★ 设置界面（见 §3.9）
        │   │   ├── SolveResultViewModel.kt# 两个界面之间共享的解集与显示选择
        │   │   └── MainActivity.kt        # 壳：NavHost + 40dp 顶栏 + 设置按钮
        │   ├── keepRules/rules.keep       # R8 保留规则（模板，当前 release 未开启 R8）
        │   └── res/                       # values（英文）/ values-zh / values-ja / layout / xml / ...
        ├── test/java/com/a24solver/wakusei/
        │   ├── solver/
        │   │   ├── RationalTest.kt        # 有理数：规范化、四则、比较、不溢出
        │   │   ├── ExpressionTest.kt      # 打印规则、括号最少化、规范形、随机往返
        │   │   ├── SolverTest.kt          # ★ 解数、穷尽性、与暴力枚举差分、预算与校验
        │   │   ├── SolverPurityTest.kt    # 断言 solver 包不引用 android/androidx
        │   │   ├── BruteForceOracle.kt    # ★ 测试用的独立暴力枚举（差分基准）
        │   │   └── ExpressionParser.kt    # ★ 测试用的独立解析器（验证打印可往返）
        │   ├── sound/                     # ★ 波形（长度/峰值/衰减/频率）、洗牌队列、引擎不阻塞
        │   ├── FirstFragmentTest.kt       # Robolectric：键盘、拉杆、手势、校验、结果往返
        │   ├── KeypadSoundTest.kt         # ★ Robolectric：哪些按键发哪个音、关掉后是否真的静音
        │   ├── SettingsFragmentTest.kt    # ★ Robolectric：语言 / 默认目标 / 音效开关的行为
        │   ├── DefaultTargetTest.kt       # ★ 默认目标从哪来、设置按钮与返回行为
        │   ├── BottomControlsTest.kt      # Robolectric：系统栏 insets 与底部按钮不被导航栏遮挡
        │   ├── SolveResultViewModelTest.kt# 结果界面的显示规则与选择保持
        │   ├── settings/SettingsTest.kt   # 设置存储：默认值、持久化、校验
        │   └── IconTest.kt                # 图标资源与 66dp 安全区几何
        └── androidTest/java/…/LaunchSmokeTest.kt   # 真机冒烟：应用能启动（见 §4.3）
```

求解逻辑（`solver/`）不引用任何 `android.*` 类，与界面完全解耦，因此可直接用 JVM 单测驱动；
这一点由 `SolverPurityTest` 扫描编译产物的常量池来**强制**保证，而不是只写在文档里。

### 1.3 已实现 / 未实现

| 部分 | 状态 |
| --- | --- |
| 精确有理数（`BigInteger`，构造即最简，永不静默溢出） | ✅ 实现并有单测 |
| 子集 DP 穷尽枚举（不剪枝、不漏解），精确命中判定 | ✅ 实现并有单测 |
| 「全部表达式」与「本质不同解」两种输出 | ✅ 实现并有单测 |
| 最小括号打印（打印结果可被独立解析器还原成同一棵树） | ✅ 实现并有单测 |
| 与独立暴力枚举的差分测试（n ≤ 5，随机输入） | ✅ 实现 |
| 输入界面：数字键盘、`[+/-]`、`DEL`、`NEXT`、`CLEAR`、`TARGET` 拉杆 | ✅ 实现并有测试 |
| 调用前校验（≤5 个候选数、`|x| < 256`、`|target| ≤ 16384`） | ✅ 实现并有测试 |
| 后台求解（`Dispatchers.Default` + `lifecycleScope`），求解中禁用按钮 | ✅ 实现并有测试 |
| 结果界面：答案列表、全部/本质不同切换、`BACK` | ✅ 实现并有测试 |
| 长按手势：`CLEAR` 全部复位、`DEL` 撤销最后一个候选数（系统级轻振动） | ✅ 实现并有测试 |
| 旋转 / 配置变更后输入不丢失 | ✅ 实现并有测试 |
| 按键音效：15 键洗牌轮转、`SOLVE` 与长按的音阶、6 声部复音不互相打断、后台播放不阻塞界面 | ✅ 实现并有测试（见 §3.8） |
| 三语界面：中文 / English / 日本語，英文为默认与 fallback，可实时切换 | ✅ 实现并有测试（见 §3.9） |
| 设置页：语言、默认目标数（滑杆节点 + 自定义输入）、音效总开关 | ✅ 实现并有测试（见 §3.9） |
| 真机 / 模拟器仪器测试 | ⚠️ 只有启动冒烟测试（见 §4.3），本机无设备可跑 |
| 真机上听感调校（音量、音色、音符时长） | ⚠️ 参数是按听感选的理论值，未在真机上听过 |
| n ≥ 6 的完全枚举 | ❌ 解空间本身过大，求解器会**快速报错**而不是卡死（见 §5.2） |
| 答案列表换成 `RecyclerView` | ❌ 仍是单个可选中 `TextView`（见 §5.3） |
| detekt / ktlint | ❌ 只跑 Android Lint |

---

## 2. 求解原理

### 2.1 一句话概括

```
解集 = { e : e 是一棵以输入数字为叶子的二叉树，每个数恰好用一次，运算符 ∈ {+,-,*,/}，
        求值(e) == target }
```

求解器不再「树形 × 运算符 × 排列」三重循环，而是对**输入位置的下标集合**做子集动态规划：

```
values[mask] = { 表达式 : 该表达式恰好用掉 mask 中那些位置的数字 }
```

`mask` 由「任意分成两个非空子集」的方式组合而成，于是每棵树只被构造一次，公共子结果被复用。
搜索空间从 `Catalan(n-1) · 4^n · n!` 降到约 `3^n` 级别的「切分 × 取值」组合（见 §2.5）。

### 2.2 精确有理数 `Rational`（`solver/Rational.kt`）

不可变、**构造即最简**：分母恒正、符号放在分子、`0` 规范成 `0/1`，于是 `equals` 就是数值相等，
`HashMap<Rational, _>` 可以直接作为 DP 的记忆表。提供四则运算（除零返回 `null` 而不是抛异常，
因为「这条分支不成立」是搜索的正常情况）、`Comparable`、`toString`（分母为 1 只打印整数）。

用 `BigInteger` 而不是 `Long` 是刻意的：本项目允许 `n = 8` 且 `|x| < 256`，而
`255^8 = 17878103347812890625`，作为有符号 64 位整数会回绕成 `-568640725896660991`。回绕不是
「精度差一点」，而是会**凭空造出解或吃掉解**——对承诺「全部解」的枚举器来说不可接受。
（7 个数以内 `Long` 其实够用：`255^7` 只要 56 位；修正只影响输入范围顶端，代价也很小。）
`RationalTest.arithmeticDoesNotWrapAroundWhereLongWould` 把这条差异固定成了测试。

### 2.3 表达式 `Expression`（`solver/Expression.kt`）

```kotlin
sealed interface Expression {
    fun evaluate(): Rational      // 精确求值
    fun render(): String          // 最小括号打印
    fun canonicalKey(): String    // + / * 的交换律 + 结合律归一后的 key
    data class Literal(val number: Int) : Expression
    data class Binary(val left: Expression, val right: Expression, val operator: Operator) : Expression
}
```

**打印规则（括号最少化且无歧义）**：子节点需要括号，当且仅当

- 子节点优先级 < 父节点优先级（`(1 + 2) * 3`），或
- 子节点是**右**操作数且优先级相等（`1 - (2 - 3)`、`6 / (2 * 3)`）。

左操作数同等优先级不加括号（`1 + 2 + 3`、`6 / 2 * 3`），因此打印结果用标准的左结合优先级规则
就能唯一地解析回原来那棵树。负数叶子一律写成 `(-7)`，所以 `2 - (-7)`、`3 * (-2)` 不会产生
`2 - -7` 这种要靠猜的写法。

**规范形 `canonicalKey`**：对 `+` 和 `*`，把同级链条摊平后按 key 排序，于是
`(12 + 13)` 与 `(13 + 12)`、`((1 + 2) + 3)` 与 `(1 + (2 + 3))` 得到同一个 key。
减法和除法**不做**代数改写（不把 `a - b` 折成 `a + (-b)`），因此「本质不同」只会合并用户也会
认为相同的写法，不会把看起来不同的解法吞掉。

### 2.4 枚举器 `Solver`（`solver/Solver.kt`）

```kotlin
val outcome = Solver.solve(target = 24, numbers = listOf(1, 2, 3, 4))
outcome.allSolutions        // 242 条：每一个不同的表达式字符串
outcome.essentialSolutions  //  28 条：每个交换/结合等价类一条代表
```

- DP 按 mask 从小到大推进；每个 mask 遍历其**无序**二分（`leftMask < rightMask`），
  在这一次里把 4 种运算符的**两个操作数顺序都生成**（`a - b` 与 `b - a` 都是解，
  `a + b` 与 `b + a` 是两个不同表达式），所以每棵树仍然恰好生成一次。
- 每个 `(mask, 取值)` 桶按**打印字符串**去重：两棵打印相同的子树在更大的表达式里可互换，
  丢掉重复的不会丢解（差分测试验证了这一点）。
- 除零在这里被丢弃，这是整个求解器里唯一会出现「运算未定义」的地方。
- 排序：两个列表都按（长度，字典序）升序，即**最简单的解排在最前面**。
- 「本质不同」的代表取所在类里最短、其次字典序最小的那一条。

### 2.5 搜索空间与实测性能

| n | 旧实现循环次数 `Catalan(n-1)·4^n·n!` | 新实现：DP 的「切分 × 取值」组合 |
| --- | --- | --- |
| 2 | 32 | 1 |
| 3 | 768 | 6 |
| 4 | 30,720 | 25 |
| 5 | 1,720,320 | 90 |
| 6 | 123,863,040 | 301 |
| 7 | 10,899,947,520 | 966 |
| 8 | **1,133,594,542,080** | 3,025 |

（上表右列是**切分组合数**；真实代价还要乘以每个 `(子集, 取值)` 桶里的表达式条数，
所以它不是「快了 10^9 倍」，而是「不再重复计算公共子表达式」。）

实测（桌面 JVM，`solver` 包，含全部中间结果）：

| 输入 | 耗时 | 中间表达式条数 | 输出 |
| --- | --- | --- | --- |
| n=4 `1,2,3,4 → 24` | 约 50 ms | 8,476 | 242 全部 / 28 本质 |
| n=5 `1,2,3,4,5 → 24` | 约 250 ms | 468,309 | 2,258 全部 / 273 本质 |
| n=5 `1,1,9,9,6 → 60` | 约 100 ms | 124,375 | 26 全部 / 2 本质 |
| n=5 五个互不相同的数（最坏） | 约 260 ms | 470,485（上界） | — |
| n=6 及以上 | ≲ 0.5 s 后抛 `SolverLimitException` | 超过预算 | — |

DP 保存的是**表达式**而不只是取值，所以内存比解数增长得快得多：n=6 只看顶层 mask 就有
`Catalan(5)·4^5·6! = 30,965,760` 条表达式。把预算放宽到 4,000,000 条时，JVM 会在 13–34 秒后
以 `OutOfMemoryError` 倒下。因此 `MAX_STORED_EXPRESSIONS = 500_000`：

- 界面允许的最坏输入（5 个互不相同的数字）需要 `Σ C(5,k)·Catalan(k-1)·4^(k-1)·k! = 470,485` 条，
  预算刚好覆盖（`SolverTest.theBudgetIsAboveEveryInputTheInputScreenAccepts` 用公式断言了这一点）；
- 6 个及以上的数字会在半秒内抛出 `SolverLimitException`，错误信息说明是「太大，无法穷尽枚举」，
  而不是挂起或崩溃。

### 2.6 解集的冗余度（实测）

| target | 数字 | 全部表达式 | 本质不同 |
| --- | --- | --- | --- |
| 24 | 1, 2, 3, 4 | **242** | **28** |
| 24 | 6, 6, 6, 6 | 7 | 3 |
| 24 | 5, 5, 5, 1 | 2 | 1 |
| 24 | 23, 23, 23, 23 | 2 | 1 |
| 24 | 3, 3, 8, 8 | 1 | 1 |
| 60 | 1, 1, 9, 9, 6 | 26 | 2 |
| 0 | 1, 1, 1, 1 | 66 | 45 |
| 10 | -5, 2, 3 | 8 | 5 |
| 24 | 1, 1, 1, 1 / 7, 7, 7, 7 | 0 | 0 |

`1,2,3,4 → 24` 的 242 条里，按 `+`/`*` 的交换律与结合律归一后只剩 **28** 个本质不同的解——
约 88% 是同一种解法的书写变形。上表每一行的两个数字都由**独立实现**验证过（§附录 B），
并且被写进了 `SolverTest.reportsTheIndependentlyVerifiedSolutionCounts`。

---

## 3. 界面与交互实现

（面向使用者的说明见 [`TUTORIAL.md`](TUTORIAL.md)；这里记录实现细节。）

### 3.1 键盘与尺寸

```
TARGET: 24 / SOLVING…         ← text_target（目标值 / 求解中）
NUMBERS: 1, 2, 3              ← text_numbers（已提交的候选数）
INPUT: 12 / TARGET INPUT: 12  ← text_current_input（当前缓冲 + 当前模式）

[   1   ] [   2   ] [   3   ]
[   4   ] [   5   ] [   6   ]
[   7   ] [   8   ] [   9   ]
[ +/−  ] [   0   ] [ NEXT  ]
[ CLEAR ] [ DEL  ] [TARGET ]
[         SOLVE            ]
```

- 每个键有 `4dp` 的左右外边距（`layout_marginStart/End`）形成间隙；按键文字 `20sp`
  （`values/styles.xml` 的 `Widget.App.KeypadButton`）。数字键的文案来自 `@string/digit_*`
  （`translatable="false"`：键盘输入的是解析器接受的 ASCII 数字，不应跟随地区数字形状）。
- **按键文字不换行、也不会被裁掉**（`KeypadLabelFitTest` 守着）：
  - 一个键只有约 100dp 宽（屏宽的三分之一），而 Material 按钮样式在**左右各花 24dp 内边距**
    ——留给文字只剩约 53dp。`TARGET` 在 20sp 下要约 82dp、`クリア` 约 60dp，所以英文的
    CLEAR/TARGET 与日文的クリア 会换行；实测把内边距改回默认值的话，**即使把字号缩到 12sp，
    `TARGET` 仍然放不下（会被裁切）**，所以瓶颈是内边距，不是可用宽度。
  - 因此键盘键的内边距收到 **4dp/侧**（文字区 53dp → 93dp，上面几个词在 20sp 下全部放得下），
    并加 `maxLines=1` 禁止换行；再用**自动缩小**（12–20sp，1sp 步进）作为兜底——系统字号放大
    或屏幕更窄时，文字缩小而不是被裁掉。`SOLVE` 是整行宽按钮，保留 Material 默认内边距。
  - Robolectric 无法用真实字体度量文字宽度，所以测试用的是**保守估算**（拉丁字母 0.75em、
    CJK/假名 1em）；它能精确断言的是那些让文字放得下的布局属性，以及「缩到最小 12sp 时最长的
    标签仍然放得下」（即永远不需要裁切）。
- 键盘整体包在 `@id/keypad` 容器里，权重 4；其下是 `@id/keypad_spacer`（权重 1）。因此键盘区
  高度恰为原先行区空间的 **80%**。该容器内是 5 行等权重的 `LinearLayout`——即「权重套权重」，
  这是表达「5 行等分固定的 80% 高度」最直接的方式，故在布局里对 `NestedWeights` 做了**带说明的
  局部抑制**（层次固定为 5 行、只多一次测量，代价可忽略）。
- `SOLVE` 固定 `@dimen/action_button_height`（56dp，默认 wrap_content 约 48dp，即约 +20%），
  其下再留 `@dimen/bottom_action_clearance`（56dp，约一个按钮高）的空隙；结果界面的 `BACK`
  用同一对尺寸。**这是为了解决「底部的按钮太贴近屏幕下缘、在部分机型上会被系统导航键挡住」**：
  系统栏 insets 已经保证按钮不会被压在导航栏底下（见 §3.6），这 56dp 是在此之上留出的可点按余量
  ——导航栏较高的机型（平板任务栏、横屏挖孔、大号系统栏）也不会让按钮贴着那条边界。
- 特殊键（`[+/-] [NEXT] [CLEAR] [TARGET] [DEL]`）用 `Widget.App.KeypadButton.Special`：
  `backgroundTint` 指向 `res/color/button_rose_tint.xml`（默认玫红 `#D81B60` / 拉杆激活 `#AD1457` /
  禁用淡玫红），白字。
  - ⚠️ 必须用 `app:backgroundTint` 或样式里的 `backgroundTint`，**`android:backgroundTint` 会被
    MaterialButton 忽略**（主题色会赢）。
- 顶栏：`AppBarLayout` 固定 `40dp`（原 `?attr/actionBarSize` 56dp），`MaterialToolbar` 加
  `minHeight=0dp`；承载标题与返回箭头。

### 3.2 输入模型：两个缓冲 + TARGET 拉杆

- 两个独立缓冲：`numberInput`（候选数）与 `targetInput`（目标值），数字键编辑的是「当前模式」的
  那个，互不干扰。
- `[TARGET]` 是**拉杆**（`MaterialButton` + `android:checkable`，点击后自身保持选中）：激活后键盘
  改写目标值，**输入框不预填**（保持 `_`，标签仍显示生效中的目标）；退出时若输入非空且非 `-` 就
  **写回**目标值，超范围则拒绝并保持激活（输入不丢）。再按一次即取消。
- `NEXT` 提交当前缓冲（候选数或目标值），提交后清空缓冲。
- `DEL` 点按 = 退格；`[+/-]` 对当前缓冲取反（空缓冲时按它会开始一个负数）。
- **配置变更不丢输入**：`onSaveInstanceState` 保存候选数、目标值、两个缓冲与拉杆状态，
  `onViewCreated` 恢复（恢复拉杆时用 `restoringTargetLatch` 挡住监听器，避免它清掉刚恢复的缓冲）。
  对应的测试会真的旋转一次：`controller.recreate()`。

### 3.3 调用前校验

`FirstFragment.validate()` 在调用求解器**之前**执行（录入时也会即时校验）：

| 规则 | 常量 | 失败时 |
| --- | --- | --- |
| 候选数个数 ≤ 5 | `MAX_NUMBERS = 5` | Toast「At most 5 candidate numbers are allowed」 |
| 每个候选数 `|x| < 256`（严格） | `MAX_ABS_NUMBER = 256` | Toast「Each candidate number must satisfy |x| < 256」 |
| 目标值 `|target| ≤ 16384` | `MAX_ABS_TARGET = 16384` | Toast「The target must be within ±16384」 |
| 至少一个候选数 | — | Toast「Add at least one number …」 |

按 `SOLVE` 时若缓冲里还有未提交的数字，会先自动提交——保证「校验的就是屏幕上看到的」。
第 1、3 条用 `<plurals>` 提供单复数正确的英文文案（中文用 `values-zh` 的 `other` 形式）。
该严格上界与 `Solver` 的 `require { |x| < 256 }` 一致，不会出现「界面通过、求解器拒绝」的矛盾。

### 3.4 长按手势与按键振动

| 手势 | 行为 |
| --- | --- |
| `CLEAR` 点按 | 只清当前模式归属的那一份（目标模式 → 目标回 24；否则清候选数），**完全静默** |
| `CLEAR` 长按 | 候选数与目标全部复位，Toast「All Numbers Set to Default」+ 轻振动 |
| `DEL` 长按 | 三段式：①当前输入不为空 → **清空当前输入**；②输入为空但已提交候选数 → **撤销最后一个候选数**；③两者都空 → **什么都不做**（无 Toast、无振动）。①②会轻振动 |

- 阈值 `x = 500 ms`：使用平台自身的长按超时（`ViewConfiguration.getLongPressTimeout()`，标准
  Android 上就是 500 ms），所以 `FirstFragment.LONG_PRESS_MS` 是**说明性常量**而非强制值。
  让 `x` 可配置是 P2 TODO。
- 振动采用 `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`：**弱**、无需任何
  权限（没有 `VIBRATE`、不直接使用 Vibrator），并且由系统设置托管——用户关闭「触感反馈 / 振动与
  触感」后即静默。键盘上的 `android:hapticFeedbackEnabled="false"` 用来关掉平台自己在长按时发出的
  那一记通用震动，因此「有轻振动」恰好等价于「动作真的发生了」（`DEL` 长按的 ①② 两种情形都算）。

### 3.5 求解与结果界面

```kotlin
solveJob = viewLifecycleOwner.lifecycleScope.launch {
    val outcome = withContext(Dispatchers.Default) {
        runCatching { Solver.solve(targetSnapshot, candidates) }
    }
    …
}
```

求解期间标签显示 `SOLVING…`、`SOLVE` 禁用；完成后把 `SolveOutcome` 交给 activity 级
`SolveResultViewModel`（不用 navigation arguments：答案可能有上千条，arguments 会写进 saved
state，必须保持小体积）并 `navigate(action_FirstFragment_to_SecondFragment)`。

结果界面（`SecondFragment` + `fragment_second.xml`）：

- 顶部标题按 `<plurals>` 给出「N answers / N essentially different answers …」；
- 其下是 **`All (242)` / `Essential (28)` 二选一开关**（`MaterialButtonToggleGroup`），两个列表
  在求解时一次算好，切换只是换一个列表来渲染，瞬时完成；当两组相同（只有 1 条解或无解）时开关
  **隐藏**——不提供无法改变任何东西的控件；
- 答案列表是可滚动的等宽可选中 `TextView`，一行一条、带序号，按「简单优先」排序；
- 底部通栏 `BACK` → `navigateUp()` 回到键盘。
- 无解时显示「No expression built from these numbers reaches the target.」；求解器抛异常
  （`IllegalArgumentException` / `SolverLimitException`）时用 Toast 报告并恢复标签，不崩溃。

按 `BACK` 再返回结果界面时，选择仍然保留（选择状态存在 ViewModel 里，rotate 亦然）。

### 3.6 窗口 insets 与底部按钮

`enableEdgeToEdge()` 让窗口铺满整屏（连导航栏区域也画进去），因此**应用必须自己**把内容让开系统栏。
这件事在 `MainActivity` 里显式做，一次覆盖两个界面：

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
    insets
}
```

布局里**刻意不写** `android:fitsSystemWindows`。曾经的写法正是只依赖这个开关，结果是：顶栏被状态栏
内边距推了一次（但现在连监听器都没有，所以那次是「只推一次」），而**屏幕底部完全没有让开导航栏**
——`SOLVE` / `BACK` 因此落在系统导航键的可触区域内，在导航栏较高的机型上很难按下。两个机制同时用
又是另一个极端：顶部被推两次。

所以现在的分工是：**insets 保证按钮永远不在导航栏底下，`@dimen/bottom_action_clearance`
（56dp ≈ 一个按钮高）保证按钮离屏幕下缘有舒服的一段距离**（见 §3.1）。

`BottomControlsTest` 把这条约定钉住了：它向根视图派发一份合成的系统栏 insets（状态栏 24dp、
导航栏 96dp——故意比那 56dp 的余量更高，否则光靠余量也能「碰巧通过」，测不出 insets 有没有生效），
然后断言根视图的内边距、两个布局都没有自己 fit system windows、并且 `SOLVE` / `BACK` 的底边确实落在
导航栏顶边之上。把 inset 那行代码删掉，这个测试会失败 3 条——它是有牙齿的。

### 3.7 没有广告

这个应用**不接入任何广告**，并且把先前「先留着广告依赖」的做法也一并去掉了：`play-services-ads`
`@string/admob_app_id` 全部删除，`AdsTest` 也随之删除。

这不仅是「少一个功能」，还顺手消掉了一整类启动崩溃风险：`play-services-ads` 会把
`MobileAdsInitProvider` 合并进 manifest，而该 provider 在 `ActivityThread.handleBindApplication`
阶段（**早于 `Application.onCreate`**）运行，缺少 app ID 时抛 `IllegalStateException`，表现为

```
RuntimeException: Unable to get provider com.google.android.gms.ads.MobileAdsInitProvider
```

——这种崩溃**无法用应用代码 try/catch**，只能靠 manifest 配置正确来避免。依赖既然不在了，这条
路径、以及 Google Mobile Ads SDK 带来的组件与权限合并，就都不存在了。

实测（删除后）：合并后的 manifest 里不再有任何 ads provider / meta-data，**debug APK 从
12,402,202 字节降到 7,606,755 字节（约 −39%，−4.8 MB）**。
### 3.8 按键音效

**全部是运行时合成的**（`sound/ToneSynthesizer.kt`）：基频 + 两个高次模式（真实音条大约调在
基频的 4 倍与 10 倍上，高次模式衰减更快）叠上瞬态击发与指数衰减包络，直接算出 PCM。项目里
**没有任何音频文件**，所以既不存在采样素材的授权问题，APK 也不会因此变大。

| 触发 | 声音 |
| --- | --- |
| 第一页 15 个键（`0-9` `[+/-]` `NEXT` `CLEAR` `DEL` `TARGET`） | 每键一个音，取当前音集里的下一个 |
| `SOLVE` | 当前音阶（C, Eb, F#, G, Bb），音与音**相隔 120ms 起音**、互相叠着响，最后一个音拖长到 1.2s |
| 长按 `CLEAR` / 长按 `DEL` | 当前琶音（G, D, B），同样是叠起来的滚奏 + 长尾音 |
| 第二页的所有按钮、标题栏按钮 | 不发声 |

- **音符之间互不打断**：一声 750ms，音阶音 450ms，都长到在下一个音响起时仍然在响，并且它们
  **叠在一起自然衰减**，而不是被下一个音掐掉——音尾衰减到峰值的几个百分点再淡出，所以也不会
  「啪」地断掉。分配声部的是 `VoicePool`：优先用空闲声部；六个都占了就复用**最快消失的那一个**
  （丢最老的尾音，而不是丢刚弹的那个音）。
- **音阶的「GAP」是起音间隔，不是静音间隔**：`Phrase.onsetMs` 表示相邻两个音**起音**之间隔多久
  （当前 120ms），而每个音仍然响 450ms —— 于是约四个音同时在响，听起来是**滚奏/合奏**而不是
  一个一个蹦。整条音阶在这一个缓冲区里就混好了，**只占 1 个声部**（另外 5 个留给键盘，弹音阶时
  按键不会挤掉音阶音），而且时间**采样级精确**。
- **叠加不会削波**：混音后过一道 `SoftLimiter`——膝点（0.7 满刻度）以下**原样通过**，所以单独一个
  按键音与合成出来的一模一样；越过膝点后平滑压向满刻度，所以五个音同时到峰值也只是被压扁而不会
  破音。`SoftLimiterTest` 把「膝点以下逐样本相等」和「永远不超过 ±1」都钉住了。
- **绝不延后触发**：`ToneEngine.play()` 只把工作丢给后台线程就返回，合成结果由 `PhraseCache`
  缓存（应用只反复播放固定的几条乐句），所以按键那一刻只是查表 + 启动声部，没有排队等待。
  `ToneEngineTest` 用一个每次睡 400ms 的假 sink 证明调用方在 100ms 内返回，并证明第二段不用等
  第一段响完才开始。
- 那 15 个键共用**一个洗牌袋**（`NoteQueue`）：每个音各响一次之前不会重复，响完自动重洗；
  袋子里还有一条「每轮的开头音」（`KEYPAD_LEADING_NOTE`，当前是 C4），它不参与洗牌，所以既保证
  每轮都以它开头、又不会在一轮里出现两次。`KeypadSoundTest` 会把一轮按键全部按一遍，再逐段比对
  波形，确认每个音都到位。
- 音高表 `Pitch` 是 **A♭3 → F#5 的完整半音列**，按音高升序，`Pitch.entries` 就是整条半音阶。
  想换音阶或让某个键只发某一个音，只改 `Melodies` 里那几个常量（比如把 `RESET_SCALE` 换成
  `listOf(Pitch.F4)`），别处都不用动。表里**允许同一个音高有两种拼写**（`G_SHARP4` 与 `A_FLAT4`
  是同一个键），这样写音阶时可以用自己习惯的拼法；**未被引用的音不要删**（见 `Pitch` 的注释，
  `PitchTest` 会拦住删掉音导致半音列出现缺口）。
- 失败一律吞掉：没有可用音频设备时按键照常工作，只是没声音。
- 音量走**媒体**流（`USAGE_MEDIA`），因此受媒体音量与勿扰控制；静音模式下的表现取决于系统设置
  （见 §5.3）。
- 一个总开关在设置页（§3.9），默认打开。

### 3.9 设置页与语言

标题栏**最右**是一个三横线按钮（`drawable/ic_menu.xml`，`android:tint="?attr/colorOnSurface"`，
即跟随主题前景色：浅色主题下是深色、深色主题下是浅色）。点它**直接进入设置页**，两个界面都能进；
已经在设置页时再点不会叠出第二份（`MainActivity` 里做了目的地判断）。设置页是导航图里的第三个
目的地，所以返回箭头会自动出现，并且**回到打开它的那个界面**。

设置页三项：

| 项 | 行为 |
| --- | --- |
| **语言** | 下拉框：跟随系统 / 中文 / English / 日本語；选中**只记录**，**离开设置页时**才生效 |
| **默认目标数** | 滑杆 7 个节点：`CUSTOMIZE`、`-24`、`-10`、`0`、`10`、`24`、`60`；选 `CUSTOMIZE` 时启用下方数字输入 |
| **按键音效** | 总开关，默认开 |

- **语言切换的时机是「离开设置页」，不是「选中那一刻」**：
  - 选择时只写进设置（`Settings.requestLanguage`），**不调用** `setApplicationLocales`，
    所以设置页不会在用户手指底下重建；列表下方常驻一行「当前使用 / 离开本页后」，
    选择时再弹一个 Toast 说明；
  - 真正的切换发生在 `SettingsFragment.onDestroyView`（`Settings.applyRequestedLanguage`）：
    返回箭头或系统返回键离开设置页时就生效，正好回到界面 1；**旋转屏幕不生效**——旋转同样会
    销毁视图，所以用 `isChangingConfigurations` 把它排除掉（规则抽成 `LanguageApplyRule`，有单测）；
  - 另外在 `WakuseiApplication.onCreate` 也应用一次作为兜底：如果在设置页里进程被杀，这次选择
    不会丢；
  - 语言以**应用内的选择为准**：在 Android 自己的「按应用设置语言」里改的语言会立刻生效、并如实
    显示为「当前使用」，但下一次应用（离开设置页 / 启动）会以应用内的选择覆盖它；
  - 持久化由系统（API 33+）或 AppCompat 的 auto-store 服务负责（manifest 里声明了
    `AppLocalesMetadataHolderService`）；API 33+ 还通过 `android:localeConfig` 把这三个语言
    暴露给系统的语言设置。
  - 三套字符串必须齐全（英文在 `values/`，是默认与 fallback），**漏翻会直接让 lint 失败**。
- 默认目标数用「预设节点 + 数字输入」的组合：预设节点覆盖常用值，其中两个是负数——这就是让用户
  **立刻看到目标可以是负数**的方式；`CUSTOMIZE` 是进入数字输入的开关（选中前输入框是禁用的）。
  输入按 ±16384 校验（与目标输入同一规则），非法值只在输入框上报错、**不写进设置**；空字符串
  在编辑过程中不算错误（失焦才提示）。
- 设置**不会改动当前正在使用的目标值**：它只决定「新开的界面从哪开始」和「长按 CLEAR 回到哪」。
  `DefaultTargetTest` 用两条测试分别钉住了这两件事，以及「改了设置不碰当前目标」。

---

## 4. 测试与构建

### 4.1 测试清单

| 测试类 | 覆盖内容 | 数量 |
| --- | --- | --- |
| `RationalTest` | 构造/约分/符号、四则、除零、精确比较、`Long` 会回绕的算例 | 8 |
| `ExpressionTest` | 括号最少化规则、负数叶子、规范形、随机树打印往返、求值一致 | 6 |
| `SolverTest` | 解数（独立验证）、每个解真的等于目标且每个数用一次、去重与排序、本质解每类一条、与暴力枚举差分、预算与输入校验 | 15 |
| `SolverPurityTest` | `solver` 包编译产物不引用 `android`/`androidx` | 1 |
| `FirstFragmentTest` | 键盘布局/尺寸/配色、按键接线、拉杆与写回、CLEAR 点按与长按、DEL 长按的三段式（清输入 → 撤候选 → 什么都不做）、校验、后台求解与结果往返、全部/本质切换及其旋转保持、旋转不丢输入（Robolectric） | 32 |
| `KeypadSoundTest` | 15 个键各发一个音、一轮用遍音集且以开头音起、音符不被截断、音阶每个音都在自己的起音位置出现、第二页与切换按钮不发声、关掉开关后彻底静音（Robolectric + 假 sink） | 12 |
| `LanguageDeferralTest` | 选语言不改当前语言、选择留存、应用那一步才切换、切回跟随系统能生效、应用内选择优先于系统侧设置、manifest 指向自己的 Application | 8 |
| `SettingsFragmentTest` | 语言下拉的四项与「只记录不生效」、**离开设置页才切换**、旋转不算离开、状态行与 Toast、预设节点、`CUSTOMIZE` 启用输入、非法值只报错不落盘、空值不算错、音效开关、回显已存值（Robolectric） | 15 |
| `DefaultTargetTest` | 新界面从默认目标开始、长按 CLEAR 回到默认目标、改设置不影响当前目标、设置按钮进入设置页/不叠加/返回原界面 | 7 |
| `settings.SettingsTest` | 默认值、预设节点取值、`CUSTOMIZE` 与自定义值、±16384 校验、持久化、语言标签与前缀匹配、「请求语言不改变当前语言」 | 8 |
| `sound.ToneSynthesizerTest` | 长度、峰值不削波、衰减到几乎无声、起始无爆音、**自相关测出基频与音高一致**、静音、起音间隔布局、叠加混音不越界、单音乐句与单音逐样本相等 | 13 |
| `sound.SoftLimiterTest` | 膝点以下逐样本原样通过、膝点处连续、永远不超过满刻度、单调、奇对称、五音叠加仍不破音 | 8 |
| `sound.PolyphonicAudioSinkTest` | **短音不得写进长音的音轨**（旧音阶不得复现）、等长音复用音轨、起新音不停旧音、占满时复用最快消失的声部、release 释放全部 | 9 |
| `sound.PitchTest` | 半音列从 A♭3 到 F#5 无缺口且升序、拼写别名是唯一允许的音高重复、每步正好一个半音、频率对上标准表、八度正好翻倍、音名拼写、2200 音分 | 9 |
| `sound.VoicePoolTest` | 优先用空闲声部、占满时丢最快消失的尾音而不是刚弹的音、超时后复用、计数与 reset | 9 |
| `sound.PhraseCacheTest` | 同一乐句只合成一次、不同乐句分别缓存、键包含每个参数、容量有界且 LRU | 6 |
| `sound.NoteQueueTest` | 用完才重复、每轮重洗、开头音每轮打头且不重复、开头音也在袋里时不会被洗两次、`reset`、空队列被拒 | 7 |
| `sound.ToneEngineTest` | 一条乐句一次写入、同一乐句只合成一次、每次调用都发声、**play() 不阻塞调用方**、第二段不等第一段响完、音频设备失败被吞、shutdown 释放 | 7 |
| `sound.MelodiesTest` | 当前音集/音阶/琶音逐音对齐、开头音不在袋里、音高符合十二平均律 | 5 |
| `SolveResultViewModelTest` | 显示哪一份解集、两组相同时回落到「全部」、选择随 ViewModel 保留 | 4 |
| `KeypadLabelFitTest` | 键盘键内边距 4dp（含 resolve 后的 left/right）、`maxLines=1`、自动缩小开启且范围正确；中/英/日文案在最小 12sp 下都放得下（保守字宽估算） | 7 |
| `BottomControlsTest` | 系统栏 insets 真的被应用到根视图、布局没有重复 fit、`SOLVE` / `BACK` 落在导航栏之上且留有底部余量（Robolectric 里派发合成 insets） | 4 |
| `IconTest` | 图标资源可解析（自适应图标 + 两层 drawable）、标记落在 66dp 安全圆内、两道标记不重叠 | 3 |
| `LaunchSmokeTest`（androidTest） | 应用能在真机上启动并显示输入界面 | 1 |

单元测试共 **203** 例。

### 4.2 构建与测试命令

```bash
cd F:/code/Wakusei
./gradlew.bat :app:testDebugUnitTest   # 全部单元测试（203 例，约 70 s）
./gradlew.bat :app:lintDebug           # Android Lint（当前应无 error / warning）
./gradlew.bat :app:assembleDebug       # 打 debug APK
./gradlew.bat :app:assembleDebugAndroidTest   # 编译仪器测试
./gradlew.bat :app:connectedDebugAndroidTest  # 需要真机/模拟器
./gradlew.bat :app:compileDebugKotlin  # 只编译，最快的自检
node tools/reference-solver.js         # 重新打印期望解数（独立实现，见附录 B）
```

### 4.3 关于仪器测试

`LaunchSmokeTest` 只做一件事：启动 `MainActivity` 并断言输入界面可见。它存在的理由很具体——
§3.7 里那个启动崩溃只发生在真实进程启动路径上，Robolectric 看不到它。它由
`:app:assembleDebugAndroidTest` 保证可编译，但**需要连接设备才能执行**；本仓库的验证环境没有
设备，所以它在本机没有被跑过（这一点没有隐瞒在别处）。

### 4.4 CI

`.github/workflows/android.yml`：push / PR 时在 `ubuntu-latest` 上装 JDK 25 与 Android SDK，
跑 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug`、
`:app:assembleDebugAndroidTest`，并上传测试与 lint 报告。选 JDK 25 是为了与
`gradle/gradle-daemon-jvm.properties` 里的守护进程工具链一致，避免每次构建都让 foojay 解析器
去下载一个 JDK。**该工作流尚未在 GitHub 上实际运行过**（本地没有 CI 环境），第一次推送时请留意
日志。

### 4.5 已处理的工具链坑

- Robolectric 需要 `testOptions.unitTests.isIncludeAndroidResources = true` 才能看到合并后的资源
  与 manifest。
- 每个 Robolectric 测试类都要写 `@Config(sdk = [34])`：Robolectric 4.17 仍不认识本项目的
  `targetSdk 37`，缺了它整类会在 `DefaultSdkPicker` 阶段直接失败（试过去掉，就是失败的样子）。
- Robolectric 4.17 已经能在 Gradle 守护进程的 **JDK 25** 上跑，所以此前「单测固定到本机
  JDK 21」的权宜已经删掉（`app/build.gradle.kts` 里留了恢复它的注释）。应用与测试字节码仍是
  Java 11。
- Robolectric 无法真正栅格化 `VectorDrawable`（画进 `Canvas` 后再读 `Bitmap` 像素全是 0），所以
  图标外观用浏览器渲染同一份 pathData 校验，测试里只做资源与几何断言。

---

## 5. 局限

### 5.1 能力边界

- 只支持二元运算符 `+ - * /`：没有一元负号、指数、取模、拼接（一些变体允许 `4!`、`√`、`12`）。
- 每个数必须**恰好用一次且全部用上**：不支持「抽取子集求解」。
- 求解器输入 `1..8` 且 `|x| < 256`；界面进一步限制为 ≤5 个、`|x| < 256`、`|target| ≤ 16384`。
- 解只有表达式字符串：不产出步骤序列、不标注难度。排序是「简单优先（长度、字典序）」，
  不是「好玩优先」。

### 5.2 n ≥ 6 无法完全枚举（有意为之）

顶层 mask 的表达式条数是 `Catalan(n-1) · 4^(n-1) · n!`：n=6 已是 3.1×10^7，n=7 是 2.4×10^9，
n=8 是 1.1×10^12。**这是输出本身的规模**，换算法也救不了——穷尽枚举在 n≥6 就不是「慢」的问题，
而是「结果比内存大」。因此求解器选择：在半秒内抛出说明清楚的 `SolverLimitException`，而不是假装
在工作。想支持更大的 n，需要换语义（例如只问「有没有解」或只输出本质解），那是另一条产品路线。

### 5.3 界面侧的已知取舍

- 长按阈值固定为系统默认 500 ms，尚不可配置（P2 TODO）。
- 答案列表是单个可选中 `TextView` + `ScrollView`（未引入 RecyclerView）；解数上千时（5 个数字
  可能有两千多条）会明显吃内存与滚动性能。
- 求解与结果界面之间用 ViewModel 传数据，进程被杀后结果页会显示空状态（符合预期，但不会自动重算）。
- **音效参数没有在真机上听过**：音符时长（按键 750ms、音阶 450ms + 80ms 间隔、尾音 1.2s）、三个模式
  的相对强度与衰减都是按听感估的理论值，可能需要按实际设备调。音效走**媒体**流，所以「静音模式」下
  是否发声取决于系统对该流的处理；如果需要「跟着铃声静音一起静音」，应改用
  `USAGE_ASSISTANCE_SONIFICATION`（一行改动）。
- 音效是**六声部复音**：最多 6 个音同时响，超了就丢最快消失的那条尾音。**一条音阶只占 1 个声部**
  （整条音阶在一个缓冲区里就混好了），所以弹音阶时按键不会挤掉音阶音；真正会互抢声部的是「快速
  连按多个键」。跨声部的叠加仍然由系统混音，理论上仍可能瞬间顶到满刻度；应用内的软限幅只作用于
  一条乐句内部。
- 语言的「离开设置页生效」靠 `SettingsFragment.onDestroyView` + `isChangingConfigurations`：
  如果将来有人把应用时机挪走（例如挪回 Activity 的 `onCreate`），规则就变了，
  `LanguageApplyRule` 与 `SettingsFragmentTest` 会因此失败。
- 界面文案中文、日文由我撰写，**没有母语者校对**；三套字符串的**完整性**由 lint 保证（漏翻即失败），
  但用词是否地道无法自动验证。

### 5.4 「本质不同」的定义边界

`essentialSolutions` 只做 `+`/`*` 的**交换律 + 结合律**归一。它**不**合并下列语义相同的解：

- `a - b` 与 `a + (-b)`、`a / b` 与 `a * (1/b)`；`a - (b - c)` 与 `a - b + c`；
- 跨运算符的代数恒等式（例如 `x * 2 / 2` 与 `x`）。

这是刻意的保守选择：再往前一步就会开始合并用户认为不同的写法。规范形的精确定义见 §2.3。

### 5.5 工程与代码质量

- `Solver` 用的是保存表达式的 DP，内存随中间结果增长；预算把最坏情况限制在约 47 万条，
  这是为「界面允许的输入」量身定制的数字（§2.5）。
- 求解逻辑虽然是纯 JVM，仍放在 `app` 模块里（只跑 `testDebugUnitTest`）。抽成独立的
  `:solver` JVM 模块是更干净的做法，但当前工具链是 AGP 9 的**内置 Kotlin**，额外引入
  `org.jetbrains.kotlin.jvm` 插件与之搭配需要额外验证，收益（构建隔离）暂时不抵风险。
  `SolverPurityTest` 已经用测试守住了「不依赖 Android」这条实质约束。
- Lint 目前 **0 error / 0 warning**（`tools:ignore="NestedWeights"` 是唯一一处抑制，理由是布局里
  的权重套权重正合语义，见 §3.1）。没有 detekt / ktlint / 覆盖率门槛。
- `release` 构建**没有开启 R8**（`optimization.enable = false`，见 `app/build.gradle.kts` 的注释）：
  打开它会影响用户实际安装的那个产物，而开启后只能在真机上验证；本仓库的验证手段停在
  「能编译、能过测试」，所以没有贸然打开。
- 广告：依赖与相关代码已按决定**全部移除**（见 §3.7），因此没有这条待办；将来若要重新接广告，
  需要重新面对 manifest provider 带来的启动崩溃风险。

---

## 6. TODO LIST

### P0 —— 让它真的能用（已完成）

- [x] 修数字键映射错位，并改为表驱动防止复发
- [x] 界面接上求解器：`SOLVE` → 校验 → 求解；`TARGET` → 目标编辑
- [x] 目标值可编辑、可写回，拉杆保持激活/不激活
- [x] 调用前校验（≤5、`|x| < 256`、`|target| ≤ 16384`），违规用提示而非崩溃
- [x] 后台线程求解（`Dispatchers.Default`），期间禁用按钮并显示 `SOLVING…`
- [x] 输入可编辑：`DEL` 退格、`CLEAR` 按作用域清理、`[+/-]` 取反
- [x] 结果界面：答案列表 + `BACK`
- [x] 统一 256 边界为严格小于（与求解器一致）
- [x] 允许负数输入（`[+/-]`）

### P1 —— 让「全部解」这个目标站得住（已完成）

- [x] **明确「本质不同」的定义**并实现：`+`/`*` 的交换律 + 结合律归一（§2.3），
      `1,2,3,4 → 24` 从 242 条降到 28 条
- [x] 提供两档输出：全部表达式 / 本质不同的解（结果界面开关，§3.5）
- [x] 用**子集 DP** 替代树枚举，去掉「运算符槽位多枚举 4 倍」的浪费
- [x] 枚举器改为无状态（不可变 `Expression` + 纯函数），可安全重入与并行化
- [x] 修掉 `Long` 静默溢出：`Rational` 换成 `BigInteger`（§2.2）
- [x] 打印去掉冗余括号（按优先级 / 结合性，§2.3），并保证可往返解析
- [x] 补「每个解真的等于 target，且每个数恰好用一次」的验证型测试
- [x] 补与独立暴力枚举的差分测试（随机 n ≤ 5）
- [x] 内存预算：宁可快速报错，也不 OOM 或假死（§2.5）
- [x] 底部按钮不再被导航栏遮挡（显式应用系统栏 insets + 一个按钮高的余量，§3.6）

### P1.5 —— 这一版新增（已完成）

- [x] 按键音效：15 键洗牌轮转、`SOLVE` 与长按的音阶、**运行时合成 PCM**（零素材、零授权）、
      后台播放不阻塞界面、默认开启（§3.8）
- [x] 三语界面：中文 / English / 日本語，英文为默认与 fallback（`values-ja` 新增）
- [x] 语言切换：跟随系统 / 中文 / EN / JP，**下次启动应用时生效**（per-app language + 持久化，§3.9）
- [x] 音符不互相打断、自然延音：6 声部复音 + 缓存保证即时起音，音阶尾音拖长（§3.8）
- [x] 音阶的 GAP 改为**起音间隔**（120ms）：音与音叠成滚奏，缓冲区内部混音 + 软限幅不破音（§3.8）
- [x] 修「播过音阶后按单音会带出旧音阶」：静态音轨只在长度完全相等时复用（§3.8）
- [x] 设置页与标题栏三横线入口（跟随主题前景色），两个界面都能进（§3.9）
- [x] 默认目标数：滑杆节点 + `CUSTOMIZE` 数字输入，影响初始化与长按 CLEAR，不动当前目标（§3.9）
- [x] 音效总开关（§3.9）
- [x] 广告：按决定**不接入广告**，依赖 + `Ads.kt` + manifest meta-data + `AdsTest` 全部移除（§3.7）
- [x] `DEL` 长按改为三段式：先清当前输入，输入为空再撤销最后一个候选数，都没有则什么都不做（§3.4）

### P2 —— 工程与体验（未完成）

- [ ] **在真机上听一遍音效并调参**（音量、音色、音符时长），确认媒体流是否符合预期（§5.3）
- [ ] 让长按阈值 `x` 可配置（当前固定为系统默认 500 ms），`CLEAR` 与 `DEL` 共用同一来源
- [ ] 请母语者校对中文/日文文案（完整性已由 lint 保证，用词没有）
- [ ] 答案列表换成 `RecyclerView`（现在解数上千时是单个 TextView）
- [ ] 把 `solver/` 抽成纯 Kotlin JVM 模块（`SolverPurityTest` 已守住依赖边界）
- [ ] 打开 release 的 R8（`optimization.enable = true`）+ 两个 Fragment 的 keep 规则（已写在
      `app/src/main/keepRules/rules.keep` 里），并在真机上冒烟验证
- [ ] 探索 Robolectric 对新 SDK 的支持，去掉每个测试类上的 `@Config(sdk = [34])`
- [ ] 再加语言（`values-zh-rTW` 等）；把 `TUTORIAL.md` 也翻成中文/日文
- [ ] 音效增强：更多声部、每键音量、可选音色、应用内软限幅混音（若要彻底消除叠加削波，§5.3）
- [ ] 配置 detekt / ktlint，并把 lint 的 warning 也纳入 CI 门槛
- [ ] 在 CI 上加一个（可选、手动触发的）模拟器 job 跑 `connectedDebugAndroidTest`
- [ ] 交互增强：允许「不用完所有数字」的变体模式、更多运算符（一元负号、`!`、拼接）
- [ ] 输出增强：一键复制 / 导出、按「好玩程度」排序

---

## 附录 A：复杂度速查

```
旧实现实际循环次数        = Catalan(n-1) · 4^n     · n!      （运算符槽位多枚举 4 倍）
新实现：树形枚举的等价规模 = Catalan(n-1) · 4^(n-1) · n!
新实现：DP 的切分组合数    = Σ_k C(n,k) · (2^(k-1) - 1)      （无序二分）
5 个互不相同的数的记忆表   = Σ_k C(n,k) · Catalan(k-1) · 4^(k-1) · k! = 470,485
Catalan(k)                = C(2k, k) / (k + 1)
本质不同排列数（有重复数字）= n! / ∏(每个不同数字出现次数的阶乘)
```

## 附录 B：结论是怎么验证的

本轮的每个数字都不是「跑一遍自己写的代码然后相信它」：

- **解数（242 / 28 / 26 / 2 / 45 / …）** 来自 [`tools/reference-solver.js`](tools/reference-solver.js)：
  一份与前一代实现逐结构同构的独立 Node.js 复刻（同样的树形、同样的四进制运算符向量、同样的排列
  顺序，`BigInt` 精确运算，全括号打印，再用一个自己写的解析器做交换/结合归一），与 app 没有任何共享
  代码。用 `node tools/reference-solver.js` 可以随时重新打印出这张表。这些期望值被硬编码进
  `SolverTest.reportsTheIndependentlyVerifiedSolutionCounts`，所以新实现必须去迁就一份它没有参与
  写出的真相。
- **穷尽性**由差分测试保证：`BruteForceOracle` 用完全相反的策略（枚举树形 × 运算符 × 排列，
  完全不共享中间结果）算出解集，断言它与 DP 的输出**逐字符串相等**（随机 n ≤ 4 共 60 组，
  外加 5 个数的用例）。
- **打印**由 `ExpressionParser`（按教科书优先级手写的递归下降解析器）验证：每个解打印出来的
  字符串都能被解析回**结构相同**的树，并在 500 个随机树上做了往返测试。
- **溢出**由具体算例固定：`255^8 = 17878103347812890625` 超出 `Long.MAX_VALUE`，`Long` 会得到
  `-568640725896660991`，函数 `RationalTest.arithmeticDoesNotWrapAroundWhereLongWould` 同时断言了
  两个值。
- **架构约束**由 `SolverPurityTest` 扫描编译产物的常量池，断言 `solver` 包不出现
  `android/`、`androidx/` 引用。
- **界面行为**由 `FirstFragmentTest` 在 Robolectric 上真实驱动：启动 `MainActivity` →
  NavHostFragment → `FirstFragment`，点击/长按并断言文本、Toast、振动常量、布局参数，
  包括「旋转一次后输入还在」和「全部/本质开关切换后行数与标题都变」。
