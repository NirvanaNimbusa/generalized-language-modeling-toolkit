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

package de.glmtk.util;

public class ObjectUtils {
    private ObjectUtils() {
    }

    /**
     * Same as {@link Object#equals(Object)} but allowing (@code null} for both
     * parameters.
     */
    public static boolean equals(Object lhs,
                                 Object rhs) {
        if (lhs == null) {
            if (rhs == null)
                return true;
            return false;
        }
        if (rhs == null)
            return false;
        return lhs.equals(rhs);
    }

    /**
     * Same as {@link Comparable#compareTo(Object)} but allowing {@code null}
     * for both parameters.
     */
    public static <T extends Comparable<T>> int compare(T lhs,
                                                        T rhs) {
        if (lhs == null) {
            if (rhs == null)
                return 0;
            return 1;
        }
        if (rhs == null)
            return -1;
        return lhs.compareTo(rhs);
    }
}
