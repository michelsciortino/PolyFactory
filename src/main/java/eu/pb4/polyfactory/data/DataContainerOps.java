package eu.pb4.polyfactory.data;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DataContainerOps implements DynamicOps<DataContainer> {
    public static final DataContainerOps REGULAR = new Regular();
    public static final DataContainerOps PARSING = new Parsing();

    @Override
    public DataContainer empty() {
        return DataContainer.empty();
    }

    @Override
    public <U> U convertTo(DynamicOps<U> outOps, DataContainer input) {
        if (input instanceof MapData) {
            return convertMap(outOps, input);
        }
        if (input instanceof ListData) {
            return convertList(outOps, input);
        }
        if (input instanceof EmptyData) {
            return outOps.empty();
        }

        if (input instanceof StringData(var value)) {
            return outOps.createString(value);
        }

        if (input instanceof BoolData(var value)) {
            return outOps.createBoolean(value);
        }

        if (input instanceof DoubleData(var value)) {
            return outOps.createDouble(value);
        }

        if (input instanceof LongData(var value)) {
            return outOps.createLong(value);
        }

        if (input instanceof RedstoneData(var value)) {
            return outOps.createInt(value);
        }

        return DataContainer.CODEC.encodeStart(outOps, input).result().orElse(outOps.empty());
    }

    @Override
    public DataContainer createNumeric(Number i) {
        return new DoubleData(i.doubleValue());
    }

    @Override
    public DataContainer createBoolean(boolean value) {
        return BoolData.of(value);
    }

    @Override
    public DataResult<String> getStringValue(DataContainer input) {
        return DataResult.success(input.asString());
    }

    @Override
    public DataContainer createString(String value) {
        return new StringData(value);
    }

    @Override
    public DataResult<DataContainer> mergeToList(DataContainer list, DataContainer value) {
        if (list instanceof ListData(List<DataContainer> data)) {
            var l = new ArrayList<>(data);
            l.add(value);
            return DataResult.success(new ListData(l));
        }

        return DataResult.error(() -> list + " is not a list!");
    }

    @Override
    public DataResult<DataContainer> mergeToMap(DataContainer map, DataContainer key, DataContainer value) {
        if (map instanceof MapData(Map<String, DataContainer> data)) {
            var l = new HashMap<>(data);
            l.put(key.asString(), value);
            return DataResult.success(new MapData(l));
        }

        return DataResult.error(() -> map + " is not a list!");
    }

    @Override
    public DataResult<Stream<Pair<DataContainer, DataContainer>>> getMapValues(DataContainer input) {
        if (input instanceof MapData(Map<String, DataContainer> map)) {
            return DataResult.success(map.entrySet().stream().map(x -> new Pair<>(new StringData(x.getKey()), x.getValue())));
        }

        return DataResult.error(() -> input + " is not a map!");
    }

    @Override
    public DataContainer createMap(Stream<Pair<DataContainer, DataContainer>> map) {
        return new MapData(map.collect(Collectors.<Pair<DataContainer, DataContainer>, String, DataContainer>toMap(p -> p.getFirst().asString(), Pair::getSecond)));
    }

    @Override
    public DataResult<Stream<DataContainer>> getStream(DataContainer input) {
        if (input instanceof ListData(List<DataContainer> list)) {
            return DataResult.success(list.stream());
        }

        return DataResult.error(() -> input + " is not a list!");
    }

    @Override
    public DataContainer createList(Stream<DataContainer> input) {
        return new ListData(input.toList());
    }

    @Override
    public DataContainer remove(DataContainer input, String key) {
        return null;
    }

    private static class Regular extends DataContainerOps {
        @Override
        public DataResult<Number> getNumberValue(DataContainer input) {
            return DataResult.success(input.asDouble());
        }

        @Override
        public DataResult<Boolean> getBooleanValue(DataContainer input) {
            return DataResult.success(input.isTrue());
        }
    }

    private static class Parsing extends DataContainerOps {
        @Override
        public DataResult<Number> getNumberValue(DataContainer input) {
            if (input instanceof StringData(var value)) {
                try {
                    return DataResult.success(Double.parseDouble(value));
                } catch (Throwable e) {
                    return DataResult.error(() -> value + " is not a valid number");
                }
            }

            return DataResult.success(input.asDouble());
        }

        @Override
        public DataResult<Boolean> getBooleanValue(DataContainer input) {
            if (input instanceof StringData(var value)) {
                try {
                    return DataResult.success(Boolean.parseBoolean(value));
                } catch (Throwable e) {
                    // Ignore 1
                }

                try {
                    return DataResult.success(Double.parseDouble(value) != 0);
                } catch (Throwable e) {
                    // Ignore 2
                }

                return DataResult.error(() -> value + " is not a valid boolean");
            }

            return DataResult.success(input.isTrue());
        }
    }
}
