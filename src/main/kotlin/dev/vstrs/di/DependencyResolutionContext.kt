package dev.vstrs.di

import org.slf4j.LoggerFactory
import java.util.IdentityHashMap
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.jvmName

private val logger = LoggerFactory.getLogger(DependencyResolutionContext::class.java)

/**
 * Basic resolver of dependencies for constructor-based dependency injection.
 *
 * Intentionally quite restricted to promote predictability and ease of understanding:
 * - Only Kotlin classes with a primary constructor can be automatically constructed from a class reference.
 *   Other types are added as values or unambiguous function references (e.g. to a constructor or factory function).
 * - No optional parameters allowed (though they can be ignored with @[DoNotResolve]).
 * - Dependencies are resolvable *either* by type or by name, not both.
 * - No ambiguous resolution. If two values are valid an exception is thrown instead.
 *
 * Simple example:
 *
 * ```kt
 * // Given a set of classes depending on each other...
 * class CheeseRepository
 * class CheeseService(private val cheeseRepository: CheeseRepository)
 * class App(private val cheeseService: CheeseService)
 *
 * // ...you can construct a context...
 * val context = DependencyResolutionContext.Builder().add(
 *     CheeseRepository::class,
 *     CheeseService::class,
 *     App::class
 * ).build()
 *
 * // ...and get the instances you need (probably the constructed app).
 * // You can throw away the context afterwards if you don't need it.
 * val app = context.get<App>()
 * ```
 */
