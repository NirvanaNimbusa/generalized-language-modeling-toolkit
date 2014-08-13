package de.glmtk.counting;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.glmtk.counting.Sequencer.WriteQueueItem;
import de.glmtk.pattern.Pattern;

public class SequencerWriteTask implements Runnable {

    private static final Logger LOGGER = LogManager
            .getLogger(SequencerWriteTask.class);

    private Sequencer sequencer;

    private BlockingQueue<WriteQueueItem> writeQueue;

    private Map<Pattern, BufferedWriter> writers;

    public SequencerWriteTask(
            Sequencer sequencer,
            BlockingQueue<WriteQueueItem> writeQueue,
            Path outputDir,
            Map<Integer, Set<Pattern>> patternsByLength,
            long writerMemory) throws IOException {
        this.sequencer = sequencer;
        this.writeQueue = writeQueue;

        int numPatterns = 0;
        for (Set<Pattern> patterns : patternsByLength.values()) {
            numPatterns += patterns.size();
        }

        int memoryPerWriter = (int) writerMemory / numPatterns;
        writers = new HashMap<Pattern, BufferedWriter>();
        for (Set<Pattern> patterns : patternsByLength.values()) {
            for (Pattern pattern : patterns) {
                BufferedWriter writer =
                        new BufferedWriter(new OutputStreamWriter(
                                Files.newOutputStream(outputDir.resolve(pattern
                                        .toString()))), memoryPerWriter);
                writers.put(pattern, writer);
            }
        }
    }

    @Override
    public void run() {
        try {
            while (!(sequencer.isCalculatingDone() && writeQueue.isEmpty())) {
                WriteQueueItem witem =
                        writeQueue.poll(10, TimeUnit.MILLISECONDS);
                if (witem != null) {
                    BufferedWriter writer = writers.get(witem.pattern);
                    writer.write(witem.sequence);
                    writer.write('\n');
                }
            }

            for (BufferedWriter writer : writers.values()) {
                writer.close();
            }
        } catch (InterruptedException | IOException e) {
            throw new IllegalStateException(e);
        }

        LOGGER.debug("Sequencer writing done.");
    }

}
