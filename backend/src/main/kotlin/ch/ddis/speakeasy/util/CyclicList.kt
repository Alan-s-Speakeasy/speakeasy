package ch.ddis.speakeasy.util

class CyclicList<out T>(elements: List<T>) {

    private val list = elements.toList()

    val size = list.size
    private var idx = -1

    fun next(): T {
        idx = (idx + 1) % size
        return list[idx]
    }

}