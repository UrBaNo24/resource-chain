import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class MemoryStorage<T> implements Storage<T> {

    private record CacheEntry<T>(T value, Instant expiresAt) {}

    private final String name;
    private final Duration expiration;

    private final AtomicReference<CacheEntry<T>> cache = new AtomicReference<>();

    public MemoryStorage() {
        this("Memory", Duration.ofHours(1));
    }

    public MemoryStorage(String name, Duration expiration) {
        this.name = name;
        this.expiration = expiration;

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public Optional<Duration> getExpiration() {
        return Optional.of(expiration);
    }

    @Override
    public void write(T value) {

        Instant expiresAt = Instant.now().plus(expiration);

        cache.set(new CacheEntry<>(value, expiresAt));
    }

    @Override
    public Optional<T> read() {
        CacheEntry<T> entry = cache.get();

        if(entry == null) {
            return Optional.empty();
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.set(null);
            return Optional.empty();
        }

        return Optional.of(entry.value());
    }


}
