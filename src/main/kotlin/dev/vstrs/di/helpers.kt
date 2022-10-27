package dev.vstrs.di

/**
 * Convenience function for building a context in the simple case.
 *
 * Alias for `DependencyResolutionContext.Builder().add(sources).build()`
 *
 * @see DependencyResolutionContext
 */
fun resolveDependencies(vararg sources: Any): DependencyResolutionContext =
    DependencyResolutionContext.Builder().add(*sources).build()

/**
 * Convenience function for building a context in the simple case.
 *
 * Alias for `resolveDependencies(*sources).get<T>()`
 */
inline fun <reified T> resolveDependenciesAndGet(vararg sources: Any): T =
    resolveDependencies(*sources).get()
