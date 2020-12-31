/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.operator.scalar;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.prestosql.metadata.BoundSignature;
import io.prestosql.metadata.FunctionBinding;
import io.prestosql.metadata.FunctionId;
import io.prestosql.spi.connector.ConnectorSession;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandle;
import java.util.Optional;

import static io.prestosql.spi.function.InvocationConvention.InvocationArgumentConvention.NEVER_NULL;
import static io.prestosql.spi.function.InvocationConvention.InvocationReturnConvention.FAIL_ON_NULL;
import static io.prestosql.spi.type.BigintType.BIGINT;
import static io.prestosql.util.Reflection.methodHandle;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestParametricScalarImplementationValidation
{
    private static final MethodHandle STATE_FACTORY = methodHandle(TestParametricScalarImplementationValidation.class, "createState");

    @Test
    public void testConnectorSessionPosition()
    {
        // Without cached instance factory
        MethodHandle validFunctionMethodHandle = methodHandle(TestParametricScalarImplementationValidation.class, "validConnectorSessionParameterPosition", ConnectorSession.class, long.class, long.class);
        ChoicesScalarFunctionImplementation validFunction = new ChoicesScalarFunctionImplementation(
                new FunctionBinding(new FunctionId("test"), new BoundSignature("test", BIGINT, ImmutableList.of(BIGINT, BIGINT)), ImmutableMap.of(), ImmutableMap.of()),
                FAIL_ON_NULL,
                ImmutableList.of(NEVER_NULL, NEVER_NULL),
                validFunctionMethodHandle);
        assertEquals(validFunction.getChoices().get(0).getMethodHandle(), validFunctionMethodHandle);

        try {
            new ChoicesScalarFunctionImplementation(
                    new FunctionBinding(new FunctionId("test"), new BoundSignature("test", BIGINT, ImmutableList.of(BIGINT, BIGINT)), ImmutableMap.of(), ImmutableMap.of()),
                    FAIL_ON_NULL,
                    ImmutableList.of(NEVER_NULL, NEVER_NULL),
                    methodHandle(TestParametricScalarImplementationValidation.class, "invalidConnectorSessionParameterPosition", long.class, long.class, ConnectorSession.class));
            fail("expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "ConnectorSession must be the first argument when instanceFactory is not present");
        }

        // With cached instance factory
        MethodHandle validFunctionWithInstanceFactoryMethodHandle = methodHandle(TestParametricScalarImplementationValidation.class, "validConnectorSessionParameterPosition", Object.class, ConnectorSession.class, long.class, long.class);
        ChoicesScalarFunctionImplementation validFunctionWithInstanceFactory = new ChoicesScalarFunctionImplementation(
                new FunctionBinding(new FunctionId("test"), new BoundSignature("test", BIGINT, ImmutableList.of(BIGINT, BIGINT)), ImmutableMap.of(), ImmutableMap.of()),
                FAIL_ON_NULL,
                ImmutableList.of(NEVER_NULL, NEVER_NULL),
                validFunctionWithInstanceFactoryMethodHandle,
                Optional.of(STATE_FACTORY));
        assertEquals(validFunctionWithInstanceFactory.getChoices().get(0).getMethodHandle(), validFunctionWithInstanceFactoryMethodHandle);

        try {
            new ChoicesScalarFunctionImplementation(
                    new FunctionBinding(new FunctionId("test"), new BoundSignature("test", BIGINT, ImmutableList.of(BIGINT, BIGINT)), ImmutableMap.of(), ImmutableMap.of()),
                    FAIL_ON_NULL,
                    ImmutableList.of(NEVER_NULL, NEVER_NULL),
                    methodHandle(TestParametricScalarImplementationValidation.class, "invalidConnectorSessionParameterPosition", Object.class, long.class, long.class, ConnectorSession.class),
                    Optional.of(STATE_FACTORY));
            fail("expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "ConnectorSession must be the second argument when instanceFactory is present");
        }
    }

    public static Object createState()
    {
        return null;
    }

    public static long validConnectorSessionParameterPosition(ConnectorSession session, long arg1, long arg2)
    {
        return arg1 + arg2;
    }

    public static long validConnectorSessionParameterPosition(Object state, ConnectorSession session, long arg1, long arg2)
    {
        return arg1 + arg2;
    }

    public static long invalidConnectorSessionParameterPosition(long arg1, long arg2, ConnectorSession session)
    {
        return arg1 + arg2;
    }

    public static long invalidConnectorSessionParameterPosition(Object state, long arg1, long arg2, ConnectorSession session)
    {
        return arg1 + arg2;
    }
}
