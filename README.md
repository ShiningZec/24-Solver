# 24 Solver (Wakusei)

一个 Android + Kotlin 的「24 点」求解器：给定一组数字和一个目标值，**穷举出全部可能的解表达式**。

- 模块：`:app`（Android Application），包名 `com.a24solver.wakusei`
- 语言 / 工具链：Kotlin，Gradle (KTS)，AGP 9.4 / Gradle 9.6，`minSdk 24`，`compileSdk/targetSdk 37`，Java 11（字节码），viewBinding，Material 3
- 应用名（`app_name`）：**24 Solver**；Gradle 根项目名：`24 Solver`
- 文档：
  - 本文件：项目状态、求解原理、界面实现、局限与 TODO
  - [`TUTORIAL.md`](TUTORIAL.md)：面向使用者的简短交互说明书（en-US）
  - `diff_[time]_[date].MD`：逐轮改动的真实 git diff 记录
- 文档日期：2026-09-29

---

## 1. 项目介绍

### 1.1 目标

求解器要回答的问题：

> 给定 `n` 个数（`1 ≤ n ≤ 8`，每个数的绝对值 `< 256`）和一个目标值 `target`，
> **把每个数恰好使用一次**，用 `+ - * /` 和任意加括号方式，列出**所有**能得到 `target` 的表达式。

名字叫 24 Solver（默认目标是 24），但 `target` 是入参，任何整数目标都能求解（例如 `target=60`）。数字在 API 层面允许负数（界面用 `[+/-]` 键输入）。

关键词是「**全部**」：不是「找到一个就返回」，而是**完全枚举**——不剪枝、不启发式，只要不漏解。运算全程使用自己实现的有理数类，因此 `(1 + 1/9) * 9` 这类依赖分数中间结果的解不会因浮点误差被漏掉或误判。

界面侧对输入有更严格的限制（见 §3.3）：**候选数最多 5 个**、`|候选数| < 256`（严格小于，与求解器的 `require` 一致）、`|target| ≤ 16384`。求解器本身的能力上限仍是 `n = 8`。

### 1.2 目录结构

```
Wakusei/
├── README.md                     # 本文件
├── TUTORIAL.md                   # 交互说明书（英文）
├── diff_*.MD                     # 逐轮变更记录（真实 git diff）
├── settings.gradle.kts           # 根项目 "24 Solver"，include(":app")
├── build.gradle.kts / gradle/    # 版本目录 (libs.versions.toml)、wrapper
└── app/
    ├── build.gradle.kts          # 模块配置（viewBinding、依赖、单测 JVM、SDK 版本）
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml    # 含 AdMob APPLICATION_ID（见 §3.6）
        │   ├── java/com/a24solver/wakusei/
        │   │   ├── SyntaxTree.kt      # ★ 求解核心（枚举 / 求值 / 打印）
        │   │   ├── Rational.kt        # ★ 精确有理数
        │   │   ├── FirstFragment.kt   # ★ 输入界面：键盘、校验、后台求解
        │   │   ├── SecondFragment.kt  # ★ 结果界面：答案列表 + BACK
        │   │   ├── SolveResultViewModel.kt  # 两个界面之间共享的解集
        │   │   ├── Ads.kt             # 广告 SDK 的异常防护层
        │   │   └── MainActivity.kt    # 壳：NavHost + 40dp 顶栏（FAB 已注释）
        │   ├── keepRules/rules.keep   # R8 保留规则（模板，全注释）
        │   └── res/
        │       ├── layout/fragment_first.xml   # 键盘 + 标签 + SOLVE
        │       ├── layout/fragment_second.xml  # 标题 + 答案 + BACK
        │       ├── values/strings.xml          # 全部文案
        │       ├── values/styles.xml           # 键盘按键样式（20sp 字号等）
        │       ├── values/colors.xml           # rose / rose_checked / rose_disabled
        │       ├── color/button_rose_tint.xml  # 特殊键的玫红状态选择器
        │       ├── drawable/ic_launcher_background.xml  # 图标底层：玫红铺底
        │       ├── drawable/ic_launcher_foreground.xml  # 图标标记：七段式 “24” + SOLVER
        │       ├── mipmap-anydpi*、mipmap-*dpi/         # 自适应图标（API 26+）+ 早期密度位图
        │       └── navigation / menu / values-* / xml
        ├── test/java/com/a24solver/wakusei/
        │   ├── SyntaxTreeTest.kt      # 求解器：命中 + 枚举不重不漏
        │   ├── RationalTest.kt        # 有理数运算
        │   ├── FirstFragmentTest.kt   # ★ 输入/结果界面的 Robolectric 测试（25 例）
        │   ├── AdsTest.kt             # 广告防护（3 例）
        │   ├── IconTest.kt            # 图标资源与 66dp 安全区几何（3 例）
        │   └── ExampleUnitTest.kt
        └── androidTest/java/…         # 仪器测试（模板）
```

