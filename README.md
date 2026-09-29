# 24 Solver (Wakusei)

一个 Android + Kotlin 的「24 点」求解器：给定一组数字和一个目标值，**穷举出全部可能的解表达式**。

- 模块：`:app`（Android Application），包名 `com.a24solver.wakusei`
- 语言 / 工具链：Kotlin，Gradle (KTS)，AGP 9.4 / Gradle 9.6，`minSdk 24`，`compileSdk/targetSdk 37`，Java 11（字节码），viewBinding
- 应用名（`app_name`）：**24 Solver**；Gradle 根项目名：`24 Solver`
- 相关文档：本文件描述**当前**项目状态；[`diff.MD`](diff.MD) 记录最近一轮改动的逐文件 diff
- 文档日期：2026-09-29

---

## 1. 项目介绍

### 1.1 目标

求解器要回答的问题：

> 给定 `n` 个数（`1 ≤ n ≤ 8`，每个数的绝对值 `< 256`）和一个目标值 `target`，
> **把每个数恰好使用一次**，用 `+ - * /` 和任意加括号方式，列出**所有**能得到 `target` 的表达式。

虽然名字叫 24 Solver（默认目标是 24），但 `target` 是入参，任何整数目标都能求解（例如 `target=60`）。输入数字在 API 层面允许负数。

关键词是「**全部**」：这不是「找一个解就返回」的求解器，而是**完全枚举**——搜索空间不剪枝、不做启发式，只要不漏解就行。运算全程使用自己实现的有理数类，因此 `(1 + 1/9) * 9` 这类依赖分数中间结果的解不会因为浮点误差被漏掉或误判。

界面侧目前对输入做了更严格的限制（见 §3.3）：**候选数最多 5 个**、`|候选数| ≤ 256`、`|目标| ≤ 16384`——也就是说 App 实际会用到的是 `n ≤ 5` 的求解路径，求解器本身的能力上限仍是 `n = 8`。

### 1.2 目录结构

```
Wakusei/
├── README.md                     # 本文件（当前状态）
├── diff.MD                       # 最近一轮改动的 diff 摘要
├── settings.gradle.kts           # 根项目 "24 Solver"，include(":app")
├── build.gradle.kts / gradle/    # 版本目录 (libs.versions.toml)、wrapper
└── app/
    ├── build.gradle.kts          # 模块配置（viewBinding、依赖、单测 JVM、SDK 版本）
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/a24solver/wakusei/
        │   │   ├── SyntaxTree.kt      # ★ 求解核心（枚举 / 求值 / 打印）
        │   │   ├── Rational.kt        # ★ 精确有理数
        │   │   ├── MainActivity.kt    # Nav 模板壳（Toolbar + FAB + NavHost）
        │   │   ├── FirstFragment.kt   # ★ 输入界面：录入、校验、后台求解
        │   │   └── SecondFragment.kt  # 模板占位页（未来的结果页）
        │   ├── keepRules/rules.keep   # R8 保留规则（模板，全注释）
        │   └── res/
        │       ├── layout/fragment_first.xml   # 三个标签 + 数字键盘 + CLEAR/DEL + TARGET/NEXT + SOLVE
        │       ├── values/strings.xml          # 界面标签、按钮文字、提示与校验文案（全部资源化）
        │       ├── navigation/nav_graph.xml    # FirstFragment ↔ SecondFragment
        │       └── menu / values-* / drawable / mipmap / xml
        ├── test/java/com/a24solver/wakusei/
        │   ├── SyntaxTreeTest.kt      # 求解器：解的命中 + 枚举完整性
        │   ├── RationalTest.kt        # 有理数运算
        │   ├── FirstFragmentTest.kt   # ★ Robolectric 界面测试（10 例）
        │   └── ExampleUnitTest.kt
        └── androidTest/java/…         # 仪器测试（模板）
```

