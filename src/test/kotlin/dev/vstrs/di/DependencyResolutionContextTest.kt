package dev.vstrs.di

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset

@Suppress("unused", "MemberVisibilityCanBePrivate")
class DependencyResolutionContextTest {

    @Test
    fun `should resolve single constructed dependency`() {
        class Foo
        class DependsOnFoo(val foo: Foo)

        DependencyResolutionContext.Builder().add(
            Foo::class,
            DependsOnFoo::class
        ).build().get<DependsOnFoo>()
    }

    @Test
    fun `should resolve single object dependency`() {
        class Foo
        class DependsOnFoo(val foo: Foo)

        DependencyResolutionContext.Builder().add(
            Foo(),
            DependsOnFoo::class
        ).build().get<DependsOnFoo>()
    }

    @Test
    fun `should resolve implicitly named parameters`() {
        class Foo
        class DependsOnFoo(@ResolveByName val namedFoo: Foo)

        DependencyResolutionContext.Builder().add(
            "namedFoo" to Foo::class,
            DependsOnFoo::class
        ).build().get<DependsOnFoo>()
    }

    @Test
    fun `should resolve explicitly named parameters`() {
        class Foo
        class DependsOnFoo(@ResolveByName("namedFoo") val foo: Foo)

        DependencyResolutionContext.Builder().add(
            "namedFoo" to Foo::class,
            DependsOnFoo::class
        ).build().get<DependsOnFoo>()
    }

    @Test
    fun `should not resolve by type if declared to be resolved by name`() {
        class Foo
        class DependsOnFoo(@ResolveByName val foo: Foo)

        assertThatThrownBy {
            DependencyResolutionContext.Builder().add(
                Foo::class,
                DependsOnFoo::class
            ).build()
        }
            .hasMessageContaining("No bean found")
    }

    @Test
    fun `should direct devs to where resolution is set up for missing beans`() {
        class Foo
        class DependsOnFoo(@ResolveByName val foo: Foo)

        assertThatThrownBy {
            DependencyResolutionContext.Builder().add(
                Foo::class,
                DependsOnFoo::class
            ).build()
        }
            .hasMessageContaining(this::class.simpleName)
            .hasMessageContaining("should direct devs".replace(" ", "_"))
    }

    @Test
    fun `should throw on missing dependency`() {
        class Foo
        class DependsOnFoo(val foo: Foo)

        assertThatThrownBy {
            DependencyResolutionContext.Builder().add(
                DependsOnFoo::class
            ).build()
        }
            .hasMessageContaining("No bean found")
            .hasMessageContaining("foo in DependsOnFoo")
    }

    @Test
    fun `should throw on ambiguity`() {
        class Foo
        class DependsOnFoo(val foo: Foo)

        assertThatThrownBy {
            DependencyResolutionContext.Builder().add(
                Foo(),
                Foo::class,
                DependsOnFoo::class
            ).build()
        }.hasMessageContaining("ambiguously defined")
    }

    @Test
    fun `should throw on unannotated optional parameter`() {
        class HasOptionalParameter(val someOption: String = "the option value")

        assertThatThrownBy {
            DependencyResolutionContext.Builder().add(
                HasOptionalParameter::class
            ).build()
        }
            .hasMessageContaining("Optional parameters")
            .hasMessageContaining("someOption")
    }

    companion object {
        private const val CONST_STRING = "some random const value"
    }

    @Test
    fun `should allow but not resolve annotated optional parameter`() {
        // Optional value has to be const for inline classes (a literal is const),
        // otherwise Kotlin ends up creating a helper function which does not have the annotation...
        class HasOptionalParameter(@DoNotResolve val someOption: String = CONST_STRING)

        val constructed = DependencyResolutionContext.Builder().add(
            "some string that should not be used",
            HasOptionalParameter::class
        ).build().get<HasOptionalParameter>()
        assertThat(constructed.someOption).isEqualTo(CONST_STRING)
    }

    interface SomeInterface // interfaces can't be inline, so re-using this one

    @Test
    fun `should resolve implementations of interfaces`() {
        class Foo : SomeInterface
        class DependsOnFoo(val foo: SomeInterface)

        DependencyResolutionContext.Builder().add(
            Foo::class,
            DependsOnFoo::class
        ).build().get<DependsOnFoo>()
    }

    class FactoryCreatedFoo
    fun fooFactory(): FactoryCreatedFoo = FactoryCreatedFoo()

