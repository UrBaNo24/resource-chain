import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChainResourceTest {

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldReturnFromFirstStorageWhenAvailable() {
        TestStorage<String> storage1 = new TestStorage<>("mem", false);
        storage1.setValue("cached-value");

        TestStorage<String> storage2 = new TestStorage<>("disk", false);
        storage2.setValue("disk-value");

        ChainResource<String> chain = new ChainResource<>(List.of(storage1, storage2));

        String result = chain.getValue().join();

        assertThat(result).isEqualTo("cached-value");
        assertThat(storage2.getReadCount()).isZero();
    }

    @Test
    void shouldTraverseChainWhenOuterStorageIsEmpty() {
        TestStorage<String> storage1 = new TestStorage<>("mem", false);
        TestStorage<String> storage2 = new TestStorage<>("disk", false);
        storage2.setValue("from-disk");

        ChainResource<String> chain = new ChainResource<>(List.of(storage1, storage2));

        String result = chain.getValue().join();

        assertThat(result).isEqualTo("from-disk");
        assertThat(storage1.getReadCount()).isEqualTo(1);
        assertThat(storage2.getReadCount()).isEqualTo(1);
    }

    @Test
    void shouldPropagateUpwardsToWritableStorages() {
        TestStorage<String> memory = new TestStorage<>("mem", false);
        TestStorage<String> disk = new TestStorage<>("disk", false);
        TestStorage<String> remote = new TestStorage<>("web", true);
        remote.setValue("from-remote");

        ChainResource<String> chain = new ChainResource<>(List.of(memory, disk, remote));

        String result = chain.getValue().join();

        assertThat(result).isEqualTo("from-remote");
        assertThat(memory.getValue()).isEqualTo("from-remote");
        assertThat(disk.getValue()).isEqualTo("from-remote");
        assertThat(remote.getWriteCount()).isZero();
    }

    @Test
    void shouldSkipFailedStorageAndContinueChain() {
        TestStorage<String> failingStorage = new TestStorage<>("failing", false) {
            @Override
            public Optional<String> read() throws Exception {
                super.read();
                throw new IOException("Storage read failed");
            }
        };

        TestStorage<String> fallbackStorage = new TestStorage<>("fallback", false);
        fallbackStorage.setValue("fallback-value");

        ChainResource<String> chain = new ChainResource<>(List.of(failingStorage, fallbackStorage));

        String result = chain.getValue().join();

        assertThat(result).isEqualTo("fallback-value");
        assertThat(failingStorage.getReadCount()).isEqualTo(1);
        assertThat(fallbackStorage.getReadCount()).isEqualTo(1);
    }

    @Test
    void shouldFailWhenAllStoragesAreEmpty() {
        TestStorage<String> storage1 = new TestStorage<>("s1", false);
        TestStorage<String> storage2 = new TestStorage<>("s2", false);

        ChainResource<String> chain = new ChainResource<>(List.of(storage1, storage2));

        assertThatThrownBy(() -> chain.getValue().join())
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No storage in the chain was able to provide a valid value");
    }

    @Test
    void shouldDeduplicateConcurrentRequestsUnderHighContention() throws Exception {
        int threadCount = 50;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        TestStorage<String> slowRemote = new TestStorage<>("slow-remote", true) {
            @Override
            public Optional<String> read() throws Exception {
                super.read();
                Thread.sleep(100);
                return Optional.of("remote-data");
            }
        };

        TestStorage<String> memory = new TestStorage<>("mem", false);
        ChainResource<String> chain = new ChainResource<>(List.of(memory, slowRemote));

        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return chain.getValue().join();
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<String> f : futures) {
            assertThat(f.get(5, TimeUnit.SECONDS)).isEqualTo("remote-data");
        }

        assertThat(slowRemote.getReadCount()).isEqualTo(1);
    }

    private static class TestStorage<T> implements Storage<T> {
        private final String name;
        private final boolean readOnly;
        private T value;
        private final AtomicInteger readCount = new AtomicInteger();
        private final AtomicInteger writeCount = new AtomicInteger();

        TestStorage(String name, boolean readOnly) {
            this.name = name;
            this.readOnly = readOnly;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<T> read() throws Exception {
            readCount.incrementAndGet();
            return Optional.ofNullable(value);
        }

        @Override
        public void write(T value) throws Exception {
            if (readOnly) {
                throw new UnsupportedOperationException("Read-only storage");
            }
            writeCount.incrementAndGet();
            this.value = value;
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public Optional<Duration> getExpiration() {
            return Optional.empty();
        }

        void setValue(T value) {
            this.value = value;
        }

        T getValue() {
            return value;
        }

        int getReadCount() {
            return readCount.get();
        }

        int getWriteCount() {
            return writeCount.get();
        }
    }
}
