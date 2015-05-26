/*
 * Generalized Language Modeling Toolkit (GLMTK)
 * 
 * Copyright (C) 2014-2015 Lukas Schmelzeisen
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

package de.glmtk.common;

import static com.google.common.base.Throwables.getStackTraceAsString;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.Formatter;
import java.util.concurrent.TimeUnit;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.AnsiOutputStream;
import org.fusesource.jansi.WindowsAnsiOutputStream;

import de.glmtk.Constants;
import de.glmtk.logging.Logger;
import de.glmtk.logging.Logger.Level;
import de.glmtk.util.ReflectionUtils;
import de.glmtk.util.StringUtils;
import de.glmtk.util.ThreadUtils;

public enum Output {
    OUTPUT;

    public static enum Phase {
        //@formatter:off
        //// GLMTK

        // Tagging
        TAGGING(1, 1, "Tagging Training"),

        // Counting
        ABSOLUTE_CHUNKING(1, 6, "Chunking Absolute"),
        ABSOLUTE_MERGING(2, 6, "Merging Absolute"),
        CONTINUATION_CHUNKING(3, 6, "Chunking Continuation"),
        CONTINUATION_MERGING(4, 6, "Merging Continuation"),
        NGRAM_TIMES_COUNTING(5, 6, "NGram Times Counting"),
        LENGTH_DISTRIBUATION_CALCULATING(6, 6, "Length Distribution Calculating"),

        // Learning
        CALCULATING_DISCOUNTS(1, 3, "Calculating Discounts"),
        CALCULATING_ALPHAS(2, 3, "Calculating Alphas"),
        CALCULATING_LAMBDAS(3, 3, "Calculating Lambdas"),

        // Cache
        LOADING_CACHE(1, 1, "Loading Counts"),

        // QueryCache
        SCANNING_COUNTS(1, 1, "Scanning Counts"),

        // Querying
        QUERYING(1, 2, "Querying"),
        ASSEMBLING(1, 2, "Assembling"),

        //// GLMTK EXPSETUP

        SPLITTING_CORPUS(1, 2, "Splitting Corpus"),
        SELECTING_NGRAMS(2, 2, "Selecting NGrams");

        //@formatter:on

        public static final int MAX_NAME_LENGTH;
        static {
            int max = 0;
            for (Phase phase : Phase.values())
                if (max < phase.name.length())
                    max = phase.name.length();
            MAX_NAME_LENGTH = max;
        }

        private int number;
        private int maxNumber;
        private String name;

        private Phase(int number,
                      int maxNumber,
                      String name) {
            this.number = number;
            this.maxNumber = maxNumber;
            this.name = name;
        }

        public int getNumber() {
            return number;
        }

        public int getMaxNumber() {
            return maxNumber;
        }

        public String getName() {
            return name;
        }
    }

    private static final Logger LOGGER = Logger.get(Output.class);
    private static final double DISABLE_PERCENT = -1.0;

    /**
     * We use this function to replace
     * {@link AnsiConsole#wrapOutputStream(OutputStream)}, because we want do do
     * our own checking if we wan't ansi codes.
     */
    private static OutputStream wrapStderrStream(OutputStream stream) {
        String os = System.getProperty("os.name");
        if (os.startsWith("Windows"))
            try {
                return new WindowsAnsiOutputStream(stream);
            } catch (Throwable ignore) {
                return new AnsiOutputStream(stream);
            }

        return new FilterOutputStream(stream) {
            @Override
            public void close() throws IOException {
                write(AnsiOutputStream.REST_CODE);
                flush();
                super.close();
            }
        };
    }

    public class Progress {
        private long current;
        private long total;
        private long lastConsoleUpdate;
        private long lastLogUpdate;
        private boolean updateConsole;
        private boolean updateLog;

        private Progress(long total) {
            current = 0;
            this.total = total;
            lastConsoleUpdate = System.currentTimeMillis();
            lastLogUpdate = lastConsoleUpdate;
            updateConsole = config.getUpdateIntervalConsole() != 0;
            updateLog = config.getUpdateIntervalLog() != 0;
        }

        public void increase(long increase) {
            set(current + increase);
        }

        public void set(double percent) {
            set((long) Math.ceil(percent * total));
        }

        public void set(long current) {
            this.current = current;

            if (updateConsole || updateLog) {
                long time = System.currentTimeMillis();
                if (updateConsole
                        && time - lastConsoleUpdate >= config.getUpdateIntervalConsole()) {
                    OUTPUT.setPercent((double) current / total);
                    lastConsoleUpdate = time;
                }
                if (updateLog
                        && time - lastLogUpdate >= config.getUpdateIntervalLog()) {
                    LOGGER.info("%6.2f%%", 100.0 * current / total);
                    lastLogUpdate = time;
                }
            }
        }
    }

    public Config config;
    private long lastUpdateConsoleParams;
    private boolean updateConsoleParams;
    private int terminalWidth;
    private int numPercentegebarBlocks;
    private boolean ansiEnabled;
    private Phase phase;
    private double percent;

    /**
     * {@code true} if the last print was a call to {@link #beginPhases(String)}
     * , or followed only by {@link #printPhase()} calls. {@code false} if not.
     */
    private boolean lastPrintBeginPhases = false;

    /**
     * {@code true} if the last print was a call to {@link #printPhase()}.
     * {@code false} if not.
     */
    private boolean lastPrintPhase = false;

    public void initialize(Config config) {
        this.config = config;
        lastUpdateConsoleParams = 0;
        updateConsoleParams = config.getUpdateIntervalConsoleParams() != 0;
        terminalWidth = 80;
        ansiEnabled = false;
        phase = null;
        percent = 0;

        updateConsoleParams();
    }

    // TODO: function name! we try to do something but we don't get a return value?
    public void tryToEnableAnsi() {
        boolean isttyStderr = Boolean.parseBoolean(System.getProperty("glmtk.isttyStderr"));
        if (!isttyStderr) {
            LOGGER.debug("Ansi Codes will not be enabled because ISTTY_STDERR is 'false'.");
            return;
        }

        try {
            ReflectionUtils.setFinalStaticField(
                    AnsiConsole.class.getField("out"), AnsiConsole.system_out);
            ReflectionUtils.setFinalStaticField(
                    AnsiConsole.class.getField("err"), new PrintStream(
                            wrapStderrStream(AnsiConsole.system_err)));
            AnsiConsole.systemInstall();
            ansiEnabled = true;
            LOGGER.debug("Ansi Codes enabled.");
        } catch (Throwable e) {
            LOGGER.error("Ansi Codes could not be enabled because: "
                    + getStackTraceAsString(e));
        }
    }

    public void disableAnsi() {
        ansiEnabled = false;
    }

    public boolean isAnsiEnabled() {
        return ansiEnabled;
    }

    private void updateConsoleParams() {
        int lengthWithoutBlocks = getPhaseStringLengthWithoutBlocks();

        if (!isAnsiEnabled()) {
            numPercentegebarBlocks = 80 - lengthWithoutBlocks;
            return;
        }
        if (!updateConsoleParams && lastUpdateConsoleParams != 0)
            return;

        long time = System.currentTimeMillis();
        if (time - lastUpdateConsoleParams >= config.getUpdateIntervalConsoleParams()) {
            updateTerminalWidth();
            numPercentegebarBlocks = terminalWidth - lengthWithoutBlocks;
            lastUpdateConsoleParams = time;
        }
    }

    /**
     * See <a href="http://stackoverflow.com/a/18883172/211404">Stack Overflow:
     * Can I find the console width with Java?</a>.
     */
    private void updateTerminalWidth() {
        try {
            Process tputColsProc = Runtime.getRuntime().exec(
                    new String[] {"bash", "-c", "tput cols 2> /dev/tty"});
            ThreadUtils.executeProcess(tputColsProc, Constants.MAX_IDLE_TIME,
                    TimeUnit.MILLISECONDS);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(tputColsProc.getInputStream(),
                            Constants.CHARSET))) {
                terminalWidth = Integer.parseInt(reader.readLine());
            }
        } catch (Throwable e) {
        }
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        percent = DISABLE_PERCENT;
        printPhase();

        LOGGER.info("(%d/%d) %s", phase.getNumber(), phase.getMaxNumber(),
                phase.getName());
    }

    public void setPercent(double percent) {
        this.percent = percent;
        printPhase();
    }

    public String bold(Object object) {
        return bold(object.toString());
    }

    public String bold(String message) {
        if (!isAnsiEnabled())
            return message;
        return Ansi.ansi().bold() + message + Ansi.ansi().boldOff();
    }

    public Progress newProgress(long total) {
        return new Progress(total);
    }

    public void beginPhases(String message) {
        System.err.println(message);

        lastPrintBeginPhases = true;
        lastPrintPhase = false;
    }

    public void endPhases(String message) {
        if (isAnsiEnabled() && lastPrintBeginPhases && lastPrintPhase)
            System.err.print(Ansi.ansi().cursorUp(1).eraseLine().cursorUp(1).eraseLine());

        System.err.println(message);

        lastPrintBeginPhases = false;
        lastPrintPhase = false;
    }

    private void printPhase() {
        if (phase == null)
            return;

        updateConsoleParams();

        if (isAnsiEnabled() && lastPrintPhase)
            System.err.print(Ansi.ansi().cursorUp(1).eraseLine());

        String message = getPhaseString();

        System.err.println(message);

        lastPrintPhase = true;
    }

    private String getPhaseString() {
        try (Formatter f = new Formatter()) {
            f.format("(%d/%d) ", phase.getNumber(), phase.getMaxNumber());
            if (percent == DISABLE_PERCENT)
                f.format("%s...", phase.getName());
            else {
                int numBlocks = (int) Math.ceil(percent
                        * numPercentegebarBlocks);
                f.format("%-" + Phase.MAX_NAME_LENGTH + "s [%s%s] %6.2f%%",
                        phase.getName(), StringUtils.repeat("#", numBlocks),
                        StringUtils.repeat("-", numPercentegebarBlocks
                                - numBlocks), 100.0 * percent);
            }

            return f.toString();
        }
    }

    private int getPhaseStringLengthWithoutBlocks() {
        Phase oldPhase = phase;
        double oldPercent = percent;
        int oldNumPercentegebarBlocks = numPercentegebarBlocks;

        phase = Phase.ABSOLUTE_CHUNKING; // just set to something thats not null
        percent = 0.0;                   // make sure is != DISABLE_PERCENT
        numPercentegebarBlocks = 0;

        int result = getPhaseString().length();

        phase = oldPhase;
        percent = oldPercent;
        numPercentegebarBlocks = oldNumPercentegebarBlocks;

        return result;
    }

    public void printMessage(Object message) {
        printMessage(message.toString());
    }

    public void printMessage(String message) {
        logWithoutAnsi(Level.INFO, message);
        System.err.println(message);

        lastPrintBeginPhases = false;
        lastPrintPhase = false;
    }

    public void printError(Object object) {
        printError(object.toString());
    }

    public void printError(Throwable throwable) {
        printError(throwable.getMessage());
    }

    public void printError(String message) {
        if (message == null || message.isEmpty())
            message = "A critical error has occured, program execution had to be stopped.";

        StringBuilder print = new StringBuilder();
        for (String line : StringUtils.split(message, '\n')) {
            if (isAnsiEnabled())
                print.append(Ansi.ansi().fg(Color.RED));
            print.append("Error: ").append(line);
            if (isAnsiEnabled())
                print.append(Ansi.ansi().reset());
            print.append('\n');
        }

        // remove last newline
        String printStr = print.toString();
        printStr = printStr.substring(0, printStr.length() - 1);

        logWithoutAnsi(Level.ERROR, printStr);
        System.err.println(printStr);

        lastPrintBeginPhases = false;
        lastPrintPhase = false;
    }

    public void printWarning(Object object) {
        printWarning(object.toString());
    }

    public void printWarning(String message) {
        if (message == null || message.isEmpty())
            message = "A warning has occured.";

        StringBuilder print = new StringBuilder();
        for (String line : StringUtils.split(message, '\n')) {
            if (isAnsiEnabled())
                print.append(Ansi.ansi().fg(Color.YELLOW));
            print.append("Warning: ").append(line);
            if (isAnsiEnabled())
                print.append(Ansi.ansi().reset());
            print.append('\n');
        }

        // remove last newline
        String printStr = print.toString();
        printStr = printStr.substring(0, printStr.length() - 1);

        logWithoutAnsi(Level.WARN, printStr);
        System.err.println(printStr);

        lastPrintBeginPhases = false;
        lastPrintPhase = false;
    }

    private void logWithoutAnsi(Level level,
                                String message) {
        String filteredMessage = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStreamWriter ansiFilter = new OutputStreamWriter(
                        new AnsiOutputStream(baos), Constants.CHARSET)) {
            ansiFilter.append(message);
            ansiFilter.flush();
            filteredMessage = baos.toString(Constants.CHARSET.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.log(level, filteredMessage);
    }
}
