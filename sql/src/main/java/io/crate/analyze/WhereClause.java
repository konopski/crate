/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import io.crate.planner.symbol.*;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;

import java.io.IOException;

public class WhereClause implements Streamable {

    public static final WhereClause MATCH_ALL = new WhereClause();
    public static final WhereClause NO_MATCH = new WhereClause(null, true);

    private Function query;
    private boolean noMatch = false;

    protected String clusteredBy;
    protected Long version;

    private WhereClause() {
    }

    public WhereClause(StreamInput in) throws IOException {
        readFrom(in);
    }

    public WhereClause(Function query) {
        this.query = query;
    }

    public WhereClause(Function query, boolean noMatch) {
        this(query);
        this.noMatch = noMatch;
    }

    public WhereClause(Symbol normalizedQuery) {
        if (normalizedQuery.symbolType() == SymbolType.FUNCTION) {
            this.query = (Function) normalizedQuery;
            return;
        }
        if (normalizedQuery.symbolType() == SymbolType.BOOLEAN_LITERAL) {
            if (!((BooleanLiteral) normalizedQuery).value()) {
                this.noMatch = true;
            }
            return;
        }
        if (normalizedQuery.symbolType() == SymbolType.NULL_LITERAL) {
            this.noMatch = true;
            return;
        }
        throw new UnsupportedOperationException("Symbol not supported for where clause: " + normalizedQuery);
    }

    public WhereClause normalize(EvaluatingNormalizer normalizer) {
        if (noMatch || query == null) {
            return this;
        }
        Symbol normalizedQuery = normalizer.normalize(query);
        if (normalizedQuery == query) {
            return this;
        }
        return new WhereClause(normalizedQuery);
    }


    public Optional<String> clusteredBy() {
        return Optional.fromNullable(clusteredBy);
    }

    public void clusteredByLiteral(@Nullable Literal clusteredByLiteral) {
        if (clusteredByLiteral != null) {
            clusteredBy = clusteredByLiteral.valueAsString();
        }
    }

    public void version(@Nullable Long version) {
        this.version = version;
    }

    public Optional<Long> version() {
        return Optional.fromNullable(this.version);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            query = new Function();
            query.readFrom(in);
        } else {
            noMatch = in.readBoolean();
        }

        if (in.readBoolean()) {
            clusteredBy = in.readBytesRef().utf8ToString();
        }

        if (in.readBoolean()) {
            version = in.readVLong();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (query != null) {
            out.writeBoolean(true);
            query.writeTo(out);
        } else {
            out.writeBoolean(false);
            out.writeBoolean(noMatch);
        }

        if (clusteredBy != null) {
            out.writeBoolean(true);
            out.writeBytesRef(new BytesRef(clusteredBy));
        } else {
            out.writeBoolean(false);
        }

        if (version != null) {
            out.writeBoolean(true);
            out.writeVLong(version);
        } else {
            out.writeBoolean(false);
        }
    }

    public boolean hasQuery() {
        return query != null;
    }

    @Nullable
    public Function query() {
        return query;
    }

    public boolean noMatch() {
        return noMatch;
    }

    @Override
    public String toString() {
        Objects.ToStringHelper helper = Objects.toStringHelper(this);
        if (noMatch()) {
            helper.add("NO_MATCH", true);
        } else if (!hasQuery()) {
            helper.add("MATCH_ALL", true);
        } else {
            helper.add("query", query);
        }
        return helper.toString();
    }
}