求解逻辑（`SyntaxTree.kt` / `Rational.kt`）不引用任何 `android.*` 类，与界面完全解耦，因此可直接用 JVM 单测驱动；界面逻辑用 Robolectric 在 JVM 上驱动（见 §4）。

### 1.3 已实现 / 未实现

| 部分 | 状态 |
| --- | --- |
| 有理数运算、三种枚举器、命中判定、表达式字符串化 | ✅ 实现并有单测 |
| 输入界面：数字键盘、`[+/-]`、`DEL`、`NEXT`、`CLEAR`、`TARGET` 拉杆 | ✅ 实现并有测试 |
| 调用前校验（≤5 个候选数、`|x| < 256`、`|target| ≤ 16384`） | ✅ 实现并有测试 |
| 后台求解（`Dispatchers.Default` + `lifecycleScope`），求解中禁用按钮 | ✅ 实现并有测试 |
| 结果界面：答案列表 + `BACK` 返回 | ✅ 实现并有测试 |
| 长按手势：`CLEAR` 全部复位、`DEL` 撤销最后一个候选数（带系统级轻振动） | ✅ 实现并有测试 |
| 广告 SDK 的异常防护 + 启动崩溃的配置修复 | ✅ 实现并有测试（见 §3.6） |
| 解集的规范化去重（等价式合并） | ❌ 目前只在字符串层面去重（§5.2） |
| 性能优化（剪枝 / 记忆化 / 并行） | ❌ 纯暴力枚举（§5.3）。界面把候选数限制在 5 个，n ≤ 5 可用 |
| 长按阈值可配置 | ❌ 目前固定为系统默认 500 ms（TODO P2） |

---

## 2. 求解原理

### 2.1 一句话概括

`解集 = { 打印(树, 运算符分配, 排列) | 树 ∈ 全部满二叉树形状, 运算符 ∈ {+,-,*,/}^(n-1), 排列 ∈ S_n, 求值(…) == target }`

即：**「所有加括号方式」×「所有运算符组合」×「所有数字摆放顺序」**，三重笛卡尔积逐一求值。代码就是 `SyntaxTree.solveIt` 里的三个嵌套 `for`（`SyntaxTree.kt:54-63`）。

### 2.2 三个枚举器

**(1) 表达式树形状 —— `generateTrees(size)`**（`SyntaxTree.kt:70-107`）

任何「用 `n` 个数做一次四则运算链」的表达式都对应一棵**叶子数 = n 的满二叉树**。递归枚举所有形状，数量是 Catalan 数 `C(n-1)`：n=4 → 5 种，n=8 → 429 种。关键设计是**槽位编号**：生成时给叶子分配 `leafIdx ∈ 0..n-1`、给内部结点分配 `signIdx ∈ 0..n-2`，于是「树的形状」与「用哪些数字、填哪些运算符」解耦——形状只枚举一次即可复用。返回惰性 `Sequence<Node>`。`SyntaxTreeTest.testGenTrees` 校验这个不重不漏的性质。

**(2) 运算符组合 —— `SignAssign(size)`**（`SyntaxTree.kt:133-157`）

运算符编码为 `0 → +`、`1 → -`、`2 → *`、`3 → /`（取模 4 兜底）。`SignAssign` 是一个四进制计数器：`hasNext()` = 还有不是 3 的位；`next()` = 从低位进位加一。

