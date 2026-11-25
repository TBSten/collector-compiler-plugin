# Collector

アノテーションを付けた宣言をコンパイル時に集める Kotlin コンパイラプラグイン。JVM 限定の `ServiceLoader` の代わりに、KMP の全ターゲットで使える（CI では JVM / JS / Wasm / linuxX64 / macosArm64 で確認している）。

```kotlin
annotation class ApiRoutes

@CollectorFor<ApiRoutes>()
object ApiRoutesCollector : Collector<Route.() -> Unit>

@ApiRoutes fun Route.userRoutes() { /* ... */ }
@ApiRoutes fun Route.adminRoutes() { /* ... */ }

// 呼び出しを含むモジュールのコンパイル時に listOf(...) へ置き換わる（実行時の走査やリフレクションはない）
val routes: List<Route.() -> Unit> = ApiRoutesCollector.collectAsList()
```

> 0.1 開発中。仕様と設計はプロジェクトの「Collector ユーザ仕様」「Collector コンパイラ設計書」を参照。

## 導入

```kotlin
// build.gradle.kts（アイテムを含むモジュールと、取り出すモジュールの両方）
plugins {
    kotlin("multiplatform") version "2.4.21" // jvm / android も可
    id("me.tbsten.collector") version "0.1.0" // 公開前
}
```

Gradle プラグインが runtime（`me.tbsten.collector:collector-runtime`）の追加とコンパイラプラグインの有効化を行う。設定項目はない。

対応する Kotlin は **2.4.21 のみ**。コンパイラの内部 API に依存するため、ほかの版ではビルドの最初に理由つきで失敗する。どうしても試す場合は `gradle.properties` に `collector.skipKotlinVersionCheck=true` を書く（壊れても保証しない）。

## 使い方

### 1. 集める型を決める

```kotlin
@CollectorFor<X>()                 // X: 集めたい宣言に付けるアノテーション（自前でも外部ライブラリのものでもよい）
object XCollector : Collector<T>  // T: 取り出すときの型
```

- 1 つのアノテーションに Collector を複数置ける（型や用途ごとに分けられる）。
- `@CollectorFor` は repeatable。1 つの Collector で複数のアノテーションを集められる。
- 自前のアノテーションなら、Collector を同じファイルかアノテーションの中に置くと、アノテーションを使う全モジュールから必ず見える（下の「制約」を参照）。

### 2. 宣言にアノテーションを付ける

宣言は Collector の `T` に合わせて値に変換される。

| 宣言 | T が普通の型 | T が関数型 | T が `KClass<…>` |
| --- | --- | --- | --- |
| object / companion / enum のエントリ | その値 | `{ Obj }` | object のみ `Obj::class` |
| 引数なしで作れる class | `Cls()`（毎回 new） | `{ Cls() }` | `Cls::class` |
| 引数ありの class | ― | `{ a, b -> Cls(a, b) }` | `Cls::class` |
| interface / abstract / sealed / enum class | ― | ― | `Cls::class` |
| トップレベル・object 内の val / var | 値 | 値が関数型なら値 | ― |
| トップレベル・object 内の関数、拡張関数 | ― | `{ a -> fn(a) }`（拡張関数型・suspend・@Composable も可） | ― |

