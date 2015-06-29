package de.glmtk.executables;

import static com.google.common.collect.Sets.newLinkedHashSet;
import static com.google.common.hash.Hashing.md5;
import static com.google.common.io.Files.hash;
import static de.glmtk.output.Output.println;
import static de.glmtk.util.Files.newBufferedReader;
import static de.glmtk.util.Files.newBufferedWriter;
import static de.glmtk.util.StringUtils.repeat;
import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.glmtk.Constants;
import de.glmtk.Glmtk;
import de.glmtk.GlmtkPaths;
import de.glmtk.cache.Cache;
import de.glmtk.cache.CacheSpecification;
import de.glmtk.cache.CacheSpecification.CacheImplementation;
import de.glmtk.cache.CompletionTrieCache;
import de.glmtk.common.Pattern;
import de.glmtk.common.Patterns;
import de.glmtk.common.Status;
import de.glmtk.exceptions.CliArgumentException;
import de.glmtk.logging.Logger;
import de.glmtk.options.BooleanOption;
import de.glmtk.options.PathsOption;
import de.glmtk.options.custom.ArgmaxExecutorOption;
import de.glmtk.options.custom.ArgmaxExecutorsOption;
import de.glmtk.options.custom.CorpusOption;
import de.glmtk.options.custom.EstimatorsOption;
import de.glmtk.querying.argmax.ArgmaxQueryCacheCreator;
import de.glmtk.querying.argmax.ArgmaxQueryExecutor;
import de.glmtk.querying.argmax.ArgmaxQueryExecutor.ArgmaxResult;
import de.glmtk.querying.estimator.Estimator;
import de.glmtk.querying.estimator.weightedsum.WeightedSumEstimator;
import de.glmtk.util.StringUtils;

public class GlmtkExpArgmaxCompare extends Executable {
    private static final Logger LOGGER = Logger.get(GlmtkExpArgmaxCompare.class);

    public static void main(String[] args) {
        new GlmtkExpArgmaxCompare().run(args);
    }

    private CorpusOption optionCorpus;
    private ArgmaxExecutorsOption optionArgmaxExecutors;
    private EstimatorsOption optionEstimators;
    private PathsOption optionQuery;
    private BooleanOption optionRandomAccess;
    private BooleanOption optionNoQueryCache;

    private Path corpus = null;
    private Path workingDir = null;
    private Set<String> executors = new LinkedHashSet<>();
    private Set<WeightedSumEstimator> estimators = new LinkedHashSet<>();
    private Set<Path> queries = new LinkedHashSet<>();
    private Boolean randomAccess = null;
    private Boolean noQueryCache = null;

    @Override
    protected String getExecutableName() {
        return "glmtk-exp-argmaxcompare";
    }

    @Override
    protected void registerOptions() {
        optionCorpus = new CorpusOption(null, "corpus",
                "Give corpus and maybe working directory.");
        optionArgmaxExecutors = new ArgmaxExecutorsOption("a",
                "argmax-executor",
                "Executors to compare. Can be specified multiple times.");
        optionEstimators = new EstimatorsOption("e", "estimator",
                "Estimators to use. Only weighted sum Estimators are allowed. "
                        + "Can be specified multiple times.").needWeightedSum();
        optionQuery = new PathsOption("q", "query",
                "Query the given files. Can be specified multiple times.").requireMustExist().requireFiles();
        optionRandomAccess = new BooleanOption("r", "random-access",
                "Use a HashMap baseed cache for any random access caches "
                        + "instead of default CompletionTrie based cache.");
        optionNoQueryCache = new BooleanOption("c", "no-querycache",
                "Do not create QueryCache.");

        commandLine.inputArgs(optionCorpus);
        commandLine.options(optionArgmaxExecutors, optionEstimators,
                optionQuery, optionRandomAccess, optionNoQueryCache);
    }

    @Override
    protected String getHelpHeader() {
        return "Performs comparision of argmax query executors.";
    }

    @Override
    protected String getHelpFooter() {
        return null;
    }

    @Override
    protected void parseOptions(String[] args) throws Exception {
        super.parseOptions(args);

        if (!optionCorpus.wasGiven())
            throw new CliArgumentException("%s missing.", optionCorpus);
        corpus = optionCorpus.getCorpus();
        workingDir = optionCorpus.getWorkingDir();

        executors = newLinkedHashSet(optionArgmaxExecutors.getArgmaxExecutors());
        if (executors.isEmpty())
            throw new CliArgumentException(String.format(
                    "No executors given, use %s.", optionArgmaxExecutors));

        @SuppressWarnings({"rawtypes", "unchecked"})
        List<WeightedSumEstimator> list = (List) optionEstimators.getEstimators();
        estimators = newLinkedHashSet(list);
        if (estimators.isEmpty())
            throw new CliArgumentException(String.format(
                    "No esimators given, use %s.", optionEstimators));

        queries = newLinkedHashSet(optionQuery.getPaths());
        if (queries.isEmpty())
            throw new CliArgumentException(String.format(
                    "No files to query given, use %s.", optionQuery));

        randomAccess = optionRandomAccess.getBoolean();
        noQueryCache = optionNoQueryCache.getBoolean();
    }

