package dev.lillya;

import java.time.ZonedDateTime;

public record LruNode<V>(long lastAccessTime, long idx, V value) {

    public LruNode(final V value, final long idx) {
        this(ZonedDateTime.now().toInstant().toEpochMilli(), idx, value);
    }

    public V getValue() {
        return this.value;
    }

    public int compareAccessTime(final LruNode<V> that) {
        final int accessTimeCompare = Long.compare(this.lastAccessTime, that.lastAccessTime);

        // If access time is the same (in high-contention condition), compare node index
        if (accessTimeCompare == 0) {
            return Long.compare(this.idx, that.idx);
        }

        return accessTimeCompare;
    }
}