**(3) 数字摆放 —— `permutations(size)`**（`SyntaxTree.kt:109-130`）

标准回溯（`vis` + `bucket`），惰性产出 `n!` 个下标排列。

### 2.3 求值与字符串化

- `resolve(...)`（`SyntaxTree.kt:20-35`）：递归求值，叶子取 `numbers[permutation[leafIdx]]`，内部结点按 `signAssign[signIdx] % 4` 运算。**除以 0 返回 `null`**，`null` 沿树上传使整条分支作废——除零保护靠类型系统而非异常。
- `myPrint(...)`（`SyntaxTree.kt:37-51`）：与求值同构的递归，输出**全括号中缀**形式，如 `(((1 + (1 / 9)) * 9) * 6)`。
- 命中判定 `Rational(target.toLong()) == result`：`Rational` 是 `data class`，相等性即「约分后分子分母分别相等」，精确无误差。
- 解集是 `MutableSet<String>`，**去重单位是打印出来的字符串**。

### 2.4 精确有理数 `Rational`

`data class Rational(n: Long, d: Long = 1L)`：`init` 中拒绝分母 0、把 0 规范成 `0/1`、把符号搬到分子、用 `gcd` 约分——**构造即最简**，这是相等比较能用 data class 默认实现的前提。提供四则运算（除零返回 `null`）、运算符重载、`compareTo`、`toString`（分母为 1 只打印整数）。不用 `Double` 是正确的：24 点存在大量必须用分数表达的解。

### 2.5 搜索空间规模（实测）

`总循环次数 = Catalan(n-1) × 4^n × n!`：

| n | 树形状 Catalan(n-1) | 运算符 4^n | 排列 n! | 实际循环次数 |
| --- | --- | --- | --- | --- |
| 2 | 1 | 16 | 2 | 32 |
| 3 | 2 | 64 | 6 | 768 |
| 4 | 5 | 256 | 24 | 30,720 |
| 5 | 14 | 1,024 | 120 | 1,720,320 |
| 6 | 42 | 4,096 | 720 | 123,863,040 |
| 7 | 132 | 16,384 | 5,040 | 10,899,947,520 |
| 8 | 429 | 65,536 | 40,320 | **1,133,594,542,080** |

（`SignAssign(size)` 产出 `4^n` 个向量，但树只有 `n-1` 个内部结点槽位，最后一个分量永远不被读取 → 白跑 4 倍；本应是 `4^(n-1)`。）

### 2.6 解集的冗余度（实测）

| target | 数字 | 实际循环次数 | 解集大小（字符串） |
| --- | --- | --- | --- |
| 24 | 1, 2, 3, 4 | 30,720 | **242** |
| 24 | 3, 3, 8, 8 | 30,720 | 1 |
| 24 | 5, 5, 5, 1 | 30,720 | 2 |
| 24 | 23, 23, 23, 23 | 30,720 | 2 |
| 60 | 1, 1, 9, 9, 6 | 1,720,320 | 26 |

`1,2,3,4 → 24` 的 242 条里，若按「加法/乘法的交换律 + 结合律」归一，只剩 **28 个本质不同的解**——约 88% 是等价变形（§5.2）。

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

- 每个键有 `4dp` 的左右外边距（`layout_marginStart/End`）形成间隙；按键文字 `20sp`（`values/styles.xml` 的 `Widget.App.KeypadButton`）。
- 键盘整体包在 `@id/keypad` 容器里，权重 4；其下是 `@id/keypad_spacer`（权重 1）。因此键盘区高度恰为原先行区空间的 **80%**，每个键相应变矮；空出的 1/5 成为 `SOLVE` 上方的留白。
- `SOLVE` 固定 `56dp`（默认 wrap_content 约 48dp，即约 +20%）。
- 特殊键（`[+/-] [NEXT] [CLEAR] [TARGET] [DEL]`）用 `Widget.App.KeypadButton.Special`：`backgroundTint` 指向 `res/color/button_rose_tint.xml`（默认玫红 `#D81B60` / 拉杆激活 `#AD1457` / 禁用淡玫红），白字。
  - ⚠️ 必须用 `app:backgroundTint` 或样式里的 `backgroundTint`，**`android:backgroundTint` 会被 MaterialButton 忽略**（主题色会赢）。
