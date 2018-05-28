package skloibi.utils;

import java.util.function.Function;

public interface T {

    static <A, B> _2<A, B> of(A a, B b) {
        return new _2<>(a, b);
    }

    class _2<A, B> {
        public final A _1;
        public final B _2;

        public _2(A _1, B _2) {
            this._1 = _1;
            this._2 = _2;
        }

        public <AA> _2<AA, B> map_1(Function<A, AA> f) {
            return new _2<>(f.apply(_1), _2);
        }

        public <BB> _2<A, BB> map_2(Function<B, BB> f) {
            return new _2<>(_1, f.apply(_2));
        }
    }
}
