package dev.vstrs.di

/**
 * Convenience function for building a context in the simple case.
 *
 * Alias for `DependencyResolutionContext.Builder().add(sources).build()`
 *
 * Example:
 *
 * ```kt
 * val context = resolveDependencies(
 *     CheeseRepository::class,
 *     CheeseService::class,
 *     App::class
 * )
 *
 * val app = context.get<App>()
 * ```
 *
 * @see DependencyResolutionContext
 */
fun resolveDependencies(vararg sources: Any): DependencyResolutionContext =
    DependencyResolutionContext.Builder().add(*sources).build()

/**
 * Convenience function for building a context and extracting the "main"
 * constructed object in the simple case.
 *
 * Alias for `resolveDependencies(*sources).get<T>()`
 *
 * Example:
 *
 * ```kt
 * val app = resolveDependenciesAndGet<App>(
 *     CheeseRepository::class,
 *     CheeseService::class,
 *     App::class
 * )
 * ```
 *
 * @see DependencyResolutionContext
 */
inline fun <reified T> resolveDependenciesAndGet(vararg sources: Any): T =
    resolveDependencies(*sources).get()