- 顶栏：`AppBarLayout` 固定 `40dp`（原 `?attr/actionBarSize` 56dp），`MaterialToolbar` 加 `minHeight=0dp`；承载标题与返回箭头，仍在用。
- FAB（浮动按钮）按需求**注释掉**（`activity_main.xml` + `MainActivity.kt` 的监听与 import），需要时取消注释即可。

### 3.2 输入模型：两个缓冲 + TARGET 拉杆

- 两个独立缓冲：`numberInput`（候选数）与 `targetInput`（目标值），数字键编辑的是「当前模式」的那个，互不干扰。
- `[TARGET]` 是**拉杆**（`MaterialButton` + `android:checkable`，点击后自身保持选中）：激活后键盘改写目标值，**输入框不预填**（保持 `_`，标签仍显示生效中的目标）；退出时若输入非空且非 `-` 就**写回**目标值，超范围则拒绝并保持激活（输入不丢）。再按一次即取消。
- `NEXT` 提交当前缓冲（候选数或目标值），提交后清空缓冲。
- `DEL` 点按 = 退格；`[+/-]` 对当前缓冲取反（空缓冲时按它会开始一个负数）。

### 3.3 调用前校验

`FirstFragment.validate()` 在调用求解器**之前**执行（录入时也会即时校验）：

| 规则 | 常量 | 失败时 |
| --- | --- | --- |
| 候选数个数 ≤ 5 | `MAX_NUMBERS = 5` | Toast「At most 5 candidate numbers are allowed」 |
| 每个候选数 `|x| < 256`（严格） | `MAX_ABS_NUMBER = 256` | Toast「Each candidate number must satisfy |x| < 256」 |
| 目标值 `|target| ≤ 16384` | `MAX_ABS_TARGET = 16384` | Toast「The target must be within ±16384」 |
| 至少一个候选数 | — | Toast「Add at least one number …」 |

按 `SOLVE` 时若缓冲里还有未提交的数字，会先自动提交——保证「校验的就是屏幕上看到的」。该严格上界与 `SyntaxTree.solveIt` 的 `require { abs(it) < 256 }` 现已一致，不会再有「界面通过、求解器拒绝」的边界矛盾。

### 3.4 长按手势与按键振动

| 手势 | 行为 |
| --- | --- |
| `CLEAR` 点按 | 只清当前模式归属的那一份（目标模式 → 目标回 24；否则清候选数），**完全静默** |
| `CLEAR` 长按 | 候选数与目标全部复位，Toast「All Numbers Set to Default」+ 轻振动 |
| `DEL` 长按 | 删除最后一个已提交的候选数；**仅在真的删除了**的时候轻振动 |

- 阈值 `x = 500 ms`：使用平台自身的长按超时（`ViewConfiguration.getLongPressTimeout()`，标准 Android 上就是 500 ms），所以 `FirstFragment.LONG_PRESS_MS` 是**说明性常量**而非强制值。让 `x` 可配置是 P2 TODO。
- 振动采用 `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`：**弱**、无需任何权限（没有 `VIBRATE`、不直接使用 Vibrator），并且由系统设置托管——用户关闭「触感反馈 / 振动与触感」后即静默。即「默认受 Android 管理」的那条路径。

### 3.5 求解与结果界面

```kotlin
solveJob = viewLifecycleOwner.lifecycleScope.launch {
    val outcome = withContext(Dispatchers.Default) { runCatching { SyntaxTree.solveIt(target, candidates) } }
    …
}
```

求解期间标签显示 `SOLVING…`、`SOLVE` 禁用；完成后把结果交给 activity 级 `SolveResultViewModel`（不用 navigation arguments：答案可能有上千条，arguments 会写进 saved state，必须保持小体积）并 `navigate(action_FirstFragment_to_SecondFragment)`。

结果界面（`SecondFragment` + `fragment_second.xml`）：上方是标题（`N answer(s) for target T from …`）与可滚动的答案列表（等宽、可选中复制，一行一条，带序号），下方是通栏 `BACK` → `navigateUp()` 回到键盘。无解时显示「No expression built from these numbers reaches the target.」。求解器抛异常（`IllegalArgumentException`）时用 Toast 报告并恢复标签，不崩溃。

