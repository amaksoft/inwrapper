package com.github.amaksoft.inwrapper.wrappers.singlefield;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.$Gson$Types;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Properties;

import static com.google.gson.internal.$Gson$Preconditions.checkArgument;

/**
 * {@link TypeAdapterFactory} for generic {@link SingleFieldWrapper} class.<br/>
 * <p>
 * During deserialization extracts single field of JSON object and sets {@link SingleFieldWrapper#data}
 * field to its value, {@link SingleFieldWrapper#dataFieldName} to its name. <br/>
 * <p>
 * During serialization creates a JSON object with a single field, filled with {@link SingleFieldWrapper#data}
 * and named with {@link SingleFieldWrapper#dataFieldName} or {@link #defaultFieldName}
 * which falls back to {@link #DEFAULT_FIELD_NAME} if not specified
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class SingleFieldWrapperTypeAdapterFactory implements TypeAdapterFactory {
    private static final String DEFAULT_FIELD_NAME = "data";

    private final String defaultFieldName;

    public SingleFieldWrapperTypeAdapterFactory(String defaultFieldName) {
        if (defaultFieldName == null || defaultFieldName.isEmpty()) {
            this.defaultFieldName = DEFAULT_FIELD_NAME;
        } else {
            this.defaultFieldName = defaultFieldName;
        }
    }

    public SingleFieldWrapperTypeAdapterFactory() {
        this(null);
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Type type = typeToken.getType();

        Class<? super T> rawType = typeToken.getRawType();
        if (!SingleFieldWrapper.class.isAssignableFrom(rawType)) {
            return null;
        }

        Class<?> rawTypeOfSrc = $Gson$Types.getRawType(type);
        Type valueType = getValueType(type, rawTypeOfSrc);
        TypeAdapter<?> valueAdapter = gson.getAdapter(TypeToken.get(valueType));

        //noinspection unchecked
        return new SingleFieldBodyTypeAdapter(valueAdapter, defaultFieldName).nullSafe();
    }

    private static class SingleFieldBodyTypeAdapter<V> extends TypeAdapter<SingleFieldWrapper<V>> {
        private final TypeAdapter<V> valueTypeAdapter;
        private String defaultFieldName;

        SingleFieldBodyTypeAdapter(TypeAdapter<V> valueTypeAdapter, String defaultFieldName) {
            this.valueTypeAdapter = valueTypeAdapter;
            this.defaultFieldName = defaultFieldName;
        }

        @Override
        public void write(JsonWriter out, SingleFieldWrapper<V> value) throws IOException {
            out.beginObject();

            String fieldName = value.getDataFieldName();
            if (fieldName == null) fieldName = defaultFieldName;
            out.name(fieldName);

            valueTypeAdapter.write(out, value.getData());

            out.endObject();
        }

        @Override
        public SingleFieldWrapper<V> read(JsonReader in) throws IOException {
            in.beginObject();

            SingleFieldWrapper<V> body = new SingleFieldWrapper<>(in.nextName(), valueTypeAdapter.read(in));

            in.endObject(); // if not the end of JSON object, it's not a single field JSON and something went wrong

            return body;
        }
    }

    /**
     * @see package private $Gson$Types#getMapKeyAndValueTypes(Type, Class)
     */
    private static Type getValueType(Type context, Class<?> contextRawType) {
    /*
     * Work around a problem with the declaration of java.util.Properties. That
     * class should extend Hashtable<String, String>, but it's declared to
     * extend Hashtable<Object, Object>.
     */
        if (context == Properties.class) {
            return String.class; // TODO: test subclasses of Properties!
        }

        Type mapType = getSupertype(context, contextRawType, SingleFieldWrapper.class);
        // TODO: strip wildcards?
        if (mapType instanceof ParameterizedType) {
            ParameterizedType mapParameterizedType = (ParameterizedType) mapType;
            return mapParameterizedType.getActualTypeArguments()[0];
        }
        return Object.class;
    }

    /**
     * @see package private $Gson$Types#getSupertype(Type, Class, Class)
     */
    private static Type getSupertype(Type context, Class<?> contextRawType, Class<?> supertype) {
        checkArgument(supertype.isAssignableFrom(contextRawType));
        return $Gson$Types.resolve(context, contextRawType,
                getGenericSupertype(context, contextRawType, supertype));
    }

    /**
     * @see package private $Gson$Types#getGenericSupertype(Type, Class, Class)
     */
    private static Type getGenericSupertype(Type context, Class<?> rawType, Class<?> toResolve) {
        if (toResolve == rawType) {
            return context;
        }

        // we skip searching through interfaces if unknown is an interface
        if (toResolve.isInterface()) {
            Class<?>[] interfaces = rawType.getInterfaces();
            for (int i = 0, length = interfaces.length; i < length; i++) {
                if (interfaces[i] == toResolve) {
                    return rawType.getGenericInterfaces()[i];
                } else if (toResolve.isAssignableFrom(interfaces[i])) {
                    return getGenericSupertype(rawType.getGenericInterfaces()[i], interfaces[i], toResolve);
                }
            }
        }

        // check our supertypes
        if (!rawType.isInterface()) {
            while (rawType != Object.class) {
                Class<?> rawSupertype = rawType.getSuperclass();
                if (rawSupertype == toResolve) {
                    return rawType.getGenericSuperclass();
                } else if (toResolve.isAssignableFrom(rawSupertype)) {
                    return getGenericSupertype(rawType.getGenericSuperclass(), rawSupertype, toResolve);
                }
                rawType = rawSupertype;
            }
        }

        // we can't resolve this further
        return toResolve;
    }

}