- T の関数型は typealias 経由でもよい（`typealias Content = @Composable () -> Unit`）。`KFunction1<…>` のようなリフレクションの型は関数型として扱わないので、関数を集めるなら関数型で書く。
- 「引数なしで作れる」は、public か internal のコンストラクタで、どの引数にもデフォルト値があるか vararg であるもの（`class V(vararg xs: String)` は `V()`）。
- `@Composable get()` を持つプロパティは集められない（警告）。
- private な宣言も集められる（公開用の橋渡し関数が生成される）。
- class を作るときに呼ぶコンストラクタは public か internal のものだけ（private・protected は対象外）。
- `Any` のようにインスタンスも `KClass` も受け取れる T では、作れる class はインスタンスになり、作れないもの（interface や、呼べるコンストラクタのない class）は `Cls::class` になる。
- class のメンバー、inner class、ローカル宣言、引数、型エイリアスに付けたものは集めない（警告 `ITEM_NOT_ALLOWED_HERE`）。エラーにはしないので、`@Serializable` のようにメンバーにも付けるアノテーションを X にしても、既存のコードはそのままコンパイルできる。
- 変換できない組み合わせは、その Collector では集めない。見えているどの Collector にも合わなければ警告。
- 1 つのファイル、または 1 つの object の中では、集める宣言の名前を重複させられない（オーバーロードや、同名の関数とプロパティはエラー）。同じパッケージの別ファイルなら同じ名前でよい（画面ごとのファイルに置く `private fun Preview()` など）。
- 細かい入力の扱い:
  - `@get:X` / `@field:X` は対象外で、`@X` と `@property:X` だけを集める。
  - expect に付けた場合は actual の 1 件として数える。アノテーションは expect に付けたものを使う（actual で繰り返さなくてよい）。actual にだけ付けたアノテーションは集められず、警告（`ITEM_ANNOTATION_ONLY_ON_ACTUAL`）になるので、expect にも付ける。expect にアノテーションのない宣言は、actual がそのままアイテムになる。
  - 同じ宣言に同じアノテーションを 2 回付けても 1 件。`collectWithAnnotation` ではアノテーションごとに 1 件ずつになる。

### メタアノテーション（マルチプレビュー）

X を付けたアノテーションクラス M は、使われた場所で X として数える（Compose のマルチプレビューと同じ考え方。入れ子も可）。M 自体はアイテムにならない。

```kotlin
@Preview(name = "light") @Preview(name = "dark")
annotation class ThemePreviews

@ThemePreviews @Composable fun ButtonPreview() { … }   // PreviewCollector に 1 件
// collectWithAnnotation では light と dark の 2 件
```

### 3. 取り出す

```kotlin
XCollector.collectAsList()        // List<T>
XCollector.collectAsSet()         // Set<T>
XCollector.collectAsSequence()    // Sequence<T>（要素に達したときに作る）
XCollector.collectAsArray()       // Array<T>
XCollector.collectSingle()        // T（0 件・2 件以上はコンパイルエラー）
XCollector.collectSingleOrNull()  // T?（2 件以上はコンパイルエラー）

val all by XCollector.lazyCollectAsList()   // 上のすべてに lazy 版がある（Lazy<…>）
```

- 順序は宣言の FQN 順で固定。FQN が同じ宣言（別モジュールや別ファイルの同名）は、モジュール名、ファイル名の順に並ぶ。
- 受け手は `@CollectorFor` の object を直接指す必要がある（`with(XCollector) { collectAsList() }`、`run`、`apply` も可）。変数経由、`let` の `it`、関数参照（`XCollector::collectAsList`）はエラー。
- 結果は **呼び出しを含むモジュールのコンパイル時** に決まる。

### タグでまとめて取り出す

```kotlin
@CollectorFor<PluginA>(tag = "plugins") object PluginACollector : Collector<Plugin>
@CollectorFor<PluginB>(tag = "plugins") object PluginBCollector : Collector<Plugin>

val plugins = collectAsList<Plugin>("plugins")   // 同じタグの Collector を全部まとめる（重複は 1 件に）
```

- `@CollectorFor` の `tag` には文字列リテラルだけを書ける。呼び出し側のタグは const val や、それらを `+` や文字列テンプレートでつないだものでもよい（`PREFIX + "x"`、`"${PREFIX}x"`）。
- 同じタグの全 Collector の T が、要求した型のサブタイプである必要がある。
- Collector が 1 つもないタグは空になる（`collectSingle` はエラー、`collectSingleOrNull` は null）。

### アノテーションの値と一緒に取り出す

```kotlin
annotation class Screen(val path: String)
@CollectorFor<Screen>() object ScreenCollector : Collector<Page>

val pairs: List<Pair<Screen, Page>> = ScreenCollector.collectWithAnnotation()
val byPath: Map<Screen, Page> = ScreenCollector.collectAsMap()   // 同じキーが 2 つあればコンパイルエラー
```

- `collectAsMap` のキーの重複は、書かれた引数どうしをコンパイル時に比べる（リテラルと const val、`[…]` と `arrayOf(…)`、別モジュールのアイテムとの組も同じ値なら重複）。引数を省略したもの（デフォルト値）と、同じ値を明示したものはコンパイル時には見分けられず、実行時に `IllegalStateException` になる。