    @Override
    protected void exec() throws Exception {
        logFields();

        Glmtk glmtk = new Glmtk(config, corpus, workingDir);

        int neededOrder = getNeededOrder();

        CacheSpecification cacheSpec = new CacheSpecification();
        cacheSpec.withProgress();
        for (Estimator estimator : estimators)
            cacheSpec.addAll(estimator.getRequiredCache(neededOrder));
        cacheSpec.withCounts(Patterns.getMany("x")); // FIXME: Refactor this
        cacheSpec.withWords(); // FIXME: Refactor this

        Set<Pattern> requiredPatterns = cacheSpec.getRequiredPatterns();
        requiredPatterns.add(Patterns.get("x1111x")); // FIXME: Refactor this

        Status status = glmtk.getStatus();
        GlmtkPaths paths = glmtk.getPaths();

        for (Path queryFile : queries) {
            println();
            println(queryFile + ":");

            // TODO: there really should be an API for the following:
            String hash = hash(queryFile.toFile(), md5()).toString();

            GlmtkPaths queryCachePaths;
            if (noQueryCache)
                queryCachePaths = paths;
            else {
                ArgmaxQueryCacheCreator argmaxQueryCacheCreator = new ArgmaxQueryCacheCreator(
                        config);
                queryCachePaths = argmaxQueryCacheCreator.createQueryCache(
                        "argmax" + hash, queryFile, false, requiredPatterns,
                        status, paths);
            }

            CompletionTrieCache sortedAccessCache = (CompletionTrieCache) cacheSpec.withCacheImplementation(
                    CacheImplementation.COMPLETION_TRIE).build(queryCachePaths);
            Cache randomAccessCache = sortedAccessCache;
            if (randomAccess)
                randomAccessCache = cacheSpec.withCacheImplementation(
                        CacheImplementation.HASH_MAP).build(queryCachePaths);

            for (String executor : executors)
                for (WeightedSumEstimator estimator : estimators) {
                    estimator.setCache(randomAccessCache);
                    ArgmaxQueryExecutor argmaxQueryExecutor = ArgmaxExecutorOption.argmaxQueryExecutorFromString(
                            executor, estimator, randomAccessCache,
                            sortedAccessCache);

                    BigInteger timeSum = BigInteger.ZERO;
                    int n = 0;

                    String type = String.format("%s-%s", executor,
                            estimator.getName());
                    println("Querying %s...", type);
                    //                    ProgressBar progressBar = new ProgressBar("Querying",
                    //                            NioUtils.countNumberOfLines(queryFile));
                    //                    try (BufferedReader reader = Files.newBufferedReader(
                    //                            queryFile, Constants.CHARSET);
                    //                            BufferedWriter writer = Files.newBufferedWriter(
                    //                                    Paths.get(queryFile + "." + type),
                    //                                            Constants.CHARSET)) {
                    try (BufferedReader reader = newBufferedReader(System.in,
                            Constants.CHARSET);
                            BufferedWriter writer = newBufferedWriter(
                                    System.out, Constants.CHARSET)) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            int lastSpacePos = line.lastIndexOf(' ');
                            String history = line.substring(0, lastSpacePos);
                            String sequence = line.substring(lastSpacePos + 1);
                            long timeBefore = System.nanoTime();
                            List<ArgmaxResult> argmaxResults = argmaxQueryExecutor.queryArgmax(
                                    history, 5);
                            long timeAfter = System.nanoTime();

                            writer.append(format("%s : %s ", history, sequence));
                            for (ArgmaxResult a : argmaxResults)
                                writer.append(format("[%s-%e]",
                                        a.getSequence(), a.getProbability()));
                            writer.append('\n');

                            writer.append(repeat("-", 80)).append('\n');
                            for (int i = 1; i != sequence.length() + 1; ++i) {
                                String s = sequence.substring(0, i);
                                List<ArgmaxResult> a = argmaxQueryExecutor.queryArgmax(
                                        history, s, 5);
                                writer.append(s).append(
                                        repeat("-", sequence.length() - i)).append(
                                        " : ");
                                for (ArgmaxResult r : a)
                                    writer.append(format("[%s-%e]",
                                            r.getSequence(), r.getProbability()));
                                writer.append('\n');
                            }

                            writer.flush();

                            timeSum = timeSum.add(BigInteger.valueOf(timeAfter
                                    - timeBefore));
                            //                            progressBar.increase();
                            ++n;
                        }
                    }

                    BigInteger timePerArgmax = timeSum.divide(BigInteger.valueOf(n));
                    println("- Average prediciton time: %.3fms",
                            (timePerArgmax.floatValue() / 1000 / 1000));
                }
        }
    }

    private int getNeededOrder() throws IOException {
        int neededOrder = 0;
        for (Path queryFile : queries)
            try (BufferedReader reader = Files.newBufferedReader(queryFile,
                    Constants.CHARSET)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int order = StringUtils.split(line, ' ').size();
                    if (neededOrder < order)
                        neededOrder = order;
                }
            }
        return neededOrder;
    }

    private void logFields() {
        LOGGER.debug("%s %s", getExecutableName(), StringUtils.repeat("-",
                80 - getExecutableName().length()));
        LOGGER.debug("Corpus:     %s", corpus);
        LOGGER.debug("WorkingDir: %s", workingDir);
        LOGGER.debug("Executors:  %s", executors);
        LOGGER.debug("Estimators: %s", estimators);
        LOGGER.debug("Queries:    %s", queries);
    }
}
