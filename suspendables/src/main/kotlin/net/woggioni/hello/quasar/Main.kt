package net.woggioni.hello.quasar

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.fibers.instrument.JavaAgent
import java.lang.IllegalStateException


interface Foo {
    fun foo()
}

class FooImpl : Foo {
        @Suspendable
    override fun foo() {}
}

object Main {

    @JvmStatic
    fun main(vararg args : String) {
        if(!JavaAgent.isActive()) throw IllegalStateException("Quasar Java agent not running")
        (FooImpl() as Foo).foo()
    }
}