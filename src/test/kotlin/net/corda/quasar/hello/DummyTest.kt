package net.corda.quasar.hello

import org.junit.jupiter.api.Test

object Bar2 {
    init {
        println("there")
    }
}

class DummyTest {

    companion object {
        init {
            println("Here")
        }
    }

    @Test
    fun test() {

    }
}