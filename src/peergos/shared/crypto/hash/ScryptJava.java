package peergos.shared.crypto.hash;
import java.util.logging.*;

import java.security.*;
import java.util.concurrent.CompletableFuture;

import peergos.shared.scrypt.com.lambdaworks.crypto.SCrypt;
import peergos.shared.user.*;

public class ScryptJava implements Hasher {
	private static final Logger LOG = Logger.getGlobal();
    private static final int LOG_2_MIN_RAM = 17;

    @Override
    public CompletableFuture<byte[]> hashToKeyBytes(String username, String password, SecretGenerationAlgorithm algorithm) {
        CompletableFuture<byte[]> res = new CompletableFuture<>();
        if (algorithm.getType() == SecretGenerationAlgorithm.Type.Scrypt) {
            byte[] hash = Hash.sha256(password.getBytes());
            byte[] salt = username.getBytes();
            try {
                ScryptGenerator params = (ScryptGenerator) algorithm;
                long t1 = System.currentTimeMillis();
                int parallelism = params.parallelism;
                int nOutputBytes = params.outputBytes;
                int cpuCost = params.cpuCost;
                int memoryCost = 1 << params.memoryCost; // Amount of ram required to run algorithm in bytes
                byte[] scryptHash = SCrypt.scrypt(hash, salt, memoryCost, cpuCost, parallelism, nOutputBytes);
                long t2 = System.currentTimeMillis();
                LOG.info("Scrypt hashing took: " + (t2 - t1) + " mS");
                res.complete(scryptHash);
                return res;
            } catch (GeneralSecurityException gse) {
                res.completeExceptionally(gse);
            }
            return res;
        }
        throw new IllegalStateException("Unknown user generation algorithm: " + algorithm);
    }

    @Override
    public byte[] sha256(byte[] input) {
        return Hash.sha256(input);
    }
}
