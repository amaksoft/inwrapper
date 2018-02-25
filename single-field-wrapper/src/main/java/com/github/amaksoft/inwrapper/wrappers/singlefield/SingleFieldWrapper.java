package com.github.amaksoft.inwrapper.wrappers.singlefield;

import com.github.amaksoft.inwrapper.InWrapperConverterFactory;

import java.lang.annotation.Annotation;

/**
 * An immutable generic body object for requests and responses with single field of specified name wrapping actual data
 * Created by amak on 2018-02-13.
 *
 * @see SingleFieldWrapperTypeAdapterFactory for serialization/deserialization mechanism
 */

@SuppressWarnings("WeakerAccess")
public class SingleFieldWrapper<T> {
    private final String dataFieldName;
    private final T data;

    /**
     * Creates an instance with desired field name
     *
     * @param dataFieldName JSON field name
     * @param data          data object
     */
    public SingleFieldWrapper(String dataFieldName, T data) {
        this.dataFieldName = dataFieldName;
        this.data = data;
    }

    /**
     * Getter method for field name
     *
     * @return JSON field name
     */
    public String getDataFieldName() {
        return dataFieldName;
    }

    /**
     * Getter method for data
     *
     * @return data object
     */
    public T getData() {
        return data;
    }

    /**
     * An unwrapper for this class. Register using {@link InWrapperConverterFactory.Builder#registerUnwrapper(Class, InWrapperConverterFactory.ResponseUnwrapper)}
     *
     * @param <T> wrapped data type
     */
    @SuppressWarnings("unused")
    public static class Unwrapper<T> implements InWrapperConverterFactory.ResponseUnwrapper<SingleFieldWrapper<T>, T> {
        @Override
        public T unwrap(SingleFieldWrapper<T> wrapper, int depth, Annotation[] annotations) {
            return wrapper.getData();
        }
    }

    /**
     * An packer for this class. Register using {@link InWrapperConverterFactory.Builder#registerPacker(Class, InWrapperConverterFactory.RequestPacker)}
     *
     * @param <T> wrapped data type
     */
    public static class Packer<T> implements InWrapperConverterFactory.RequestPacker<SingleFieldWrapper<T>, T> {
        @Override
        public SingleFieldWrapper<T> pack(T data, int depth, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
            SingleFieldWrapper<T> body = null;
            for (Annotation annotation : parameterAnnotations) {
                if (annotation instanceof FieldName) {
                    FieldName fieldNameAnno = (FieldName) annotation;
                    body = new SingleFieldWrapper<>(fieldNameAnno.value()[depth], data);
                }
            }
            return body != null ? body : new SingleFieldWrapper<>(null, data);
        }
    }

    /**
     * Annotation for passing field names to the {@link Packer}
     */
    public @interface FieldName {
        String[] value();
    }
}
