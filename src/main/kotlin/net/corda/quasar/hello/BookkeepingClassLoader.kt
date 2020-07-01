package net.corda.quasar.hello

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import kotlin.collections.HashSet


class BookkeepingClassLoader(parent: ClassLoader? = getSystemClassLoader()) : ClassLoader(parent) {
    private val _classes = HashSet<Class<*>>()
    val classes :Set<Class<*>>
    get() = Collections.unmodifiableSet(_classes)

    override fun loadClass(name: String, resolve : Boolean): Class<*> {
        return when {
            name.startsWith("java.lang") -> {
                parent.loadClass(name)
            }
            else -> {
                loadClassFromFile(name).also {
                    _classes.add(it)
                }
            }
        }
    }

    private fun loadClassFromFile(className: String): Class<*> {
        val classPath = className.replace('.', File.separatorChar) + ".class"
        return parent.getResourceAsStream(classPath)?.use  { inputStream ->
                val byteStream = ByteArrayOutputStream()
                val buffer = ByteArray(0x1000)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    byteStream.write(buffer, 0, read)
                }
            byteStream.toByteArray().let {
                defineClass(className, it, 0, it.size)
            }
        } ?: throw ClassNotFoundException(className)
    }

    fun reset() {
        _classes.clear()
    }
}