### 3.6 广告依赖的异常防护

`play-services-ads` 会把 `MobileAdsInitProvider` 合并进 manifest，它在 `ActivityThread.handleBindApplication` 阶段（**早于 `Application.onCreate`**）运行，缺少 `com.google.android.gms.ads.APPLICATION_ID` 时抛 `IllegalStateException`，表现为启动崩溃 `RuntimeException: Unable to get provider com.google.android.gms.ads.MobileAdsInitProvider`。**这种崩溃无法用应用代码 try/catch**，所以：

- `AndroidManifest.xml` 声明该 meta-data（值取自 `@string/admob_app_id`，当前是 Google 官方样例 ID，**发布前必须替换**）；
- `Ads.kt` 提供异常防护层（`loadSafely` 兜住 `Throwable`、`initialize` 幂等），`MainActivity.onCreate` 调用一次；
- `AdsTest` 断言该 meta-data 存在且形态正确（`ca-app-pub-…~…`）。

---

## 4. 测试与构建

| 测试类 | 覆盖内容 | 数量 |
| --- | --- | --- |
| `RationalTest` | 构造/约分/符号、四则、除零、比较 | 7 |
| `SyntaxTreeTest` | `solveIt` 命中（含分数解）、三个枚举器不重不漏 | 8 |
| `FirstFragmentTest` | 键盘布局/尺寸/配色、按键接线、拉杆与写回、CLEAR 点按与长按、DEL 长按、校验、后台求解与结果往返（Robolectric） | 25 |
| `AdsTest` | app ID 声明、异常防护、初始化不抛 | 3 |
| `IconTest` | 图标资源可解析（自适应图标 + 两层 drawable）、标记落在 66dp 安全圆内、两道标记不重叠 | 3 |
| `ExampleUnitTest` | 模板用例 | 1 |

```bash
cd F:/code/Wakusei
./gradlew.bat :app:testDebugUnitTest   # 全部单元测试
./gradlew.bat :app:assembleDebug       # 打 debug APK
./gradlew.bat :app:compileDebugKotlin  # 只编译，最快的自检
```

两个已处理的工具链坑（都在 `app/build.gradle.kts` 里）：

- Robolectric 需要 `testOptions.unitTests.isIncludeAndroidResources = true` 才能看到合并后的资源与 manifest。
- Gradle 守护进程跑在 **JDK 25** 上，Robolectric 4.14.1 内置的 ASM 读不了 Java 25 平台类（`Unsupported class file major version 69`），因此单测任务被固定到本机 **JDK 21**；应用与测试字节码仍是 Java 11。
- 每个 Robolectric 测试类都要写 `@Config(sdk = [34])`（Robolectric 4.14.1 不认识本项目的 `targetSdk 37`，缺了它整类会在 `DefaultSdkPicker` 直接初始化失败）。
- Robolectric 无法真正栅格化 `VectorDrawable`（画进 `Canvas` 后再读 `Bitmap` 像素全是 0），所以图标外观用浏览器渲染同一份 pathData 校验，测试里只做资源与几何断言。

`SyntaxTreeTest.kt:54-61` 里 `size = 6` 的用例仍被注释掉（约 1.2 亿次循环）。

---

## 5. 局限

### 5.1 界面侧的已知取舍

- 长按阈值 `x` 固定为系统默认 500 ms，尚不可配置（P2 TODO）。
- 答案列表是单个可选中 `TextView` + `ScrollView`（未引入 RecyclerView）；解数上千时建议换 `RecyclerView`。
- FAB 已注释保留；若确定不用可连同 `@dimen/fab_margin` 一起删除。
- 界面文案为英文，目前没有 `values-zh`（文案已全部资源化，加一个目录即可中文）。
- 求解与结果界面之间用 ViewModel 传数据，进程被杀后结果页会显示空状态（符合预期，但不会自动重算）。

### 5.2 「全部可能解」目前是「全部字符串」