核心逻辑与 Android 框架**完全解耦**（`SyntaxTree.kt` / `Rational.kt` 不引用任何 `android.*` 类），因此可以直接用 `test/` 里的 JVM 单元测试驱动；界面逻辑则用 Robolectric 在 JVM 上驱动（见 §2.7）。

### 1.3 已实现 / 未实现

| 部分 | 状态 |
| --- | --- |
| 有理数运算（约分、比较、四则、除零保护） | ✅ 已实现并有单测 |
| 表达式树枚举 + 运算符枚举 + 排列枚举 | ✅ 已实现并有单测 |
| 求值（精确有理数、命中 target 即记入解集） | ✅ 已实现并有单测 |
| 表达式的字符串化（全括号中缀） | ✅ 已实现 |
| 界面：数字键盘录入 + DEL 退格 / CLEAR 清除 | ✅ 已修正接线，可正常编辑 |
| 界面：目标值可编辑（TARGET / NEXT 模式切换） | ✅ 已实现 |
| 调用前输入校验（≤5 个候选数、±256、±16384） | ✅ 已实现并有测试 |
| 连接求解器并放在后台线程调用 | ✅ 已实现（coroutines + `Dispatchers.Default`） |
| 界面自动化测试（点击 → 标签/Toast 断言） | ✅ Robolectric，10 个用例 |
| 结果展示（解的数量与列表） | ❌ 未实现：暂时只把 TARGET 标签改成 `SOLVE COMPLETE` 占位 |
| 解集渲染到 `SecondFragment`、导航过去 | ❌ 未实现（`nav_graph` 里的 action 仍未被使用） |
| 解的规范化去重（等价式合并） | ❌ 仅有「字符串级」去重 |
| 性能优化（剪枝 / 记忆化 / 并行） | ❌ 纯暴力枚举 |

---

## 2. 原理

### 2.1 一句话概括

`解集 = { 打印(树, 运算符分配, 排列) | 树 ∈ 全部满二叉树形状, 运算符 ∈ {+,-,*,/}^(n-1), 排列 ∈ S_n, 求值(…) == target }`

即：**「所有加括号方式」×「所有运算符组合」×「所有数字摆放顺序」**，三重笛卡尔积逐一求值。Kotlin 代码里就是 `SyntaxTree.solveIt` 的三个嵌套 `for`（`SyntaxTree.kt:54-63`）。

### 2.2 三个枚举器

**(1) 表达式树的形状 —— `generateTrees(size)`**（`SyntaxTree.kt:70-107`）

任何「用 `n` 个数做一次四则运算链」的表达式，对应一棵**叶子数 = n 的满二叉树**（每个内部结点恰好两个孩子）。`generateTrees` 用递归自相似的方式枚举所有形状：

```
T(1)     = 单个叶子
T(size)  = { 结点(左=T(leftSize), 右=T(size-leftSize)) | leftSize ∈ 1..size-1 }
```

形状数量是 Catalan 数 `C(n-1)`：n=4 → 5 种，n=8 → 429 种。

关键设计是**槽位编号（slot numbering）**：生成时给每个叶子分配 `leafIdx ∈ 0..n-1`、给每个内部结点分配 `signIdx ∈ 0..n-2`（`offset` / `offset2` 参数实现，且保证同一形状内编号不重复、刚好用满）。这样「树的形状」就与「用哪些数字、填哪些运算符」解耦了：形状只需枚举一次并可复用，数字与运算符分别由排列和运算符向量提供。`SyntaxTreeTest.testGenTrees` 正是在校验这个不重不漏的性质。

返回类型是 `Sequence<Node>`（惰性），避免一次性把整棵树铺满内存。

**(2) 运算符组合 —— `SignAssign(size)`**（`SyntaxTree.kt:133-157`）

运算符用整数编码：`0 → +`、`1 → -`、`2 → *`、`3 → /`（取模 4 兜底）。`SignAssign` 是一个 `Iterable<Array<Int>>`，内部就是一个**四进制计数器**：`hasNext()` = 数组里还有不是 3 的位；`next()` = 从低位进位加一。