## 制約

- **アイテムを含むモジュールから Collector が見えている必要がある。** Collector が見えないモジュールのアイテムは集められない。自前のアノテーションなら、Collector を同じファイルに置けば必ず見える。
  - ソースセットでも同じ。Collector を `jvmMain` などプラットフォーム側に置くと、`commonMain` のアイテムからは見えないので集められない。Collector は `commonMain` に置く。
  - 見えずに集められなかった宣言があると、取り出す側の呼び出しで警告（`COLLECT_MISSED_ITEMS`）が出る。警告にはモジュールごとに例を 1 つ挙げる（全件の一覧ではない）。同じアノテーションの別の Collector だけが見えていた場合（共通モジュールの Collector に加えて、アプリ側で Collector を足したときなど）も同じ。
- **アノテーションを付けるモジュールすべてにプラグインを適用する。** 適用していないモジュールの宣言は、警告なしで対象外になる（プラグインが動かないので、集められなかったことも記録できない）。
- **取り出すモジュールから、アイテムを含むモジュールが見えている必要がある。** `implementation` の推移依存の先にあるアイテムは見えないので、`api` で公開する。
- ほかのバージョンの Collector でビルドされたモジュールが混ざると、エラー（`HINT_FORMAT_MISMATCH`）で再ビルドを促す。
- **`@CollectorFor` はその名前のまま書く。** import の別名（`import …CollectorFor as Register`）や typealias 経由だと Collector として登録されないので、エラー（`COLLECTOR_FOR_ALIASED`）にしている。
- **`@CollectorFor<X>` の X を、同じクラスの中にネストしたアノテーションの単純名で書くことはできない。** `object Routes { annotation class Api; @CollectorFor<Api>() object C … }` は `Unresolved reference` になるので、`@CollectorFor<Routes.Api>()` と書く。
- **型パラメータを持つアノテーションクラスは X にできない**（エラー `COLLECTOR_FOR_GENERIC_ANNOTATION`）。型引数の違いを区別しないため。`@CollectorFor` 自身も型パラメータを持つので、Collector を集める Collector（`@CollectorFor<CollectorFor<*>>`）は書けない。
- アイテムに付けたアノテーションは import、import の別名、typealias、ネストしたクラス名のどの書き方でも、コンパイラが解決するクラスで判定する。`expect annotation class` と `actual typealias` の組も同じ 1 つのアノテーションとして集まる。
  - 例外として、**外側のクラスの親クラスにネストしたアノテーションを単純名で書いた場合**だけはプラグインが追えず、エラー（`ANNOTATION_MISREAD`）になる。`@Base.FromSuper` のように親クラス名から書けば集まる。
- Java で宣言したアノテーションも集められる（同じモジュールの Java ソースでも、ライブラリのものでもよい）。アイテムになるのは Kotlin の宣言だけで、Java のクラスやメソッドに付けても集まらない。
  - **同じモジュールの Java ソースで宣言したアノテーションクラスは、メタアノテーションとしては使えない。** そのクラスに付いたアノテーションをプラグインが読めないので、エラー（`META_ANNOTATION_IN_JAVA_SOURCE`）になる。Kotlin で宣言するか、別のモジュールに置く（別モジュールの Java のものはメタアノテーションとして使える）。
- メタアノテーションに付けた X は、そのメタアノテーションを使うモジュールが読み、引数はアイテムのあるファイルで組み立て直す。そのため次の 2 つは宣言の時点で知らせる。
  - **X が `@Retention(SOURCE)` だと、コンパイル後のメタアノテーションに X が残らない。** ほかのモジュールでそのメタアノテーションを付けた宣言は集まらないので、公開されたメタアノテーションには警告（`META_ANNOTATION_SOURCE_RETENTION`）を出す。X を BINARY か RUNTIME にするか、X を直接付ける。
  - **X の引数から private なクラスは参照できない**（`@X(Hidden::class) annotation class M`）。アイテムのファイルからは見えないので、エラー（`META_ANNOTATION_PRIVATE_ARGUMENT`）にしている。メタアノテーション自体が private なら同じファイルでしか使えないので問題ない。