去重单位是打印字符串（`Set<String>`），因此下列语义相同的解会同时出现：交换律（`(12 + 13)` 与 `(13 + 12)`）、结合律（`((a + b) + c)` 与 `(a + (b + c))`），以及再叠加排列产生的整体等价。实测 `1,2,3,4 → 24`：242 条字符串 vs 28 个交换/结合等价类。也没有做 `a - b` / `a + (-b)`、`a / b` / `a * (1/b)` 这类代数变形合并。**「解」的规范形式尚未定义**。

### 5.3 性能：搜索空间远超手机承受范围

- n = 8 是 `1.13 × 10^12` 次循环（n = 7 已是 `1.1 × 10^10`），单线程 JVM 也要几天到几年。界面把候选数限制在 5 个，n ≤ 5 在后台线程上是秒级，但求解器自身没有更严的保护（只有 `require(size in 1..8)`）。
- 没有剪枝、没有子表达式缓存、不排除「同一子集重复求解」。
- 排列按**下标**而非**值**去重：输入有重复数字时（`1,1,9,9,6`、`5,5,5,1`）会重复计算完全相同的表达式，只靠最后字符串 `Set` 掩盖。
- 运算符枚举多跑 4 倍（§2.5）。
- 每个候选解都要重新走一遍树并拼接字符串；`Rational` 每次新建对象，分配量大。

### 5.4 数值与类型问题

- `Rational` 全程 `Long` 且不检查溢出：`n * other.d`、`this.d * other.d` 在嵌套运算中分母迅速变大，溢出后**静默给出错误结果**；`compareTo` 的叉乘同理。
- `divide` 返回可空 `Rational?`，调用方到处 `!!`，漏判即 `NullPointerException`。
- 相等性依赖「构造即约分」的不变式；若将来允许原地修改分子分母，`data class` 的 `equals` 会立刻失效。

### 5.5 工程与代码质量

- `SignAssign` 与 `permutations` 是**有状态、复用同一数组对象**的枚举器：当前调用方式没踩到，但任何「迭代两遍」「保存后处理」「并行化」的改动都会静默出错。
- `SyntaxTree.solveIt` 是巨型函数（局部函数 + 三层循环），不便细分测试与改造。
- 无 CI、无 lint/detekt；求解逻辑是纯 JVM 却放在 `app` 模块里，只能跑 `testDebugUnitTest`。
- 广告相关：`admob_app_id` 仍是 Google 样例 ID（发布前必须替换）；依赖虽然没有广告位，但已声明且必须带着 meta-data，否则启动即崩。

### 5.6 能力边界

- 只支持二元运算符 `+ - * /`：没有一元负号、指数、取模、拼接（一些变体允许 `4!`、`√`、`12`）。
- 每个数必须**恰好用一次且全部用上**：不支持「抽取子集求解」。
- 求解器输入 `1..8` 且 `|x| < 256`；界面进一步限制为 ≤5 个、`|x| < 256`、`|target| ≤ 16384`。
- 解只有全括号字符串形式：不产出步骤序列、不排序（仅按字符串排序）、不标注难度。

---

## 6. TODO LIST

### P0 —— 让它真的能用（已完成）

- [x] 修数字键映射错位，并改为表驱动防止复发
- [x] 界面接上求解器：`SOLVE` → 校验 → `solveIt`；`TARGET` → 目标编辑
- [x] 目标值可编辑、可写回，拉杆保持激活/不激活
- [x] 调用前校验（≤5、`|x| < 256`、`|target| ≤ 16384`），违规用提示而非崩溃
- [x] 后台线程求解（`Dispatchers.Default`），期间禁用按钮并显示 `SOLVING…`
- [x] 输入可编辑：`DEL` 退格、`CLEAR` 按作用域清理、`[+/-]` 取反
- [x] 结果界面：答案列表 + `BACK`，接上 `action_FirstFragment_to_SecondFragment`
- [x] 统一 256 边界为严格小于（与求解器一致）
- [x] 允许负数输入（`[+/-]`）
- [x] `git init` 并提交基线，后续改动可产出真实 diff

### P1 —— 让「全部解」这个目标站得住（语义 + 规模）

