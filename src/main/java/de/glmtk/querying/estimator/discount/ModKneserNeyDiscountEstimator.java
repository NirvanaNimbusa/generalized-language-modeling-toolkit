/*
 * Generalized Language Modeling Toolkit (GLMTK)
 * 
 * Copyright (C) 2014-2015 Lukas Schmelzeisen, Rene Pickhardt
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

package de.glmtk.querying.estimator.discount;

import java.util.HashMap;
import java.util.Map;

import de.glmtk.cache.Cache;
import de.glmtk.common.Pattern;
import de.glmtk.counts.Discounts;
import de.glmtk.counts.NGramTimes;
import de.glmtk.querying.estimator.fraction.FractionEstimator;

public class ModKneserNeyDiscountEstimator extends ThreeDiscountEstimator {
    private Map<Pattern, Discounts> discounts = null;

    public ModKneserNeyDiscountEstimator(FractionEstimator fractionEstimator) {
        super(fractionEstimator);
    }

    @Override
    public void setCache(Cache cache) {
        super.setCache(cache);

        discounts = new HashMap<>();
    }

    @Override
    public Discounts getDiscounts(Pattern pattern) {
        Discounts result = discounts.get(pattern);
        if (result != null)
            return result;

        NGramTimes n = cache.getNGramTimes(pattern);
        double y = (double) n.getOneCount()
                / (n.getOneCount() + n.getTwoCount());
        result = new Discounts(1.0f - 2.0f * y * n.getTwoCount()
                / n.getOneCount(), 2.0f - 3.0f * y * n.getThreeCount()
                / n.getTwoCount(), 3.0f - 4.0f * y * n.getFourCount()
                / n.getThreeCount());
        discounts.put(pattern, result);
        return result;
    }
}
