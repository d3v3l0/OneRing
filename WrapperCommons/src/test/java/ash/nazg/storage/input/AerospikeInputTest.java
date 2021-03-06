/**
 * Copyright (C) 2020 Locomizer team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package ash.nazg.storage.input;

import ash.nazg.config.WrapperConfig;
import ash.nazg.storage.TestStorageWrapper;
import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import org.apache.spark.api.java.JavaRDDLike;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AerospikeInputTest {
    @Test
    @Ignore
    public void testInput() throws Exception {
        AerospikeClient as = null;
        try (TestStorageWrapper underTest = new TestStorageWrapper(false, "/config.AerospikeInput.properties")) {

            WrapperConfig config = underTest.getConfig();

            as = new AerospikeClient(
                    config.getInputProperty("aerospike.host", null, "localhost"),
                    Integer.parseInt(config.getInputProperty("aerospike.port", null, "3000"))
            );
            as.truncate(null, "test", "profiles_keyed", null);

            underTest.go();

            Map<String, JavaRDDLike> res = underTest.result;

            List c = res.get("profiles").collect();
            for (int i = 0; i < c.size(); i++) {
                as.put(null, new Key("test", "profiles_keyed", "" + i), new Bin("v", String.valueOf(c.get(i))));
            }

            int part_count = res.get("profiles2").getNumPartitions();

            assertEquals(20, part_count);

            long profiles2_count = res.get("profiles2_out").count();
            assertTrue(profiles2_count > 0L);
            assertEquals(res.get("profiles").count(), profiles2_count);
        } finally {
            if (as != null) {
                as.truncate(null, "test", "profiles_keyed", null);
            }
        }
    }
}
