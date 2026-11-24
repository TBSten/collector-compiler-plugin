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

- private な宣言も集められる（公開用の橋渡し関数が生成される）。
- class のメンバー、inner class、ローカル宣言、引数、型エイリアスには付けられない（エラー）。
- 変換できない組み合わせは、その Collector では集めない。見えているどの Collector にも合わなければ警告。
- 細かい入力の扱い:
  - `@get:X` / `@field:X` は対象外で、`@X` と `@property:X` だけを集める。
  - expect に付けた場合は actual の 1 件として数える。
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

- 順序は宣言の FQN 順で固定。
- 受け手は `@CollectorFor` の object を直接指す必要がある（`with(XCollector) { collectAsList() }`、`run`、`apply` も可）。変数経由、`let` の `it`、関数参照（`XCollector::collectAsList`）はエラー。
- 結果は **呼び出しを含むモジュールのコンパイル時** に決まる。

### タグでまとめて取り出す

```kotlin
@CollectorFor<PluginA>(tag = "plugins") object PluginACollector : Collector<Plugin>
@CollectorFor<PluginB>(tag = "plugins") object PluginBCollector : Collector<Plugin>

val plugins = collectAsList<Plugin>("plugins")   // 同じタグの Collector を全部まとめる（重複は 1 件に）
```

- `@CollectorFor` の `tag` には文字列リテラルだけを書ける。呼び出し側のタグは const val でもよい。
- 同じタグの全 Collector の T が、要求した型のサブタイプである必要がある。
- Collector が 1 つもないタグは空になる（`collectSingle` はエラー、`collectSingleOrNull` は null）。

### アノテーションの値と一緒に取り出す

```kotlin
annotation class Screen(val path: String)
@CollectorFor<Screen>() object ScreenCollector : Collector<Page>

val pairs: List<Pair<Screen, Page>> = ScreenCollector.collectWithAnnotation()
val byPath: Map<Screen, Page> = ScreenCollector.collectAsMap()   // 同じキーが 2 つあればコンパイルエラー
```

## 制約

- **アイテムを含むモジュールから Collector が見えている必要がある。** Collector が見えないモジュールのアイテムは集められない。自前のアノテーションなら、Collector を同じファイルに置けば必ず見える。
- **取り出すモジュールから、アイテムを含むモジュールが見えている必要がある。** `implementation` の推移依存の先にあるアイテムは見えないので、`api` で公開する。
- ほかのバージョンの Collector でビルドされたモジュールが混ざると、エラー（`HINT_FORMAT_MISMATCH`）で再ビルドを促す。

## コンパイルエラーと警告

| 診断 | 内容 |
| --- | --- |
| `RECEIVER_NOT_COLLECTOR_OBJECT` | 受け手が `@CollectorFor` の object を直接指していない |
| `COLLECT_SINGLE_COUNT` / `COLLECT_SINGLE_OR_NULL_COUNT` | `collectSingle*` の件数が合わない |
| `COLLECT_TAG_NOT_CONSTANT` / `COLLECT_TAG_TYPE_MISMATCH` | タグが定数でない / タグの Collector の T が合わない |
| `COLLECT_ANNOTATION_TYPE_MISMATCH` / `COLLECT_AS_MAP_DUPLICATE_KEY` | アノテーションの型が合わない / Map のキーが重複 |
| `COLLECT_CALLABLE_REFERENCE` | `XCollector::collectAsList` のような参照 |
| `COLLECTOR_FOR_NOT_OBJECT` / `COLLECTOR_FOR_NOT_COLLECTOR` / `COLLECTOR_FOR_TAG_NOT_LITERAL` | `@CollectorFor` の付け方の誤り |
| `ITEM_NOT_ALLOWED_HERE` / `ITEM_NAME_CLASH` | 付けられない場所 / 同じ FQN の宣言が 2 つ（オーバーロード、別ファイルの同名宣言） |
| `HINT_FORMAT_MISMATCH` | 別バージョンの Collector でビルドされたモジュール |
| `ITEM_NOT_CONVERTIBLE`（警告） | どの Collector の T にも変換できない |
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
