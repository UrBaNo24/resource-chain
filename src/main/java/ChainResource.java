import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;



public class ChainResource<T> {

    private final List<Storage<T>> storages;

    private final AtomicReference<CompletableFuture<T>> inFlight = new AtomicReference<>();

    public ChainResource(List<Storage<T>> storages) {
        this.storages = storages;
    }

    private T resolveFromChain(int startIndex) throws Exception {
        for (int i = startIndex; i < storages.size(); i++) {
            Storage<T> storage = storages.get(i);
            Optional<T> valueOpt;

            try {
                valueOpt = storage.read();
            } catch (Exception e) {
                continue;
            }

            if (valueOpt.isPresent()) {
                T value = valueOpt.get();
                propagateUpwards(i, value);
                return value;
            }
        }

        throw new IllegalStateException("No storage in the chain was able to provide a valid value.");
    }

    private void propagateUpwards(int fromIndex, T value) {
        for (int j = fromIndex - 1; j >= 0; j--) {
            Storage<T> higherStorage = storages.get(j);

            if (!higherStorage.isReadOnly()) {
                try {
                    higherStorage.write(value);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public CompletableFuture<T> getValue() {
        while (true) {
            CompletableFuture<T> active = inFlight.get();
            if (active != null && !active.isDone()) {
                return active;
            }

            Storage<T> outermost = storages.get(0);
            try {
                Optional<T> cached = outermost.read();
                if (cached.isPresent()) {
                    return CompletableFuture.completedFuture(cached.get());
                }
            } catch (Exception ignored) {
            }

            CompletableFuture<T> newFuture = new CompletableFuture<>();
            if (inFlight.compareAndSet(active, newFuture)) {
                CompletableFuture.runAsync(() -> {
                    try {
                        T value = resolveFromChain(1);
                        newFuture.complete(value);
                    } catch (Throwable t) {
                        newFuture.completeExceptionally(t);
                    } finally {
                        inFlight.compareAndSet(newFuture, null);
                    }
                });
                return newFuture;
            }
        }
    }
}
