package shark.core.event
abstract class Event {

    protected var bus: EventBus? = null
    protected var cancelled = false

    open fun isCancellable() = false
    open fun isCancelled() = isCancellable() && cancelled

    open fun setCancelled(cancelled: Boolean) = this.also {
        this.cancelled = cancelled
    }

    open fun getEventBus() = bus!!
    open fun setEventBus(bus: EventBus) = this.also {
        it.bus = bus
    }

}