package peergos.shared.user;

import peergos.shared.MaybeMultihash;
import peergos.shared.cbor.CborObject;
import peergos.shared.crypto.hash.PublicKeyHash;
import peergos.shared.mutable.HashCasPair;
import peergos.shared.mutable.MutablePointers;
import peergos.shared.storage.ContentAddressedStorage;
import peergos.shared.util.AsyncLock;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class WriteSynchronizer {

    private final MutablePointers mutable;
    private final ContentAddressedStorage dht;
    private final Map<PublicKeyHash, AsyncLock<CommittedWriterData>> pending = new ConcurrentHashMap<>();

    public WriteSynchronizer(MutablePointers mutable, ContentAddressedStorage dht) {
        this.mutable = mutable;
        this.dht = dht;
    }

    private CompletableFuture<CommittedWriterData> getWriterData(PublicKeyHash controller, MaybeMultihash hash) {
        if (!hash.isPresent())
            return CompletableFuture.completedFuture(new CommittedWriterData(MaybeMultihash.empty(), WriterData.createEmpty(controller)));
        return dht.get(hash.get())
                .thenApply(cborOpt -> {
                    if (! cborOpt.isPresent())
                        throw new IllegalStateException("Couldn't retrieve WriterData from dht! " + hash);
                    return new CommittedWriterData(hash, WriterData.fromCbor(cborOpt.get()));
                });
    }

    public CompletableFuture<CommittedWriterData> getWriterData(PublicKeyHash owner, PublicKeyHash hash) {
        return mutable.getPointer(owner, hash)
                .thenCompose(dataOpt -> dht.getSigningKey(hash)
                        .thenApply(signer -> dataOpt.isPresent() ?
                                HashCasPair.fromCbor(CborObject.fromByteArray(signer.get().unsignMessage(dataOpt.get()))).updated :
                                MaybeMultihash.empty())
                        .thenCompose(x -> getWriterData(hash, x)));
    }

    public CompletableFuture<CommittedWriterData> getCurrentWriterData(PublicKeyHash owner,
                                                                        PublicKeyHash writer,
                                                                        Function<CommittedWriterData, CompletableFuture<CommittedWriterData>> updater) {
        // This is subtle, but we need to ensure that there is only ever 1 thenAble waiting on the future for a given key
        // otherwise when the future completes, then the two or more waiters will both proceed with the existing hash,
        // and whoever commits first will win. We also need to retrieve the writer data again from the network after
        // a previous transaction has completed (another node/user may have updated the mapping)
        return pending.computeIfAbsent(writer, w -> new AsyncLock<>(getWriterData(owner, w)))
                .runWithLock(current -> updater.apply(current), () -> getWriterData(owner, writer));
    }
}
