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

package de.glmtk.exceptions;

import static java.lang.String.format;

public class CliArgumentException extends Termination {

    private static final long serialVersionUID = -8644343219016883237L;

    public CliArgumentException() {
        super();
    }

    public CliArgumentException(String message) {
        super(message + "\nTry --help for more information.");
    }

    public CliArgumentException(String format,
                                Object... args) {
        this(format(format, args));
    }

    public CliArgumentException(Throwable cause) {
        super(cause);
    }

    public CliArgumentException(String message,
                                Throwable cause) {
        super(message, cause);
    }

}
