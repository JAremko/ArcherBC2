package loggingutil;

import java.io.IOException;
import java.util.logging.FileHandler;

public final class FileHandlerFactory {

    private FileHandlerFactory() {
    }

    public static FileHandler createRotating(String pattern,
                                             int limit,
                                             int count,
                                             boolean append) throws IOException {
        return new FileHandler(pattern, limit, count, append);
    }
}
