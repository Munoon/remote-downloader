package io.remotedownloader.util;

import org.junit.jupiter.api.function.Executable;

public class TestUtil {
    public static void assertWithReties(int retriesLimit, int timeout, Executable executable) throws Throwable {
        Throwable result = null;
        for (int i = 0; i < retriesLimit; i++) {
            try {
                executable.execute();
                return;
            } catch (Throwable e) {
                result = e;
                Thread.sleep(timeout);
            }
        }
        if (result != null) {
            throw result;
        }
    }
}
