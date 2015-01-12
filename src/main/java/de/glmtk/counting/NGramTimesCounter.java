package de.glmtk.counting;

import static de.glmtk.common.Output.OUTPUT;
import static de.glmtk.util.PrintUtils.humanReadableByteCount;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.glmtk.Constants;
import de.glmtk.Glmtk;
import de.glmtk.common.Config;
import de.glmtk.common.Output.Phase;
import de.glmtk.common.Output.Progress;
import de.glmtk.common.Pattern;
import de.glmtk.common.Status;
import de.glmtk.counts.NGramTimes;
import de.glmtk.files.CountsReader;
import de.glmtk.files.NGramTimesWriter;
import de.glmtk.util.ThreadUtils;

public class NGramTimesCounter {
    private static final Logger LOGGER = LogManager.getFormatterLogger(NGramTimesCounter.class);

    private class Thread implements Callable<Object> {
        private Pattern pattern;
        private NGramTimes nGramTimes;

        @Override
        public Object call() throws InterruptedException, IOException {
            while (!patternQueue.isEmpty()) {
                pattern = patternQueue.poll(Constants.QUEUE_TIMEOUT,
                        TimeUnit.MILLISECONDS);
                if (pattern == null)
                    continue;

                LOGGER.debug("Counting pattern '%s'.", pattern);

                countNGramTimes();
                nGramTimesForPattern.put(pattern, nGramTimes);

                LOGGER.debug("Finished pattern '%s'.", pattern);

                synchronized (progress) {
                    progress.increase(1);
                }
            }

            LOGGER.debug("Thread finished.");
            return null;
        }

        private void countNGramTimes() throws IOException {
            nGramTimes = new NGramTimes();

            Path inputDir = pattern.isAbsolute()
                    ? absoluteDir
                            : continuationDir;
            Path inputFile = inputDir.resolve(pattern.toString());
            int memory = (int) Math.min(Files.size(inputFile), readerMemory);
            try (CountsReader reader = new CountsReader(inputFile,
                    Constants.CHARSET, memory)) {
                while (reader.readLine() != null)
                    nGramTimes.add(reader.getCount());
            }
        }
    }

    private Config config;

    private Progress progress;
    private Path outputFile;
    private Path absoluteDir;
    private Path continuationDir;
    private BlockingQueue<Pattern> patternQueue;
    private ConcurrentHashMap<Pattern, NGramTimes> nGramTimesForPattern;
    private int readerMemory;

    public NGramTimesCounter(Config config) {
        this.config = config;
    }

    public void count(Status status,
                      Path outputFile,
                      Path absoluteDir,
                      Path continuationDir) throws Exception {
        OUTPUT.setPhase(Phase.NGRAM_TIMES_COUNTING);

        if (status.isNGramTimesCounted()) {
            LOGGER.debug("Status reports ngram times already counted, returning.");
            return;
        }

        Set<Pattern> patterns = status.getCounted();

        this.outputFile = outputFile;
        this.absoluteDir = absoluteDir;
        this.continuationDir = continuationDir;
        patternQueue = new LinkedBlockingQueue<>(patterns);
        nGramTimesForPattern = new ConcurrentHashMap<>();
        progress = OUTPUT.newProgress(patternQueue.size());
        calculateMemory();

        List<Callable<Object>> threads = new LinkedList<>();
        for (int i = 0; i != config.getNumberOfThreads(); ++i)
            threads.add(new Thread());

        ThreadUtils.executeThreads(config.getNumberOfThreads(), threads);

        Glmtk.validateExpectedResults("ngram times couting", patterns,
                nGramTimesForPattern.keySet());

        writeToFile();
        status.setNGramTimesCounted();
    }

    private void calculateMemory() {
        readerMemory = config.getMemoryReader();
        LOGGER.debug("readerMemory = %s", humanReadableByteCount(readerMemory));
    }

    private void writeToFile() throws IOException {
        SortedMap<Pattern, NGramTimes> sortedNGramTimesForPattern = new TreeMap<>(
                nGramTimesForPattern);
        try (NGramTimesWriter writer = new NGramTimesWriter(outputFile,
                Constants.CHARSET)) {
            for (Entry<Pattern, NGramTimes> entry : sortedNGramTimesForPattern.entrySet())
                writer.append(entry.getKey(), entry.getValue());
        }
    }
}