    @Test
    fun `should resolve output of factories`() {
        class DependsOnFactoryCreatedFoo(val foo: FactoryCreatedFoo)

        DependencyResolutionContext.Builder().add(
            ::fooFactory,
            DependsOnFactoryCreatedFoo::class
        ).build().get<DependsOnFactoryCreatedFoo>()
    }

    private fun privateFooFactory(): FactoryCreatedFoo = FactoryCreatedFoo()

    @Test
    @Disabled("Currently DependencyResolutionContext needs to access the factory")
    fun `should resolve output of private factories`() {
        class DependsOnFactoryCreatedFoo(val foo: FactoryCreatedFoo)

        DependencyResolutionContext.Builder().add(
            ::privateFooFactory,
            DependsOnFactoryCreatedFoo::class
        ).build().get<DependsOnFactoryCreatedFoo>()
    }

    @Test
    @Disabled("Anonymous function references don't quite seem to work with kotlin-reflect yet")
    fun `should resolve output of inline factories`() {
        class DependsOnFactoryCreatedFoo(val foo: FactoryCreatedFoo)
        fun inlineFooFactory(): FactoryCreatedFoo = FactoryCreatedFoo()

        DependencyResolutionContext.Builder().add(
            ::inlineFooFactory,
            DependsOnFactoryCreatedFoo::class
        ).build().get<DependsOnFactoryCreatedFoo>()
    }

    @Test
    @Disabled("Not really possible yet, as reflection on lambdas is not fully supported by kotlin-reflect")
    fun `should resolve output of lambda factories`() {
        class DependsOnFactoryCreatedFoo(val foo: FactoryCreatedFoo)

        DependencyResolutionContext.Builder().add(
            { FactoryCreatedFoo() },
            DependsOnFactoryCreatedFoo::class
        ).build().get<DependsOnFactoryCreatedFoo>()
    }

    class StringHolder(val value: String)
    class NumberHolder(val value: Int)
    fun lengthCalculator(s: StringHolder) = NumberHolder(s.value.length)

    @Test
    fun `should resolve parameters of factories`() {
        DependencyResolutionContext.Builder().add(
            "hello",
            StringHolder::class,
            ::lengthCalculator
        ).build().get<NumberHolder>()
    }

    fun enterpriseAdder(
        @ResolveByName a: Int,
        @ResolveByName b: Int
    ) = a + b

    @Test
    fun `should resolve named parameters of factories`() {
        val result = DependencyResolutionContext.Builder().add(
            "a" to 5,
            "b" to 10,
            "output" to ::enterpriseAdder
        ).build().get<Int>("output")
        assertThat(result).isEqualTo(15)
    }

    @Test
    fun `should resolve list of dependencies`() {
        class FooOne : SomeInterface
        class FooTwo : SomeInterface
        class DependsOnFoo(@ResolveAll val foos: List<SomeInterface>)

        val bar: DependsOnFoo = DependencyResolutionContext.Builder().add(
            ::FooOne,
            ::FooTwo,
            ::DependsOnFoo
        ).build().get()

        assertThat(bar.foos).hasSize(2)
        assertThat(bar.foos).anyMatch { it is FooOne }
        assertThat(bar.foos).anyMatch { it is FooTwo }
    }

    @Test
    fun `should not depend on specific ordering`() {
        class Foo
        class DependsOnFoo(val foo: Foo)

        DependencyResolutionContext.Builder().add(
            DependsOnFoo::class,
            Foo::class
        ).build()
    }

    // We can't use an inline class before it is defined, so these have to outside the test
    class CycleA(val b: CycleB)
    class CycleB(val a: CycleA)

    @Test
    fun `should tell you if there is a cycle`() {
        assertThatThrownBy {
            DependencyResolutionContext.Builder().add(
                CycleA::class,
                CycleB::class
            ).build()
        }.hasMessageContaining("CycleA::class -> CycleB::class -> CycleA::class")
    }

    @Test
    fun `should support layer-wise context building`() {
        class FooRepository
        class FooService(private val fooRepository: FooRepository)
        class App(private val fooService: FooService)

        val repositoryLayer = listOf(
            FooRepository::class
        )
        val serviceLayer = listOf(
            FooService::class
        )

        val contextBuilder = DependencyResolutionContext.Builder()
        contextBuilder.addAll(repositoryLayer)
        contextBuilder.addAll(serviceLayer)
        contextBuilder.add(App::class)
        val context = contextBuilder.build()
        context.get<App>()
    }

    @Test
    fun `should support references to java methods`() {
        class DependsOnClock(val clock: Clock)

        DependencyResolutionContext.Builder().add(
            ZoneOffset.UTC,
            Clock::system,
            DependsOnClock::class
        ).build()
    }
}
