/*
 * Generalized Language Modeling Toolkit (GLMTK)
 * 
 * Copyright (C) 2015 Lukas Schmelzeisen
 * 
 * GLMTK is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * GLMTK is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * GLMTK. If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the AUTHORS file for contributors.
 */

package de.glmtk.querying;

import static de.glmtk.common.Output.OUTPUT;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import de.glmtk.Constants;
import de.glmtk.Glmtk;
import de.glmtk.GlmtkPaths;
import de.glmtk.cache.OldCache;
import de.glmtk.cache.CacheBuilder;
import de.glmtk.common.Output.Phase;
import de.glmtk.common.Output.Progress;
import de.glmtk.logging.Logger;
import de.glmtk.querying.estimator.Estimator;
import de.glmtk.querying.estimator.Estimators;
import de.glmtk.querying.probability.QueryExecutor;
import de.glmtk.querying.probability.QueryMode;
import de.glmtk.testutil.TestCorporaTest;
import de.glmtk.testutil.TestCorpus;
import de.glmtk.util.NioUtils;

@RunWith(Parameterized.class)
public class QueryCacheTest extends TestCorporaTest {
    private static final Logger LOGGER = Logger.get(QueryCacheTest.class);

    private static TestCorpus testCorpus = TestCorpus.EN0008T;
    private static Path testFile = Constants.TEST_RESSOURCES_DIR.resolve("en0008t.testing.5");

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        //@formatter:off
        return Arrays.asList(new Object[][] {
                {Estimators.MKN},
                {Estimators.GLM}
        });
        //@formatter:on
    }

    private Estimator estimator;

    public QueryCacheTest(Estimator estimator) {
        this.estimator = estimator;
    }

    @Test
    public void testQueryingWithCacheEqualsWithout() throws Exception {
        Glmtk glmtk = testCorpus.getGlmtk();

        CacheBuilder requiredCache = estimator.getRequiredCache(5);

        glmtk.count(requiredCache.getCountsPatterns());

        GlmtkPaths paths = glmtk.getPaths();
        GlmtkPaths queryCachePaths = glmtk.provideQueryCache(testFile,
                requiredCache.getCountsPatterns());

        LOGGER.info("Loading cache without QueryCache...");
        OldCache cache = requiredCache.withProgress().build(paths);
        LOGGER.info("Loading cache with QueryCache...");
        OldCache queryCache = requiredCache.withProgress().build(queryCachePaths);

        QueryMode queryMode = QueryMode.newCond(5);
        QueryExecutor executor = new QueryExecutor(paths, queryMode, estimator,
                5);

        OUTPUT.setPhase(Phase.QUERYING);
        Progress progress = OUTPUT.newProgress(NioUtils.calcNumberOfLines(testFile));

        Logger.setTraceEnabled(false);
        try (BufferedReader reader = Files.newBufferedReader(testFile,
                Constants.CHARSET)) {
            String line;
            while ((line = reader.readLine()) != null) {
                double probExpected = Double.NaN, probActual = Double.NaN;
                try {
                    estimator.setCache(cache);
                    probExpected = executor.querySequence(line);
                    estimator.setCache(queryCache);
                    probActual = executor.querySequence(line);
                    if (Math.abs(probExpected - probActual) > Math.abs(probExpected) / 1e6)
                        throw new Exception("failAssert");
                } catch (Throwable t) {
                    Logger.setTraceEnabled(true);

                    estimator.setCache(cache);
                    LOGGER.trace("Query without QueryCache:");
                    executor.querySequence(line);

                    estimator.setCache(queryCache);
                    LOGGER.trace("Query with QueryCache:");
                    executor.querySequence(line);

                    Logger.setTraceEnabled(false);

                    if (t.getMessage().equals("failAssert"))
                        fail(String.format("Expected <%e> but was <%e>.",
                                probExpected, probActual));

                    throw t;
                }

                progress.increase(1);
            }
        }
        Logger.setTraceEnabled(true);
    }
}
