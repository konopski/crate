package io.crate.metadata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Functions {

    private static final Map<FunctionIdent, FunctionImplementation> functionImplemnetations = new ConcurrentHashMap<>();

    public static void registerImplementation(FunctionImplementation impl) {
        functionImplemnetations.put(impl.info().ident(), impl);
    }

    public static FunctionImplementation get(FunctionIdent ident) {
        return functionImplemnetations.get(ident);
    }

}