**(3) 数字摆放 —— `permutations(size)`**（`SyntaxTree.kt:109-130`）

标准回溯（`vis` 数组 + `bucket`），惰性 `sequence` 产出 `n!` 个下标排列，把「第 k 个叶子用哪个输入数字」覆盖完。

### 2.3 求值与字符串化

- `resolve(root, signAssign, permutation)`（`SyntaxTree.kt:20-35`）：递归求值，叶子返回 `numbers[permutation[leafIdx]]`，内部结点按 `signAssign[signIdx] % 4` 做四则运算。**除法除以 0 时返回 `null`**，`null` 会沿树向上传播，整条分支作废——这就是除零保护，靠类型系统而不是异常。
- `myPrint(root, signAssign, permutation)`（`SyntaxTree.kt:37-51`）：与求值同构的递归，输出**全括号中缀**形式，如 `(((1 + (1 / 9)) * 9) * 6)`。
- 命中判定：`if (Rational(target.toLong()) == result)`。`Rational` 是 `data class`，相等性因此是「约分后分子分母分别相等」，精确比较、无浮点误差。
- 解集是 `MutableSet<String>`（`answerSheet`），最后 `toSet()` 返回——**去重单位是打印出来的字符串**。

### 2.4 精确有理数 `Rational`

（`Rational.kt`）`data class Rational(n: Long, d: Long = 1L)`：

- `init` 中：分母为 0 抛 `IllegalArgumentException`；分子为 0 时直接规范成 `0/1`；分母为负时把符号搬到分子；最后用 `gcd` 约分——**所有对象一经构造即为最简形式**，这是相等比较能直接用 data class 默认实现的前提。
- 提供 `add / subtract / multiply / divide`（除零返回 `null`）与运算符重载 `+ - * /`、一元 `+/-`、`compareTo`、`toString`（分母为 1 时只打印整数）。
- 选择「自己写有理数」而不是 `Double` 或 `BigDecimal` 是正确决定：24 点存在大量必须用分数表达的解（`1/3`、`1/7` 等），浮点会在相等判定上产生假阳性/假阴性。

### 2.5 搜索空间规模

`总循环次数 = Catalan(n-1) × 4^n × n!`（实测，用与 Kotlin 同构的 Python 复刻跑出）：

| n | 树形状 Catalan(n-1) | 运算符 4^n | 排列 n! | 实际循环次数 |
| --- | --- | --- | --- | --- |
| 2 | 1 | 16 | 2 | 32 |
| 3 | 2 | 64 | 6 | 768 |
| 4 | 5 | 256 | 24 | 30,720 |
| 5 | 14 | 1,024 | 120 | 1,720,320 |
| 6 | 42 | 4,096 | 720 | 123,863,040 |
| 7 | 132 | 16,384 | 5,040 | 10,899,947,520 |
| 8 | 429 | 65,536 | 40,320 | **1,133,594,542,080** |

（注意 `SignAssign(size)` 产出的是 `4^n` 个向量，但树里只有 `n-1` 个内部结点槽位，最后一个分量永远不被读取 → 白白多跑 4 倍；必要时应该是 `4^(n-1)`。）

界面当前的校验把候选数限制在 5 个，所以 App 最坏情况是 n=5（约 172 万次循环，后台线程上秒级完成）；n≥6 只能通过直接调用 API 触发。

### 2.6 实测：解的冗余度

同样用同构复刻在实际跑过（精确分数运算）：

| target | 数字 | 实际循环次数 | 解集大小（字符串） |
| --- | --- | --- | --- |
| 24 | 1, 2, 3, 4 | 30,720 | **242** |
| 24 | 3, 3, 8, 8 | 30,720 | 1 |
| 24 | 5, 5, 5, 1 | 30,720 | 2 |
| 24 | 23, 23, 23, 23 | 30,720 | 2 |
| 60 | 1, 1, 9, 9, 6 | 1,720,320 | 26 |

