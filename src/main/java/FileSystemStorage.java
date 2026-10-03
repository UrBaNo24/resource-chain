import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class FileSystemStorage<T> implements Storage<T>{

    private final String name;
    private final Path filePath;
    private final Class<T> valueType;
    private final Duration expiration;
    private final ObjectMapper objectMapper;


    public FileSystemStorage(Path filePath, Class<T> valueType) {
        this("FileSystem", filePath, valueType, Duration.ofHours(4), new ObjectMapper());
    }

    public FileSystemStorage(String name, Path filePath, Class<T> valueType, Duration expiration, ObjectMapper objectMapper) {

        this.name = name;
        this.filePath = filePath;
        this.valueType = valueType;
        this.expiration = expiration;
        this.objectMapper = objectMapper;
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
    public Optional<T> read() throws Exception {

        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        FileTime lastModified = Files.getLastModifiedTime(filePath);
        Instant lastModifiedInstant = lastModified.toInstant();

        Instant expiresAt = lastModifiedInstant.plus(expiration);

        if(Instant.now().isAfter(expiresAt)) {
            return Optional.empty();
        }

        byte[] bytes = Files.readAllBytes(filePath);

        if(bytes.length == 0) {
            return Optional.empty();
        }

        T value = objectMapper.readValue(bytes, valueType);

        return Optional.of(value);
    }

    @Override
    public void write(T value) throws IOException {

        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), value);
    }
}
