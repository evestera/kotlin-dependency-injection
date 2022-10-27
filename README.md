# kotlin-dependency-injection

Basic resolver of dependencies for constructor-based dependency injection of Kotlin classes.

- Self-contained. Classes being constructed do not need to know about this library.
- Low maintenance. Adding or removing pre-existing classes as dependencies requires no changes to DI setup code.
- Intentionally quite restricted to promote predictability and ease of understanding:
  - Only Kotlin classes with a primary constructor can be automatically constructed from a class reference.
    Other types are added as values or unambiguous function references (e.g. to a constructor or factory function).
  - No optional parameters allowed (though they can be ignored with `@DoNotResolve`).
  - Dependencies are resolvable *either* by type or by name, not both.
  - No ambiguous resolution. If two values are valid an exception is thrown instead.

## Using

In `build.gradle.kts`:

```kts
dependencies {
    implementation("dev.vstrs:kotlin-dependency-injection:0.1.0")
}
```

### Basic usage

```kt
// Given a set of classes depending on each other...
class CheeseRepository
class CheeseService(private val cheeseRepository: CheeseRepository)
class App(private val cheeseService: CheeseService)

// ...you can construct a context...
val context = DependencyResolutionContext.Builder().add(
    CheeseRepository::class,
    CheeseService::class,
    App::class
).build()

// ...and get the instances you need (probably the constructed app).
// You can throw away the context afterwards if you don't need it.
val app = context.get<App>()
 ```

In very simple cases like the above you can also just do

```kt
val app = resolveDependenciesAndGet<App>(
    CheeseRepository::class,
    CheeseService::class,
    App::class
)
```

### Injecting by name

See [@ResolveByName](https://kotlin-dependency-injection.vstrs.dev/kotlin-dependency-injection/dev.vstrs.di/-resolve-by-name/)

### Injecting all implementations of an interface

See [@ResolveAll](https://kotlin-dependency-injection.vstrs.dev/kotlin-dependency-injection/dev.vstrs.di/-resolve-all/)

### Function references and values

You can use other things than just classes for dependency resolution.
Direct values from calls like `Clock.systemUTC()`
and function references like `::someFunction` can be used.

The function references have to be unambiguous references to public functions.
It is not currently possible to use inline lambdas as factory functions.

Example:

```kt
fun dbConfig(appConfig: AppConfig) = appConfig.db

fun createDbClient(dbConfig: DbConfig): DbClient {
    // do something with dbConfig
}

val app = resolveDependenciesAndGet<App>(
    Clock.systemUTC(),
    AppConfig::class,
    ::dbConfig,
    ::connectionPool,
    // ... more stuff using Clock and DbClient ...
    App::class
)
```

[//]: # (TODO: Docs - Dividing dependencies into layers and mocking a layer)

[//]: # (TODO: Docs - Component scanning with e.g. classgraph)

[//]: # (TODO: Docs - Comparison to alternatives)
