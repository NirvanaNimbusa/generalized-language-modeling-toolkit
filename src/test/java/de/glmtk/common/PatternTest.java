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

import static de.glmtk.common.PatternElem.CNT;
import static de.glmtk.common.PatternElem.POS;
import static de.glmtk.common.PatternElem.PSKP;
import static de.glmtk.common.PatternElem.SKP;
import static de.glmtk.common.PatternElem.WSKP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;


public class PatternTest {
    @Test
    public void testEqual() {
        Pattern a = Patterns.get("101x");
        Pattern b = Patterns.get(Arrays.asList(CNT, SKP, CNT, WSKP));
        assertEquals(a, b);

        Pattern c = Patterns.get("1011");
        assertNotEquals(a, c);
        assertNotEquals(b, c);
    }

    @Test
    public void testCache() {
        Pattern a = Patterns.get("101x");
        Pattern b = Patterns.get(Arrays.asList(CNT, SKP, CNT, WSKP));
        assertTrue(a == b);

        Pattern c = Patterns.get("1011");
        assertFalse(a == c);
        assertFalse(b == c);
    }

    @Test
    public void testToString() {
        Pattern a = Patterns.get("101x");
        Pattern b = Patterns.get(Arrays.asList(CNT, SKP, CNT, WSKP));
        assertEquals(a.toString(), b.toString());
        assertEquals("101x", b.toString());
    }

    @Test
    public void testIterator() {
        Pattern a = Patterns.get("101x");
        int i = -1;
        for (PatternElem elem : a) {
            assertEquals(elem, a.get(++i));
        }
    }

    @Test
    public void testSize() {
        assertEquals(0, Patterns.get().size());
        assertEquals(1, Patterns.get("1").size());
        assertEquals(4, Patterns.get("101x").size());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(Patterns.get().isEmpty());
        assertFalse(Patterns.get(CNT).isEmpty());
    }

    @Test
    public void testGet() {
        Pattern a = Patterns.get("101x");
        assertEquals(CNT, a.get(0));
        assertEquals(WSKP, a.get(3));

        try {
            a.get(-1);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}

        try {
            a.get(5);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testContains() {
        Pattern a = Patterns.get("101x");
        assertTrue(a.contains(CNT));
        assertFalse(a.contains(POS));
    }

    @Test
    public void testContainsAny() {
        Pattern a = Patterns.get("101x");
        assertTrue(a.containsAny(Arrays.asList(CNT, SKP)));
        assertFalse(a.containsAny(Arrays.asList(POS, PSKP)));

        try {
            a.containsAny(new ArrayList<PatternElem>());
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testContainsOnly() {
        Pattern a = Patterns.get("11111");
        assertTrue(a.containsOnly(CNT));
        assertFalse(a.containsOnly(SKP));
        assertTrue(a.containsOnly(Arrays.asList(CNT, SKP)));
        assertFalse(a.containsOnly(Arrays.asList(SKP, POS)));

        try {
            assertFalse(a.containsOnly(new ArrayList<PatternElem>()));
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}

        Pattern b = Patterns.get("101x");
        assertFalse(b.containsOnly(CNT));
        assertFalse(b.containsOnly(SKP));
        assertFalse(b.containsOnly(Arrays.asList(CNT, SKP)));
        assertTrue(b.containsOnly(Arrays.asList(CNT, SKP, WSKP)));

        Pattern c = Patterns.get();
        assertFalse(c.contains(CNT));
        assertTrue(c.containsOnly(CNT));
        assertFalse(c.containsAny(Arrays.asList(CNT, SKP)));
        assertTrue(c.containsOnly(Arrays.asList(CNT, SKP)));
    }

    @Test
    public void testIsAbsolute() {
        assertTrue(Patterns.get("1").isAbsolute());
        assertTrue(Patterns.get("0").isAbsolute());
        assertTrue(Patterns.get("10110").isAbsolute());
        assertFalse(Patterns.get("x").isAbsolute());
        assertFalse(Patterns.get("1011x").isAbsolute());
    }

    @Test
    public void testNumElems() {
        Pattern a = Patterns.get("101x");
        assertEquals(2, a.numElems(Arrays.asList(CNT)));
        assertEquals(1, a.numElems(Arrays.asList(SKP)));
        assertEquals(3, a.numElems(Arrays.asList(CNT, SKP)));
        assertEquals(0, a.numElems(Arrays.asList(POS)));

        try {
            assertEquals(a.numElems(new ArrayList<PatternElem>()), 0);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testConcat() {
        Pattern a = Patterns.get("01");
        Pattern b = Patterns.get("011");
        Pattern c = Patterns.get("01011");
        Pattern d = Patterns.get("01101");

        assertEquals(a.concat(CNT), b);
        assertEquals(b.concat(SKP).concat(CNT), d);
        assertEquals(a.concat(b), c);
        assertEquals(b.concat(a), d);
    }

    @Test
    public void testRange() {
        Pattern a = Patterns.get("101x");
        Pattern b = Patterns.get();

        assertEquals(a, a.range(0, a.size()));
        assertEquals(Patterns.get("101"), a.range(0, 3));
        assertEquals(Patterns.get("1"), a.range(0, 1));
        assertEquals(b, a.range(0, 0));
        assertEquals(b, a.range(4, 4));
        assertEquals(b, b.range(0, b.size()));

        try {
            a.range(-1, a.size());
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}

        try {
            a.range(0, a.size() + 1);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void testReplace() {
        Pattern a = Patterns.get("101x");
        Pattern b = Patterns.get();

        assertEquals(Patterns.get("111x"), a.replace(SKP, CNT));
        assertEquals(Patterns.get("000x"), a.replace(CNT, SKP));
        assertEquals(a, a.replace(CNT, CNT));
        assertEquals(b, b.replace(CNT, SKP));
    }

    @Test
    public void testReplaceLast() {
        Pattern a = Patterns.get("101x");
        Pattern b = Patterns.get();

        assertEquals(Patterns.get("1011"), a.replaceLast(WSKP, CNT));
        assertEquals(Patterns.get("100x"), a.replaceLast(CNT, SKP));
        assertEquals(Patterns.get("111x"), a.replaceLast(SKP, CNT));
        assertEquals(a, a.replaceLast(CNT, CNT));
        assertEquals(b, b.replaceLast(CNT, SKP));
    }

    @Test
    public void testGetContinuationSource() {
        assertEquals(Patterns.get("1011"),
            Patterns.get("101x").getContinuationSource());
        assertEquals(Patterns.get("1110"),
            Patterns.get("1x10").getContinuationSource());
        assertEquals(Patterns.get("1x01"),
            Patterns.get("1x0x").getContinuationSource());
        assertEquals(Patterns.get("1xy02"),
            Patterns.get("1xy0y").getContinuationSource());

        try {
            Patterns.get("1011").getContinuationSource();
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}

        try {
            Patterns.get().getContinuationSource();
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {}
    }
}