## コンパイルエラーと警告

| 診断 | 内容 |
| --- | --- |
| `RECEIVER_NOT_COLLECTOR_OBJECT` | 受け手が `@CollectorFor` の object を直接指していない |
| `COLLECT_SINGLE_COUNT` / `COLLECT_SINGLE_OR_NULL_COUNT` | `collectSingle*` の件数が合わない |
| `COLLECT_TAG_NOT_CONSTANT` / `COLLECT_TAG_TYPE_MISMATCH` | タグが定数でない / タグの Collector の T が合わない |
| `COLLECT_ANNOTATION_TYPE_MISMATCH` / `COLLECT_AS_MAP_DUPLICATE_KEY` | アノテーションの型が合わない / Map のキーが重複 |
| `COLLECT_CALLABLE_REFERENCE` | `XCollector::collectAsList` のような参照 |
| `COLLECTOR_FOR_NOT_OBJECT` / `COLLECTOR_FOR_NOT_COLLECTOR` / `COLLECTOR_FOR_TAG_NOT_LITERAL` / `COLLECTOR_FOR_ALIASED` | `@CollectorFor` の付け方の誤り（object でない、`Collector<T>` を実装していない、タグがリテラルでない、別名や typealias 経由で書いた） |
| `COLLECTOR_FOR_NOT_ANNOTATION_CLASS` / `COLLECTOR_FOR_GENERIC_ANNOTATION` | `@CollectorFor<X>` の X が annotation class でない（`Nothing`、`Annotation`）/ 型パラメータを持つ annotation class（型引数の違いは区別しないため、集められない） |
| `ANNOTATION_MISREAD` | アノテーションをプラグインがコンパイラと違うクラスに読んだ（上の制約を参照）。完全修飾名で書くか、明示的に import する |
| `META_ANNOTATION_IN_JAVA_SOURCE` | 同じモジュールの Java ソースで宣言したアノテーションクラスを、メタアノテーションとして使った |
| `META_ANNOTATION_PRIVATE_ARGUMENT` | メタアノテーションに付けた X の引数が private なクラスを参照している |
| `ITEM_NAME_CLASH` | 1 つのファイルか object の中に同じ名前の宣言が 2 つ（オーバーロード、同名の関数とプロパティ） |
| `HINT_FORMAT_MISMATCH` | 別バージョンの Collector でビルドされたモジュール |
| `ITEM_NOT_ALLOWED_HERE`（警告） | 集められない場所に付いている（class のメンバー、inner class、ローカル宣言、引数、型エイリアス）。その宣言は集めない |
| `ITEM_NOT_CONVERTIBLE`（警告） | どの Collector の T にも変換できない |
| `ITEM_ANNOTATION_ONLY_ON_ACTUAL`（警告） | expect がアイテムの宣言で、actual にだけ付けたアノテーションがある（集められない） |
| `COLLECT_MISSED_ITEMS`（警告） | その Collector が見えないモジュールやソースセットで付けられ、集められなかった宣言がある |
| `META_ANNOTATION_SOURCE_RETENTION`（警告） | 公開されたメタアノテーションに付けた X が SOURCE retention（ほかのモジュールからは見えない） |
| `COLLECTOR_FOR_DUPLICATE`（警告） | 同じ `@CollectorFor<X>` の重複 |

## モジュール

| パス | 中身 |
| --- | --- |
| `collector-runtime` | `@CollectorFor` / `Collector` / `collect*` 関数（KMP） |
| `collector-compiler` | コンパイラプラグイン本体（FIR で hint を宣言し、IR で body と呼び出しを埋める） |
| `collector-gradle-plugin` | `me.tbsten.collector` Gradle プラグイン |
| `integration-tests` | 複数モジュールの KMP 構成で実際にビルドして確かめる別ビルド |

## 開発

```sh
./gradlew :collector-compiler:test :collector-gradle-plugin:test
cd integration-tests
./gradlew allTests              # jvm / js / wasmJs / linuxX64（macOS では macosArm64 も）
./scripts/ic-check.sh           # インクリメンタルビルドの確認
./scripts/error-check.sh        # コンパイルエラー・警告の確認（// E: / // W: マーカーと照合）
```
