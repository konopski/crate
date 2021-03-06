/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.planner.symbol;

import org.apache.lucene.util.BytesRef;
import io.crate.DataType;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;


public class NumberLiteralTest {

    @Test
    public void testConvertTo() {
        Literal numberLiteral = new LongLiteral(123L);

        assertEquals(123L, numberLiteral.convertTo(DataType.LONG).value());
        assertEquals(123, numberLiteral.convertTo(DataType.INTEGER).value());
        assertEquals(123.0d, numberLiteral.convertTo(DataType.DOUBLE).value());
        assertEquals(123.0f, numberLiteral.convertTo(DataType.FLOAT).value());
        assertEquals(123, numberLiteral.convertTo(DataType.SHORT).value());
        assertEquals(123, numberLiteral.convertTo(DataType.BYTE).value());
        assertEquals(123L, numberLiteral.convertTo(DataType.TIMESTAMP).value());
        assertEquals(new BytesRef("123"), numberLiteral.convertTo(DataType.STRING).value());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void convertToBooleanUnsupported() {
        Literal numberLiteral = new LongLiteral(123L);
        numberLiteral.convertTo(DataType.BOOLEAN).value();
    }
}
