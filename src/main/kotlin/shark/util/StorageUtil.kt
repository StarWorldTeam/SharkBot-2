package shark.util

import com.fasterxml.jackson.core.type.TypeReference
import shark.SharkBotEnvironment
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

interface IStorage<T> {

    fun get(): T
    fun set(value: T)
    fun reset(): Unit = Unit.apply { getDefault()?.let(::set) }
    fun getDefault(): T?
    fun flush() {}

}

class MemoryStorage<T>(private val default: () -> T? = { null }) : IStorage<T> {

    private var value: T? = null

    override fun get(): T {
        if (value == null) reset()
        return value!!
    }

    override fun set(value: T) {
        this.value = value
    }

    override fun getDefault() = default()

}

class FileStorage<T>(private val typeRef: TypeReference<T>, private val default: () -> T? = { null }, private val file: File) : IStorage<T> {

    constructor(typeRef: TypeReference<T>, default: () -> T? = { null }, path: Path) : this(typeRef, default, path.toFile())
    constructor(typeRef: TypeReference<T>, default: () -> T? = { null }, path: String) : this(typeRef, default, SharkBotEnvironment.getSharkDirectory("data", path))

    override fun getDefault() = default()

    override fun get(): T {
        if (!file.exists()) reset()
        return ResourceFileType.getType(file.name).readAs(file.readBytes(), typeRef)
    }

    override fun reset() {
        flush()
        super.reset()
    }

    override fun set(value: T) = Unit.also {
        ResourceFileType.getType(file.name).writeObject(file.outputStream(), value)
    }

    fun getFile() = file

    override fun flush() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            file.createNewFile()
        }
        if (file.readBytes().isEmpty()) super.reset()
    }

}

class BytesStreamStorage(private val default: () -> ByteArray? = { null }, private val input: () -> InputStream, private val output: () -> OutputStream) : IStorage<ByteArray> {

    override fun getDefault() = default()

    override fun get(): ByteArray = input().readAllBytes()

    override fun reset() = output().write(getDefault())

    override fun set(value: ByteArray) = output().write(value)

    fun getInputStream() = input
    fun getOutputStream() = output

}
