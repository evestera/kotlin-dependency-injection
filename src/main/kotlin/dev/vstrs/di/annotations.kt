package dev.vstrs.di

/**
 * Mark parameter as injected by name rather than type
 *
 * Unless another name is provided, the paramater name is used for resolution.
 *
 * To provide a named value of type `T` to the dependency resolution, use a `Tuple<String, T>`.
 * (Which you can make with the construct `"name" to value`).
 *
 * Example:
 *
 * ```kt
 * class FrobnicatorClient(
 *     @ResolveByName private val frobnicatorUrl: String
 * )
 *
 * val context = resolveDependencies(
 *     "frobnicatorUrl" to System.getenv("FROB_URL"),
 *     FrobnicatorClient::class
 * )
 *
 * context.get<String>("frobnicatorUrl")
 * ```
 */
annotation class ResolveByName(val name: String = USE_PARAMETER_NAME) {
    companion object {
        /** "Magic" value that signifies that the name used for resolution is the parameter name  */
        const val USE_PARAMETER_NAME = "USE_PARAMETER_NAME"
    }
}

/**
 * Mark an optional parameter to be ignored by dependency resolution
 *
 * Note, there is no annotation to do the inverse, i.e. actually resolve an optional dependency
 *
 * ```kt
 * class WeirdlyTestedService(
 *     @DoNotResolve private val testMonitorUrl: String? = null
 * )
 *
 * val context = resolveDependencies(
 *     // if the @DoNotResolve above is removed, this throws an exception
 *     WeirdlyTestedService::class
 * )
 * ```
 */
annotation class DoNotResolve

/**
 * Resolve all matching values, e.g. multiple implementations of the same interface
 *
 * The annotated parameter has to have type `List<T>` where `T` is the type you want to resolve.
 *
 * ```
 * interface SomeInterface
 * class ImplementationOne : SomeInterface
 * class ImplementationTwo : SomeInterface
 * class DependsOnImplementations(
 *     @ResolveAll val allImplementations: List<SomeInterface>
 * )
 *
 * val context = resolveDependencies(
 *     ImplementationOne::class,
 *     ImplementationTwo::class,
 *     DependsOnImplementations::class
 * )
 * ```
 */
annotation class ResolveAll
