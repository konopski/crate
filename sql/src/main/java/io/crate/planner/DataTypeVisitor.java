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

package io.crate.planner;

import io.crate.planner.symbol.*;
import io.crate.DataType;

public class DataTypeVisitor extends SymbolVisitor<Void, DataType> {

    @Override
    public DataType visitAggregation(Aggregation symbol, Void context) {
        if (symbol.toStep() == Aggregation.Step.PARTIAL) {
            return DataType.NULL; // TODO: change once we have aggregationState types
        }
        return symbol.functionInfo().returnType();
    }

    @Override
    public DataType visitValue(Value symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitStringLiteral(StringLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitDoubleLiteral(DoubleLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitBooleanLiteral(BooleanLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitByteLiteral(ByteLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitShortLiteral(ShortLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitIntegerLiteral(IntegerLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitInputColumn(InputColumn inputColumn, Void context) {
        return null;
    }

    @Override
    public DataType visitNullLiteral(Null symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitLongLiteral(LongLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitFloatLiteral(FloatLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitReference(Reference symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitFunction(Function symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitDynamicReference(DynamicReference symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitObjectLiteral(ObjectLiteral symbol, Void context) {
        return symbol.valueType();
    }

    @Override
    public DataType visitParameter(Parameter symbol, Void context) {
        return DataType.NULL;
    }

    @Override
    protected DataType visitSymbol(Symbol symbol, Void context) {
        throw new UnsupportedOperationException(SymbolFormatter.format("Unable to get DataType from symbol: %s", symbol));
    }
}
