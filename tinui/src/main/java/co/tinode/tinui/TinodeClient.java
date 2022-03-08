package co.tinode.tinui;

import co.tinode.tinsdk.Storage;
import co.tinode.tinsdk.Tinode;

public class TinodeClient {

    private static Tinode instance;
    /**
     * Get instance of Tinode
     *
     * @return Tinode instance
     */
    public static Tinode getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TinodeClient.Builder::build() must be called before obtaining Tinode instance");
        }
        return instance;
    }

    public static class Builder extends TinodeClient.TinodeClientBuilder {

        private final String appname;
        private final String apikey;
        private Storage storage = null;
        private Tinode.EventListener eventListener = null;

        public Builder(String appname, String apikey) {
            this.appname = appname;
            this.apikey = apikey;
        }

        public Builder setStorage(Storage storage) {
            this.storage = storage;
            return this;
        }

        public Builder setEventListener(Tinode.EventListener listener) {
            this.eventListener = listener;
            return this;
        }

        @Override
        protected Tinode internalBuild() {
            return new Tinode(appname, apikey, storage, eventListener);
        }
    }

    public abstract static class TinodeClientBuilder {
        /**
         * Create a [Tinode] instance based on the current configuration
         * of the [Builder].
         */
        public Tinode build() {
            TinodeClient.instance = internalBuild();
            return instance;
        }

        protected abstract Tinode internalBuild();
    }
}
