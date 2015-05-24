package de.glmtk.options;

import static de.glmtk.util.revamp.ListUtils.list;
import static java.util.Objects.requireNonNull;

import java.util.List;

import de.glmtk.querying.estimator.Estimator;

public class EstimatorsOption extends Option {
    public static final String DEFAULT_ARGNAME = EstimatorOption.DEFAULT_ARGNAME;

    private String argname;
    private List<Estimator> defaultValue = list();

    public EstimatorsOption(String shortopt,
                            String longopt,
                            String desc) {
        this(shortopt, longopt, desc, DEFAULT_ARGNAME);
    }

    public EstimatorsOption(String shortopt,
                            String longopt,
                            String desc,
                            String argname) {
        super(shortopt, longopt, desc);

        requireNonNull(argname);

        this.argname = argname;
    }

    public EstimatorsOption defaultValue(List<Estimator> defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public List<Estimator> getEstimators() {
        throw new UnsupportedOperationException();
    }
}