对 `1,2,3,4 → 24` 的 242 条解，如果按「加法/乘法的**交换律 + 结合律**」做规范化（把 `(a+b)+c`、`a+(b+c)`、`(b+a)+c`… 归成同一个规范形），只剩 **28 个本质上不同的解**。也就是说当前输出的解集里约 **88% 是等价变形**。——这直接决定了「全部解」这个词到底指什么（见 §4.2）。

### 2.7 测试

| 测试类 | 覆盖内容 | 运行环境 |
| --- | --- | --- |
| `RationalTest`（7 例） | 构造/约分/符号规范化、加减乘除、除零抛错与返回 `null`、比较运算 | JVM |
| `SyntaxTreeTest`（8 例） | `solveIt` 命中（含 `((9 * 8) / 6)`、依赖分数的 `((1 + (1 / 9)) * 9) * 6`）、`permutations` / `SignAssign` / `generateTrees` 不重不漏 | JVM |
| `FirstFragmentTest`（10 例，本轮新增） | 数字键接线、DEL/CLEAR 编辑、候选数上限与 ±256、目标值模式与 ±16384、无数字拒绝求解、SOLVE 的后台语义与 `SOLVE COMPLETE` 占位、求解器异常上报 | Robolectric（JVM 上跑真界面） |
| `ExampleUnitTest`（1 例） | 模板用例 | JVM |

运行方式：

```bash
cd F:/code/Wakusei
./gradlew.bat :app:testDebugUnitTest        # 全部单元测试（26 个）
./gradlew.bat :app:assembleDebug            # 打 debug APK
./gradlew.bat :app:compileDebugKotlin       # 只编译（改完代码最快的一次自检）
```

两个坑（已在 `app/build.gradle.kts` 里处理）：

- Robolectric 需要 `testOptions.unitTests.isIncludeAndroidResources = true` 才能看到合并后的资源与 manifest。
- Gradle 守护进程运行在 **JDK 25** 上，而 Robolectric 4.14.1 内置的 ASM 读不了 Java 25 的平台类（`Unsupported class file major version 69`），所以单测任务被显式固定到本机 **JDK 21**；应用与测试字节码本身仍是 Java 11。

`SyntaxTreeTest.kt:54-61` 里 `size = 6`（`1,1,9,9,6,7 → 420`）的用例仍被注释掉——见 §4.3。

---

## 3. 当前实现：界面与求解器是怎么连起来的

（这一节描述本轮完成的部分；逐行改动见 [`diff.MD`](diff.MD)。）

### 3.1 键盘与显示

```
TARGET: 24                  ← text_target：目标值 / SOLVING… / SOLVE COMPLETE
NUMBERS: 1, 2, 3            ← text_numbers：已提交的候选数
INPUT: 12                   ← text_current_input：正在输入的缓冲（目标模式下显示 TARGET INPUT:）

[ 1 ] [ 2 ] [ 3 ]
[ 4 ] [ 5 ] [ 6 ]
[ 7 ] [ 8 ] [ 9 ]
[CLEAR][ 0 ][DEL]
[ TARGET ][ NEXT ]
[       SOLVE         ]
```

- 三个标签的文本**全部来自 `strings.xml` 的格式串**，由 `FirstFragment.refreshDisplay()` 写入；XML 里只剩下用于 IDE 预览的 `tools:text`。
- 数字键与数字的映射改为**表驱动**（`mapOf(binding.button0 to 0, …)`），历史上的「3 号键输入 0」这类错位不会再出现，`FirstFragmentTest.everyDigitKeyTypesItsOwnDigit` 会同时校验按键文字与输入结果。

### 3.2 两种输入模式

- **NUMBER 模式**（默认）：数字键追加到输入缓冲，`DEL` 退格，`CLEAR` 清空缓冲；缓冲已空时再按 `CLEAR` 会**撤销最后一个已提交的候选数**（有 Toast 提示）。`NEXT` 提交数字并校验。
- **TARGET 模式**：按 `TARGET` 进入，缓冲预填当前目标值以便修改，`NEXT` 提交目标值并回到 NUMBER 模式；再按一次 `TARGET` = 取消。

