package io.crate.metadata;


// PRESTOBORROW

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.ComparisonChain;
import org.cratedb.DataType;

public class FunctionInfo
        implements Comparable<FunctionInfo> {

    private final FunctionIdent ident;
    private final DataType returnType;
    private final boolean isAggregate;
    private final boolean deterministic;

    public FunctionInfo(FunctionIdent ident, DataType returnType, boolean isAggregate) {
        this.ident = ident;
        this.returnType = returnType;
        this.isAggregate = isAggregate;
        this.deterministic = true;
    }

    public FunctionIdent ident() {
        return ident;
    }

    public boolean isAggregate() {

        return isAggregate;
    }


    public DataType returnType() {
        return returnType;
    }

    public boolean isDeterministic() {
        return deterministic;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        FunctionInfo o = (FunctionInfo) obj;
        return Objects.equal(isAggregate, o.isAggregate) &&
                Objects.equal(ident, o.ident) &&
                Objects.equal(returnType, o.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isAggregate, ident, returnType);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("isAggregate", isAggregate)
                .add("ident", ident)
                .add("returnType", returnType)
                .toString();
    }

    @Override
    public int compareTo(FunctionInfo o) {
        return ComparisonChain.start()
                .compareTrueFirst(isAggregate, o.isAggregate)
                .compare(ident, o.ident)
                .compare(returnType, o.returnType)
                .result();
    }

    public static Predicate<FunctionInfo> isAggregationPredicate() {
        return new Predicate<FunctionInfo>() {
            @Override
            public boolean apply(FunctionInfo functionInfo) {
                return functionInfo.isAggregate();
            }
        };
    }
}