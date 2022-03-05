package co.tinode.tinsdk.model;

public class Pair<F, S> {
    public final F first;
    public S second;

    public Pair(F f, S s) {
        first = f;
        second = s;
    }
}
