import java.time.Duration;
import java.util.Optional;

public interface Storage<T> {

    String getName();

    Optional<T> read() throws Exception;

    void write (T value) throws Exception;

    boolean isReadOnly();


    Optional<Duration> getExpiration();

}
