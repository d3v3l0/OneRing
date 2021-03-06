/**
 * Copyright (C) 2020 Locomizer team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package ash.nazg.storage.output;

import ash.nazg.storage.TestStorageWrapper;
import com.aerospike.client.*;
import com.aerospike.client.policy.ScanPolicy;
import ash.nazg.config.WrapperConfig;
import org.apache.spark.api.java.JavaRDDLike;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AerospikeOutputTest {
    @Test
    @Ignore
    public void testOutput() throws Exception {

        AerospikeClient as = null;
        try (TestStorageWrapper underTest = new TestStorageWrapper(true, "/config.AerospikeOutput.properties")) {
            WrapperConfig config = underTest.getConfig();

            as = new AerospikeClient(
                    config.getOutputProperty("aerospike.host", null, "localhost"),
                    Integer.valueOf(config.getOutputProperty("aerospike.port", null, "3000"))
            );

            underTest.go();
            Map<String, JavaRDDLike> res = underTest.result;

            TestScanCallback scanCallback = new TestScanCallback();
            ScanPolicy sp = new ScanPolicy();
            sp.includeBinData = false;
            as.scanAll(sp, "test", "profiles_keyed", scanCallback);

            long profiles_keyed = res.get("profiles_keyed").count();
            assertTrue(profiles_keyed > 0L);
            assertEquals(res.get("profiles").count(), profiles_keyed);
            assertEquals(profiles_keyed, scanCallback.asRec);
        } finally {
            if (as != null) {
                as.truncate(null, "test", "profiles_keyed", null);
            }
        }
    }

    private class TestScanCallback implements ScanCallback {
        long asRec = 0;

        @Override
        public void scanCallback(Key key, Record record) throws AerospikeException {
            asRec++;
        }
    }
}
