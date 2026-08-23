package eu.pb4.polyfactory.util;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import eu.pb4.placeholders.api.arguments.StringArgs;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StringOps implements DynamicOps<String> {
    public static final StringOps INSTANCE = new StringOps();

    @Override
    public String empty() {
        return "";
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, String input) {
        return outOps.empty();
    }

    @Override
    public DataResult<Number> getNumberValue(String input) {
        try {
            return DataResult.success(Double.valueOf(input));
        } catch (Throwable _) {
            try {
                return DataResult.success(Boolean.parseBoolean(input) ? 1 : 0);
            } catch (Throwable _) {
            }
        }

        return DataResult.error(() -> input + " is not a number!");
    }

    @Override
    public String createNumeric(Number i) {
        return String.valueOf(i);
    }

    @Override
    public DataResult<Boolean> getBooleanValue(String input) {
        try {
            return DataResult.success(Boolean.parseBoolean(input));
        } catch (Throwable _) {
        }
        

        return DataResult.error(() -> input + " is not a boolean!");
    }

    @Override
    public String createBoolean(boolean b) {
        return String.valueOf(b);
    }

    @Override
    public DataResult<String> getStringValue(String input) {
        return DataResult.success(input);
    }

    @Override
    public String createString(String value) {
        return value;
    }

    @Override
    public DataResult<String> mergeToList(String list, String value) {
        return DataResult.success(list + ", " + value);
    }

    @Override
    public DataResult<String> mergeToMap(String map, String key, String value) {
        return DataResult.success(map + ", " + key + "=" + value);
    }

    @Override
    public DataResult<Stream<Pair<String, String>>> getMapValues(String input) {
        return DataResult.success(Arrays.stream(input.split(",")).map(String::strip).map(s -> {
            var split = s.split("=", 2);
            if (split.length != 2) {
                return new Pair<>(split[0], "");
            }

            return new Pair<>(split[0], split[1]);
        }));
    }

    @Override
    public String createMap(Stream<Pair<String, String>> map) {
        return map.map(pair -> pair.getFirst() + "=" + pair.getSecond()).collect(Collectors.joining(", "));
    }

    @Override
    public DataResult<Stream<String>> getStream(String input) {
        return DataResult.success(Arrays.stream(input.split(",")).map(String::strip));
    }

    @Override
    public String createList(Stream<String> input) {
        return input.collect(Collectors.joining(", "));
    }

    @Override
    public String remove(String input, String key) {
        return createMap(getMapValues(input).result().orElseThrow().filter(x -> !x.getFirst().equals(key)));
    }
}