### 3.3 调用前的校验

`FirstFragment.validate()` 在调用求解器**之前**执行，返回错误文案（null 表示通过）：

| 规则 | 常量 | 失败时 |
| --- | --- | --- |
| 候选数个数 ≤ 5 | `MAX_NUMBERS = 5` | Toast「At most 5 candidate numbers are allowed」 |
| 每个候选数 `|x| ≤ 256` | `MAX_ABS_NUMBER = 256` | Toast「Each candidate number must be within ±256」 |
| 目标值 `|target| ≤ 16384` | `MAX_ABS_TARGET = 16384` | Toast「The target must be within ±16384」 |
| 至少要有一个候选数 | — | Toast「Add at least one number …」 |

同样的规则也在**录入时**即时生效（超限的数字根本提交不进列表）。另外，按 `SOLVE` 时若缓冲里还有没按 `NEXT` 的数字，会先自动提交——保证「校验的就是屏幕上看到的」。

### 3.4 后台求解

```kotlin
solveJob = viewLifecycleOwner.lifecycleScope.launch {
    val outcome = withContext(Dispatchers.Default) {
        runCatching { SyntaxTree.solveIt(targetSnapshot, candidates) }
    }
    …
}
```

- 求解在 `Dispatchers.Default`（后台线程）上执行，界面线程不阻塞；期间 `SOLVE` 按钮禁用、标签显示 `SOLVING…`，Fragment 销毁时 job 被取消。
- 求解器对不符合自身 `require` 的输入抛 `IllegalArgumentException`，这里用 `runCatching` 捕获，转成 Toast「Solver rejected the input: …」并把标签恢复为 `TARGET: n`，不会崩溃。

### 3.5 结果的处理（占位）

求解成功后只做两件事：把解集存进 `firstFragment.lastAnswers`、用 `Log.d` 打出条数，并把 `TARGET` 标签改成 **`SOLVE COMPLETE`**。真正的结果展示（数量 + 列表 + 跳到 `SecondFragment`）尚未实现，`showSolveComplete()` 里留了 TODO。

---

## 4. 局限

### 4.1 曾经的接线问题（本轮已修复）

改动前：`button3 → 0`、`button4 → 1`、`button5 → 2`、`button6 → 0`、`button7 → 1`、`button8 → 2`、`button9 → 0`（数字键整体错位 2/3）；`SOLVE` 与 `TARGET` 两个按钮根本没有监听；`FirstFragment` 从不调用 `SyntaxTree.solveIt`——算法有单测、但 App 跑不出任何答案。以上均已修复并有测试覆盖。

仍未处理的界面缺口：**没有结果页**（`SecondFragment` 仍是模板占位页，`nav_graph` 里的 `action_FirstFragment_to_SecondFragment` 从未被触发）；**不能输入负数**（只有数字键，没有 `±`）；没有历史记录 / 编辑已提交候选数的能力（只能靠 CLEAR 撤销最末一个）。

### 4.2 「全部可能解」目前是「全部字符串」，不是「全部本质不同的解」

去重单位是表达式的打印字符串（`Set<String>`），所以下面这些**语义完全相同的解会被当成不同的解**全部列出：

- 交换律：`(12 + 13)` 与 `(13 + 12)`；
- 结合律：`((a + b) + c)` 与 `(a + (b + c))`；
- 由交换/结合再叠加排列产生的整体等价（数字有重复时更严重）。

实测 `1,2,3,4 → 24`：242 条字符串 vs 28 个交换/结合等价类。因此若产品目标是「展示所有解法」，会淹没在等价变形里；若目标是「枚举所有字符串」，那这是符合预期的——**这个语义必须在文档/界面里明确**，目前没有定义「解」的规范形式。

另外没有做的等价消减：`a - b` 与 `a + (-b)`、`a / b` 与 `a * (1/b)` 等代数变形不会被合并。

