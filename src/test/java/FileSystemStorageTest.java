import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteAndReadValueFromDisk() throws Exception {
        Path filePath = tempDir.resolve("cache.json");
        FileSystemStorage<ExchangeRateList> storage = new FileSystemStorage<>(filePath, ExchangeRateList.class);

        ExchangeRateList data = new ExchangeRateList("disclaimer", "license", 1700000000L, "USD", Map.of("EUR", new BigDecimal("0.92")));

        storage.write(data);
        Optional<ExchangeRateList> retrieved = storage.read();

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().base()).isEqualTo("USD");
        assertThat(retrieved.get().getRate("EUR")).contains(new BigDecimal("0.92"));
    }

    @Test
    void shouldSurviveRestartByReadingExistingFile() throws Exception {
        Path filePath = tempDir.resolve("restart-cache.json");

        // First application lifecycle: write data to disk
        FileSystemStorage<ExchangeRateList> initialInstance = new FileSystemStorage<>(filePath, ExchangeRateList.class);
        ExchangeRateList rates = new ExchangeRateList("disclaimer", "license", 1700000000L, "USD", Map.of("GBP", new BigDecimal("0.78")));
        initialInstance.write(rates);

        // Simulated application restart: brand new instance pointing to same file
        FileSystemStorage<ExchangeRateList> restartedInstance = new FileSystemStorage<>(filePath, ExchangeRateList.class);
        Optional<ExchangeRateList> recovered = restartedInstance.read();

        assertThat(recovered).isPresent();
        assertThat(recovered.get().base()).isEqualTo("USD");
        assertThat(recovered.get().getRate("GBP")).contains(new BigDecimal("0.78"));
    }

    @Test
    void shouldExpireWhenFileIsOlderThanConfiguredDuration() throws Exception {
        Path filePath = tempDir.resolve("expired-cache.json");
        FileSystemStorage<ExchangeRateList> storage = new FileSystemStorage<>(filePath, ExchangeRateList.class);

        ExchangeRateList rates = new ExchangeRateList("disclaimer", "license", 1700000000L, "USD", Map.of("EUR", new BigDecimal("0.92")));
        storage.write(rates);

        // Simulate file written 5 hours ago (default expiration is 4 hours)
        Instant fiveHoursAgo = Instant.now().minus(Duration.ofHours(5));
        Files.setLastModifiedTime(filePath, FileTime.from(fiveHoursAgo));

        Optional<ExchangeRateList> result = storage.read();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenFileDoesNotExist() throws Exception {
        Path nonExistent = tempDir.resolve("missing.json");
        FileSystemStorage<ExchangeRateList> storage = new FileSystemStorage<>(nonExistent, ExchangeRateList.class);

        Optional<ExchangeRateList> result = storage.read();

        assertThat(result).isEmpty();
    }
}
