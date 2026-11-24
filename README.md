# Collector

アノテーションを付けた宣言をコンパイル時に集める Kotlin コンパイラプラグイン。JVM 限定の `ServiceLoader` の代わりに、KMP の全ターゲットで使えることを目指す。

> 0.1 開発中。仕様と設計はプロジェクトの「Collector ユーザ仕様」「Collector コンパイラ設計書」を参照。

```kotlin
annotation class ApiRoutes

@CollectorFor<ApiRoutes>()
object ApiRoutesCollector : Collector<Route.() -> Unit>

@ApiRoutes fun Route.userRoutes() { /* ... */ }

// 呼び出しを含むモジュールのコンパイル時に listOf(...) へ置き換わる
val routes = ApiRoutesCollector.collectAsList()
```

## モジュール

| パス | 中身 |
| --- | --- |
| `collector-runtime` | `@CollectorFor` / `Collector` / `collect*` 関数（KMP） |
| `collector-compiler` | コンパイラプラグイン本体（FIR で hint を宣言し、IR で body と呼び出しを埋める） |
| `collector-gradle-plugin` | `me.tbsten.collector` Gradle プラグイン |
| `integration-tests` | 複数モジュールの KMP 構成で実際にビルドして確かめる別ビルド |

## 開発

```sh
./gradlew :collector-compiler:test
cd integration-tests
./gradlew allTests            # jvm / js / wasmJs / linuxX64
./scripts/ic-check.sh         # インクリメンタルビルドの確認
```

対応する Kotlin は 2.4.21。
