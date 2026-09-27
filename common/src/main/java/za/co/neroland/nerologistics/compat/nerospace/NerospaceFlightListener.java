package za.co.neroland.nerologistics.compat.nerospace;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import za.co.neroland.nerospace.api.route.FlightHandle;
import za.co.neroland.nerospace.api.route.PadHandle;
import za.co.neroland.nerospace.api.route.RouteEvents;

/**
 * The single {@link RouteEvents.Listener} NeroLogistics subscribes. Nerospace calls it inside its own
 * tick, where it must be quick and must not throw, and where no {@code MinecraftServer} is handed over —
 * so it only enqueues a compact record (ids and a kind, never a UUID) and
 * {@link NerospaceRouteProvider#tick} drains the queue on NeroLogistics' server tick, where the flight
 * store and metrics are reachable. Flights NeroLogistics did not request are filtered out at drain time.
 * The queue is bounded ({@value #MAX_QUEUED}); if something stalls the drain, the oldest events go and
 * the provider's periodic reconciliation against {@code RouteApi.flight} fixes the counts.
 */
final class NerospaceFlightListener implements RouteEvents.Listener {

    static final int MAX_QUEUED = 1_024;

    /** What happened. */
    enum Kind {
        DEPARTED,
        ARRIVED,
        HELD,
        DROPPED,
        PAD_UNREGISTERED
    }

    /**
     * One queued event.
     *
     * @param kind  what happened
     * @param id    flight id, or pad id for {@link Kind#PAD_UNREGISTERED}
     * @param padId destination pad id for flight events ({@code 0} otherwise)
     */
    record Event(Kind kind, int id, int padId) {
    }

    private final Queue<Event> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    @Override
    public void onFlightDeparted(FlightHandle flight) {
        offer(new Event(Kind.DEPARTED, flight.id(), flight.destinationPadId()));
    }

    @Override
    public void onFlightArrived(FlightHandle flight) {
        offer(new Event(Kind.ARRIVED, flight.id(), flight.destinationPadId()));
    }

    @Override
    public void onFlightHeld(FlightHandle flight) {
        offer(new Event(Kind.HELD, flight.id(), flight.destinationPadId()));
    }

    @Override
    public void onFlightDropped(FlightHandle flight) {
        offer(new Event(Kind.DROPPED, flight.id(), flight.destinationPadId()));
    }

    @Override
    public void onPadUnregistered(PadHandle pad) {
        offer(new Event(Kind.PAD_UNREGISTERED, pad.id(), 0));
    }

    private void offer(Event event) {
        this.queue.add(event);
        if (this.size.incrementAndGet() > MAX_QUEUED && this.queue.poll() != null) {
            this.size.decrementAndGet();
        }
    }

    /** Next event, or null when drained. */
    Event poll() {
        Event event = this.queue.poll();
        if (event != null) {
            this.size.decrementAndGet();
        }
        return event;
    }

    boolean isEmpty() {
        return this.queue.isEmpty();
    }

    void clear() {
        this.queue.clear();
        this.size.set(0);
    }
}
