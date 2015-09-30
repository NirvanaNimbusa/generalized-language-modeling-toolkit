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

package de.glmtk.files;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

import de.glmtk.util.StringUtils;


public class LengthDistributionReader extends AbstractFileReader {
    private int length;
    private double frequency;

    public LengthDistributionReader(Path file,
                                    Charset charset) throws IOException {
        this(file, charset, 8192);
    }

    public LengthDistributionReader(Path file,
                                    Charset charset,
                                    int sz) throws IOException {
        super(file, charset, sz);
        length = 0;
        frequency = Double.NaN;
    }

    @Override
    protected void parseLine() {
        if (line == null) {
            length = 0;
            frequency = Double.NaN;
            return;
        }

        List<String> split = StringUtils.split(line, '\t');

        if (split.size() != 2) {
            throw newFileFormatException("length distribution",
                "Expected line to have format '<length>\\t<frequency>'.");
        }

        try {
            length = (int) parseNumber(split.get(0));
            frequency = parseFloatingPoint(split.get(1));
            length = Integer.parseInt(split.get(0));
        } catch (IllegalArgumentException e) {
            throw newFileFormatException("length distribution", e.getMessage());
        }
    }

    public int getLength() {
        return length;
    }

    public double getFrequency() {
        return frequency;
    }
}