### 4.3 性能：搜索空间远超手机能承受的范围

- 循环次数按 §2.5 增长，**n = 8 是 `1.13 × 10^12` 次**——单线程 JVM 级别也要几天到几年，Android 上必然超时/OOM/被 ANR 杀掉。n = 7 已是 `1.1 × 10^10`。
- 这就是 `SyntaxTreeTest` 里 `size = 6` 用例被注释掉的原因（约 1.2 亿次循环 + 精确分数运算）。
- 界面把候选数限制在 5 个，掩盖了这个问题：n ≤ 5 在后台线程上是秒级，但求解器本身没有上限保护（只受 `require(size in 1..8)` 约束）。
- 没有任何剪枝：不判断中间结果是否还有希望命中 target，不缓存子表达式结果，不排除「同一个子集重复求解」。
- 排列枚举按**下标**而非按**值**去重，输入里有重复数字时（如 `1,1,9,9,6`、`5,5,5,1`）会重复计算大量完全相同的表达式，只靠最后字符串 `Set` 掩盖。
- 运算符枚举多跑了 4 倍（§2.5 的 `4^n` vs 实际需要的 `4^(n-1)`）。
- 每个候选解都要 `myPrint` 重新走一遍树并拼接字符串（`String` 拼接在叶子层），命中率高时开销不小。
- `resolve` / `myPrint` 是递归闭包，且 `answerSheet` 是 `MutableSet<String>`，没有并发；`Rational` 每次都新建对象，中间结果对象分配量大。

### 4.4 边界不一致：256 的双重标准

界面按需求实现 `|候选数| ≤ 256`，而 `SyntaxTree.solveIt` 内部是 `require(inputArray.all { abs(it) < 256 })`。于是 **256 能通过界面校验、却被求解器拒绝**（当前表现为 Toast「Solver rejected the input: …」，不崩溃，但用户体验上是个谜）。需要二选一统一：界面改成 `< 256`，或求解器放宽到 `≤ 256`。

### 4.5 数值与类型问题

- `Rational` 全程用 `Long` 计算且不检查溢出：`n * other.d`、`this.d * other.d` 在嵌套运算里分母会迅速变大，溢出后**静默给出错误结果**（可能产生假阳性解）。`compareTo` 里的叉乘 `this.n * other.d` 同样可能溢出。
- `divide` 返回可空 `Rational?`，导致调用方到处 `!!`（如 `(r1 + (r2 / r3)!!)`），除零分支一旦被漏判就是 `NullPointerException`。
- `data class Rational` 里有 `private var n / d` 与注释掉的 `copy()` / `equals()`：`equals` 用 data class 自动生成的版本（基于 `n`、`d`）恰好正确，但这依赖「构造即约分」的不变式，很脆弱——若将来允许原地修改分子分母，相等性立刻失效。

### 4.6 工程与代码质量

- `SignAssign` 是**有状态、单次可迭代**的（`isNew` 标志 + 复用同一个数组对象）；`permutations` 反复 `yield` 的是同一个 `bucket` 数组引用。当前调用方式恰好没踩到（`resolve` 立即消费），但任何「保存起来稍后处理」「迭代两遍」「改成并行」的改动都会静默出错。
- `SyntaxTree.solveIt` 是 `companion object` 里的巨型函数（局部函数嵌套三层循环），既不好单测细分，也不方便做剪枝/并行改造。
- `FirstFragment` 已清掉 Compose 残留（原文件的 `Composable` import 与文件尾注释掉的 Compose 版本），界面技术路线现在是 View/ViewBinding；若将来迁 Compose，`MainActivity` / `SecondFragment` 与布局文件要一起改。
- `app/build.gradle.kts` 仍引入未使用的 `play-services-ads`；`keepRules/rules.keep` 仍是模板注释；`values` 里 `lorem_ipsum`、`SecondFragment` 的 "Previous" 都是模板残留。
- 无版本控制（没有 `git` 仓库）、无 CI、无 lint/detekt 配置；`Rational.kt` / `SyntaxTree.kt` 虽然是纯 JVM 逻辑却放在 `app` 模块里，只能跑 `testDebugUnitTest`。
- 界面文案是英文（与原始 UI 一致），没有做多语言资源。

