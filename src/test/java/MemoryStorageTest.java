import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryStorageTest {

    @Test
    void shouldStoreAndRetrieveValue() {
        MemoryStorage<String> storage = new MemoryStorage<>("TestMemory", Duration.ofHours(1));

        storage.write("sample-data");
        Optional<String> result = storage.read();

        assertThat(result).contains("sample-data");
    }

    @Test
    void shouldReturnEmptyWhenNoValueStored() {
        MemoryStorage<String> storage = new MemoryStorage<>();

        Optional<String> result = storage.read();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldExpireAfterConfiguredDuration() throws InterruptedException {
        MemoryStorage<String> storage = new MemoryStorage<>("ExpiringMemory", Duration.ofMillis(50));

        storage.write("ephemeral");
        assertThat(storage.read()).contains("ephemeral");

        Thread.sleep(70);

        assertThat(storage.read()).isEmpty();
    }

    @Test
    void shouldHaveExpectedMetadata() {
        MemoryStorage<String> storage = new MemoryStorage<>();

        assertThat(storage.getName()).isEqualTo("Memory");
        assertThat(storage.isReadOnly()).isFalse();
        assertThat(storage.getExpiration()).contains(Duration.ofHours(1));
    }
}
