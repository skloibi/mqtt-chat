package skloibi.utils;

import java.util.function.Function;

public interface TFunction<T, R> extends Function<T, R> {
    @Override
    default R apply(T t) {
        try {
            return applyOrThrow(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    R applyOrThrow(T t) throws Exception;
}