### 4.7 能力边界（设计上的取舍）

- 只支持二元运算符 `+ - * /`：没有一元负号、没有指数/取模/拼接（有些 24 点变体允许 `4!`、`√`、拼接 `12`）。
- 每个数必须**恰好用一次**且**全部用上**：不支持「抽取子集求解」「允许不用某些数」。
- 求解器输入规模硬限制 `1..8`，且每个数 `|x| < 256`；界面进一步限制为 ≤5 个、`|x| ≤ 256`、`|target| ≤ 16384`。超限时求解器抛 `IllegalArgumentException`（界面已捕获为提示），而不是返回可展示的业务错误。
- 解的表达形式只有全括号字符串，不产出求值步骤序列、不排序、不标注解的类型/难度。

---

## 5. TODO LIST

### P0 —— 让它真的能用

- [x] 修 `setupDigitButtons()` 的数字映射（`buttonN → N`），并改为表驱动防止复发（`FirstFragment.setupButtons()`）。
- [x] 把界面接到求解器：`SOLVE` 按钮 → 校验 → `SyntaxTree.solveIt`；`TARGET` 按钮 → 目标值编辑模式。
- [x] 目标值可编辑：`text_target` 不再硬编码 24，默认 24 且可改。
- [x] 输入校验：候选数 ≤ 5、`|候选数| ≤ 256`、`|目标| ≤ 16384`，违规用 Toast 提示而不是崩溃。
- [x] 求解放到后台线程（coroutines + `Dispatchers.Default`），期间禁用按钮并显示 `SOLVING…`。
- [x] 输入可编辑：新增 `CLEAR` / `DEL` 键，CLEAR 在空缓冲时撤销最后一个候选数。
- [ ] 结果展示页：把 `lastAnswers` 渲染成可滚动列表（解的数量 + 内容）到 `SecondFragment`，接上 `action_FirstFragment_to_SecondFragment`；处理「无解」与大数据量的分页/懒加载。（现在只显示 `SOLVE COMPLETE` 占位）
- [ ] 决策 §4.4 的 256 边界：统一为 `|x| < 256` 或把求解器放宽到 `≤ 256`。
- [ ] 允许负数输入（键盘加 `±` 或让 DEL/CLEAR 之外支持前导负号），校验同步覆盖。
- [ ] `git init` 并提交当前状态，让后续改动有真实 diff 可查。

### P1 —— 让「全部解」这个目标站得住（语义 + 规模）

- [ ] **明确并实现「解」的规范形式**：推荐做交换律 + 结合律规范化（把 `+` / `*` 的连续同级结点展平并按规范序排序），从而得到「本质不同的解」。实测 `1,2,3,4 → 24` 会从 242 条降到 28 条。
- [ ] 去重下沉到求值阶段：用规范形 key 直接进 `Set`，避免生成后再去重，同时减少字符串拼接开销。
- [ ] 提供两档输出（可选）：`全部字符串（保留原样）` / `本质不同的解`，让用户/测试各取所需。
- [ ] 剪枝：中间结果超出可行范围（例如绝对值超过 `target` 的量级且只可能继续放大）时提前返回 `null`；对纯 `+`/`*` 分支做上界剪枝。
- [ ] 修掉运算符枚举的 4 倍浪费：`SignAssign(size)` → `SignAssign(size - 1)`（或只迭代前 `n-1` 位）。
- [ ] 按**值**而非下标去重排列：输入有重复数字时只枚举 multiset 的不同排列（`1,1,9,9,6` 的 5! = 120 → 30 个本质不同排列），可再省数倍。
- [ ] 用**集合 DP**替代树枚举：`dp[S] = { 用集合 S 能得到的所有值 → 一个代表表达式 }`，`|S|` 从 1 到 n 逐层合并，复杂度从 `Catalan × 4^(n-1) × n!` 降到约 `3^n` 量级；这是让 n=7/8 可行的关键，也是「不漏解」的标准做法。
- [ ] 溢出治理：`Rational` 换成 `BigInteger`（或加带检测的 `Long` 运算 + 溢出回退），`compareTo` 用 `BigInteger` 交叉相乘；给 `Rational` 加不变量测试（大分母用例）。
- [ ] 让枚举器无状态：`SignAssign` 改成 `Sequence<Array<Int>>` 且每次产出新数组；`permutations` 同样 `yield` 副本（`bucket.copyOf()`），为后续并行化铺路。
- [ ] 恢复到 `size = 6` 的测试用例（优化后应能秒级跑完），并补 `size = 8` 的性能基准测试（带超时断言）。