class DependencyResolutionContext private constructor(
    private val beans: MutableMap<String, Any>,
    private val named: MutableMap<String, Any?>
) {
    private data class Constructable(
        val callable: KCallable<*>,
        val valueType: KClass<*>,
        val debugName: String,
        val resolutionName: String? = null
    ) {
        var constructedValue: Any? = null

        constructor(source: KClass<*>) : this(
            callable = source.primaryConstructor
                ?: throw DependencyResolutionException(
                    "${source.simpleName}: Class must have a primary constructor " +
                        "to be constructed by ${DependencyResolutionContext::class.simpleName}."
                ),
            valueType = source,
            debugName = "${source.simpleName}::class"
        )

        constructor(source: KFunction<*>) : this(
            callable = source,
            valueType = source.returnType.jvmErasure,
            debugName = "::${source.name}"
        )

        override fun toString(): String = debugName
    }

    /** Placeholder to indicate that a type is satisfied by several values, so it is ambiguous */
    private class AmbiguousBeanPlaceholder(val instances: MutableList<Any>)

    /** Placeholder to indicate that a type should be wired by name, to block resolution by type */
    private class ResolveByNamePlaceholder(val name: String) {
        override fun toString(): String = "ResolveByNamePlaceholder($name)"
    }

    /** Builder class for [DependencyResolutionContext] */
    class Builder {
        private val constructables: MutableList<Constructable> = mutableListOf()
        private val named: MutableMap<String, Any?> = mutableMapOf()
        private val objects: MutableList<Any> = mutableListOf()
        private val beans: MutableMap<String, Any> = mutableMapOf()

        /** Add classes to be constructed and the dependencies needed to construct them */
        fun add(vararg sources: Any): Builder {
            sources.forEach {
                when (it) {
                    is KClass<*> -> constructables.add(Constructable(it))
                    is KFunction<*> -> constructables.add(Constructable(it))
                    is Pair<*, *> -> {
                        val (name, value) = it
                        if (name is String) {
                            if (named[name] != null) {
                                throw DependencyResolutionException("A bean with the name $name already exists")
                            }
                            when (value) {
                                is KClass<*> -> {
                                    val constructable = Constructable(value).copy(resolutionName = name)
                                    constructables.add(constructable)
                                    named[name] = constructable
                                }
                                is KFunction<*> -> {
                                    val constructable = Constructable(value).copy(resolutionName = name)
                                    constructables.add(constructable)
                                    named[name] = constructable
                                }
                                else -> {
                                    named[name] = it.second
                                }
                            }
                        } else {
                            objects.add(it)
                        }
                    }
                    else -> objects.add(it)
                }
            }

            return this
        }

        fun addAll(sources: Collection<Any>): Builder {
            sources.forEach { add(it) }
            return this
        }

        /** Actually resolve dependencies and construct objects, returning a [DependencyResolutionContext] */
        fun build(): DependencyResolutionContext {
            val unusedValues = IdentityHashMap<Any, Any>()
            named.forEach { addBean(ResolveByNamePlaceholder(it.key)) }
            objects.forEach { addBean(it) }
            constructables.forEach {
                if (it.resolutionName == null) {
                    addBean(it)
                }
            }

            for (constructable in constructables) {
                if (constructable.constructedValue == null) {
                    construct(constructable, unusedValues)
                }
            }

            if (unusedValues.size > 1) {
                logger.warn(
                    "DependencyResolutionContext contains more unused values than expected:" +
                        "\n  ${unusedValues.values.joinToString("\n  ")}"
                )
            }

            return DependencyResolutionContext(beans, named)
        }

        private fun construct(
            constructable: Constructable,
            unusedValues: IdentityHashMap<Any, Any>,
            dependencyChain: List<Constructable> = mutableListOf()
        ) {
            if (dependencyChain.contains(constructable)) {
                throw DependencyResolutionException(
                    "Detected dependency loop: " +
                        (dependencyChain + constructable).joinToString(" -> ") { it.debugName }
                )
            }

            val callable = constructable.callable
            val initArgs = mutableMapOf<KParameter, Any?>()
            for (parameter in callable.parameters) {
                if (parameter.isOptional) {
                    if (parameter.hasAnnotation<DoNotResolve>()) {
                        continue
                    }
                    // Don't try to resolve optional parameters.
                    // Reasoning: If it's OK for something to be both resolved and not resolved it is very easy to
                    // change program behaviour without noticing, and we don't want unpredictable magic.
                    throw DependencyResolutionException(
                        "${constructable.debugName}.${parameter.name}: " +
                            "Optional parameters are not allowed in the primary constructor " +
                            "of classes constructed with ${DependencyResolutionContext::class.simpleName}. " +
                            "Annotate the parameter with @${DoNotResolve::class.simpleName} " +
                            "if you want the resolution to always use the default value."
                    )
                }
                val resolveAll =
                    parameter.type.classifier == List::class && parameter.hasAnnotation<ResolveAll>()

                var name = parameter.findAnnotation<ResolveByName>()?.name
                if (name == ResolveByName.USE_PARAMETER_NAME) {
                    name = parameter.name
                }
                var bean = if (name != null) {
                    named[name]
                        ?: throw DependencyResolutionException(
                            "No bean found for name $name " +
                                "(needed for parameter ${parameter.name} in ${constructable.debugName}). " +
                                callSiteMessage()
                        )
                } else {
                    val lookupType = if (resolveAll) parameter.type.arguments[0].type!! else parameter.type
                    try {
                        getByTypeAsAny(lookupType.jvmErasure.java, resolveAll) // cast happens on constructor call
                    } catch (e: Exception) {
                        if (e is DependencyResolutionException) throw e
                        throw DependencyResolutionException(
                            "Unable to construct ${constructable.debugName}. Parameter: ${parameter.name}",
                            e
                        )
                    } ?: throw DependencyResolutionException(
                        "No bean found for type ${lookupType.jvmErasure.java} " +
                            "(needed for parameter ${parameter.name} in ${constructable.debugName}) " +
                            callSiteMessage()
                    )
                }

                if (resolveAll) {
                    bean = (bean as List<*>).map {
                        if (it !is Constructable) return@map it
                        if (it.constructedValue == null) {
                            construct(it, unusedValues, dependencyChain + constructable)
                        }
                        it.constructedValue
                    }
                }

                if (bean is Constructable) {
                    if (bean.constructedValue == null) {
                        construct(bean, unusedValues, dependencyChain + constructable)
                    }
                    bean = bean.constructedValue!!
                }

                initArgs[parameter] = bean
                if (resolveAll) {
                    (bean as List<*>).forEach { unusedValues.remove(it) }
                } else {
                    unusedValues.remove(bean)
                }
            }
            val instance = callable.callBy(initArgs)
            constructable.constructedValue = instance
            unusedValues[instance] = constructable
        }

        private fun callSiteMessage(): String {
            val callSite = Thread.currentThread().stackTrace.asIterable()
                .drop(1) // ::getStackTrace
                .first { it.className != this::class.jvmName }
            return "This is something you probably need to fix in the code " +
                "where ${DependencyResolutionContext::class.simpleName} is built, i.e. in this case in " +
                "${callSite.className}::${callSite.methodName}."
        }

        private fun addBean(type: String, bean: Any) {
            when (val existing = beans[type]) {
                null -> beans[type] = bean
                is AmbiguousBeanPlaceholder -> existing.instances.add(bean)
                else -> beans[type] = AmbiguousBeanPlaceholder(mutableListOf(existing, bean))
            }
        }

        private inline fun forAllRelevantClasses(
            rootClass: Class<*>,
            crossinline action: (Class<*>) -> Unit
        ) {
            action(rootClass)
            // Note: This does not iterate the entire type tree. Class.interfaces only includes direct interfaces.
            rootClass.interfaces.forEach { action(it) }
            var superclass = rootClass.superclass
            while (superclass != null && superclass.name != "java.lang.Object") {
                action(superclass)
                superclass = superclass.superclass
            }
        }

        private fun addBean(bean: Any) {
            val jClass = when (bean) {
                is Constructable -> bean.valueType.java
                else -> bean::class.java
            }
            forAllRelevantClasses(jClass) { addBean(it.name, bean) }
        }

        private fun <T> getByTypeAsAny(jClass: Class<T>, resolveAll: Boolean): Any? {
            val bean = beans[jClass.name]
            if (bean is AmbiguousBeanPlaceholder) {
                if (resolveAll) return bean.instances
                throw DependencyResolutionException(
                    "Bean for ${jClass.name} is ambiguously defined: ${bean.instances}"
                )
            }
            if (resolveAll) return listOf(bean)
            return bean
        }
    }

    /** Get a named value of type [T] from the resolution context */
    fun <T> get(name: String, jClass: Class<T>): T {
        var bean = named[name]
        if (bean is Constructable) {
            bean = bean.constructedValue
        }
        return jClass.cast(bean)
    }

    /** Get a named value of type [T] from the resolution context */
    inline fun <reified T> get(name: String): T {
        return get(name, T::class.java)
    }

    /**
     * Get a value of type [T] from the resolution context.
     *
     * Will not resolve a named value of type [T] unless a name argument is passed.
     */
    fun <T> get(jClass: Class<T>): T {
        var bean = beans[jClass.name]
        if (bean is AmbiguousBeanPlaceholder) {
            throw DependencyResolutionException(
                "Bean for ${jClass.name} is ambiguously defined, with ${bean.instances.size} instances"
            )
        }
        if (bean is Constructable) {
            bean = bean.constructedValue
        }
        return jClass.cast(bean)
    }

    /**
     * Get a value of type [T] from the resolution context.
     *
     * Will not resolve a named value of type [T] unless a name argument is passed.
     */
    inline fun <reified T> get(): T {
        return get(T::class.java)
    }
}
