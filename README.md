# kotlin-dependency-injection

Basic resolver of dependencies for constructor-based dependency injection of Kotlin classes.

- Self-contained. Classes being constructed do not need to know about this library.
- Low maintenance. Adding or removing pre-existing classes as dependencies requires no changes to DI setup code.

## Using

In `build.gradle.kts`:

```kts
dependencies {
    implementation("dev.vstrs:kotlin-dependency-injection:0.1.0")
}
```

Then:

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

[//]: # (TODO: Docs - Annotations)

[//]: # (TODO: Docs - Factories)

[//]: # (TODO: Docs - Dividing dependencies into layers and mocking a layer)

[//]: # (TODO: Docs - Component scanning with e.g. classgraph)

[//]: # (TODO: Docs - Comparison to alternatives)