### P2 —— 工程与体验

- [ ] 把 `SyntaxTree.kt` / `Rational.kt` 抽成纯 Kotlin JVM 模块（`:solver`），Android 只做 UI —— 测试更快、可复用（CLI / 桌面 / 服务端），并能给求解器单独加基准测试。
- [ ] 清理无用依赖与模板残留：`play-services-ads`（未使用）、`rules.keep` 模板、`lorem_ipsum`、`SecondFragment` 的 "Previous"。
- [ ] 输出格式优化：去掉冗余括号（按运算符优先级与结合性打印，如 `1 + 1/9` 而非 `((1 + (1 / 9))`）、按「步骤数/复杂度」排序、支持复制/导出解集为文本。
- [ ] 界面文案资源化已就绪，如需中文界面只需替换 `strings.xml`（或加 `values-zh/strings.xml`）。
- [ ] 补齐文档与测试：本 README 与 `diff.MD` 持续维护；给 `solveIt` 加「解集内容里没有非法中间结果」「所有解确实等于 target」的验证型测试（随机输入做分数/浮点双重校验）；补仪器测试或 Compose UI 测试。
- [ ] 加分/减分细节：允许选择「不用完所有数字」的变体模式、支持更多运算符（一元负号、`!`、拼接）。
- [ ] 配置 CI（GitHub Actions：`./gradlew test` + lint，并固定单测 JDK 版本），至少保证 `testDebugUnitTest` 在每次提交时通过。

---

## 附录 A：复杂度速查

```
需要的循环次数（不含浪费）   = Catalan(n-1) · 4^(n-1) · n!
程序实际循环次数             = Catalan(n-1) · 4^n     · n!     （多跑 4 倍，见 §2.5）
Catalan(k)                   = C(2k, k) / (k+1)
本质不同排列数（有重复数字时）= n! / ∏(每个不同数字出现次数的阶乘)
集合 DP 方案的量级            ≈ 3^n ~ 4^n 级别（含子集合并与去重）
```

n = 4 时 30,720 次循环、n = 8 时约 1.13 × 10^12 次——这就是「暴力枚举能到 n=6，n≥7 必须换算法」的分界线（界面当前把输入限制在 n ≤ 5）。

## 附录 B：验证方法

- §2.5、§2.6 的循环次数、解集大小（242 / 26 / 1 / 2 …）与「交换结合等价类」（242 → 28）来自一份与 `SyntaxTree.kt` **逐结构同构**的 Python 复刻（同样的树枚举槽位编号、同样的四进制运算符向量、同样的 `permutations` 顺序、`fractions.Fraction` 精确运算、同样的全括号打印），逐用例实跑得到；不是估算值。
- 界面行为（按键接线、编辑、校验、后台求解与 `SOLVE COMPLETE` 占位）由 `FirstFragmentTest` 在 Robolectric 上真实驱动验证：启动 `MainActivity` → NavHostFragment → `FirstFragment`，点击按钮并断言标签文本与 Toast。
- 最近一轮改动的逐文件 diff 见 [`diff.MD`](diff.MD)。