- [ ] **明确并实现「解」的规范形式**：交换律 + 结合律规范化（把 `+` / `*` 的连续同级结点展平并按规范序排序），实测可把 `1,2,3,4 → 24` 从 242 条降到 28 条
- [ ] 去重下沉到求值阶段（用规范形 key 直接进 `Set`），减少字符串拼接
- [ ] 提供两档输出：`全部字符串` / `本质不同的解`
- [ ] 剪枝：中间结果明显不可能命中时提前返回 `null`
- [ ] 修掉运算符枚举的 4 倍浪费（`SignAssign(size - 1)`）
- [ ] 按**值**而非下标去重排列（有重复数字时只枚举 multiset 的不同排列）
- [ ] 用**集合 DP** 替代树枚举（`dp[S] = 用集合 S 能得到的值`），量级从 `Catalan × 4^(n-1) × n!` 降到约 `3^n`，让 n=7/8 可行
- [ ] 溢出治理：`Rational` 换成 `BigInteger` 或加溢出检测
- [ ] 枚举器改为无状态（`Sequence` + `bucket.copyOf()`），为并行化铺路
- [ ] 恢复 `size = 6` 的用例，补 `size = 8` 的性能基准

### P2 —— 工程与体验

- [ ] **让长按阈值 `x` 可配置**（当前固定为系统默认 500 ms）：把 `LONG_PRESS_MS` 抽成可设置项（偏好设置或注入的 `ViewConfiguration` 值），`CLEAR` 与 `DEL` 共用同一来源，并同步 `TUTORIAL.md`
- [ ] 答案列表换成 `RecyclerView`（现在解数上千时是单个 TextView）
- [ ] 决定 FAB 的去留（当前注释保留；不用则删掉 `@dimen/fab_margin`）
- [ ] 把 `SyntaxTree.kt` / `Rational.kt` 抽成纯 Kotlin JVM 模块（`:solver`），Android 只做 UI
- [ ] 清理模板残留：`rules.keep`、`values-*` 里多余的 dimens
- [ ] 广告：把 `admob_app_id` 换成真实 ID（或确定不做广告后移除依赖 + meta-data + `Ads.kt`）
- [ ] 输出格式优化：去掉冗余括号（按优先级打印）、按复杂度排序、一键复制/导出
- [ ] 加 `values-zh/strings.xml` 提供中文界面
- [ ] 补「解集内容都真的等于 target」的验证型测试（随机输入做分数/浮点双重校验）
- [ ] 配置 CI（GitHub Actions：`./gradlew test` + lint，固定单测 JDK）
- [ ] 交互增强：允许「不用完所有数字」的变体模式、更多运算符（一元负号、`!`、拼接）

---

## 附录 A：复杂度速查

```
需要的循环次数（不含浪费）   = Catalan(n-1) · 4^(n-1) · n!
程序实际循环次数             = Catalan(n-1) · 4^n     · n!     （多跑 4 倍，见 §2.5）
Catalan(k)                   = C(2k, k) / (k+1)
本质不同排列数（有重复数字时）= n! / ∏(每个不同数字出现次数的阶乘)
集合 DP 方案的量级            ≈ 3^n ~ 4^n 级别（含子集合并与去重）
```

## 附录 B：结论是怎么验证的

- §2.5、§2.6 的循环次数、解集大小（242 / 26 / 1 / 2 …）与「交换结合等价类」（242 → 28）来自一份与 `SyntaxTree.kt` **逐结构同构**的 Python 复刻（同样的槽位编号、同样的四进制运算符向量、同样的排列顺序、`fractions.Fraction` 精确运算、同样的全括号打印）实跑得到。
- 界面行为（键盘布局/尺寸/配色、按键接线、拉杆与写回、CLEAR 点按与长按、DEL 长按与振动、校验、后台求解与结果往返）由 `FirstFragmentTest` 在 Robolectric 上真实驱动验证：启动 `MainActivity` → NavHostFragment → `FirstFragment`，点击/长按并断言文本、Toast、振动常量与布局参数。
- 广告防护由 `AdsTest` 通过 `PackageManager` 读取与 SDK 相同的 meta-data 来验证。
- 每轮改动的逐文件真实 diff 见 `diff_[time]_[date].MD`。
