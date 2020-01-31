/**
 * Copyright (C) 2020 Locomizer team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package ash.nazg.proximity;

import ash.nazg.spark.TestRunner;
import org.apache.hadoop.io.Text;
import org.apache.spark.api.java.JavaRDD;
import org.junit.Assert;
import org.junit.Test;

public class PostcodesAndTimezonesJoinTest {
    @Test
    public void partial2JoinTest() throws Exception {
        try (TestRunner underTest = new TestRunner("/configs/grid_join/config.postcodes_and_timezones_join.properties")) {

            JavaRDD<Text> dataset = (JavaRDD<Text>) underTest.go().get("signals_output");

            Assert.assertEquals(722, dataset.count());

        }
    }
}
