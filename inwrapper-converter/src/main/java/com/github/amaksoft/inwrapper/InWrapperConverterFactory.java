package com.github.amaksoft.inwrapper;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Unwraps response class and returns the desired field
 * Used to save space and time on writing boilerplate code for all kinds of wrappers and extracting actual response from them.<br/>
 * <br/>
 * If response comes in some wrapper and the wrapper is to be thrown away:
 * <ul>
 * <li>set callback return type for the {@link Retrofit} interface method to desired field type<li/>
 * <li>annotate the {@link Retrofit} interface method with {@link InWrapper} with wrapper class argument<li/>
 * <li>implement and add a {@link ResponseUnwrapper} for the wrapper class in constructor argument<li/>
 * <ul/>
 * <p>
 * If request is to be sent in some wrapper:
 * <ul>
 * <li>set body type for the {@link Retrofit} interface method to actual data type<li/>
 * <li>annotate the corresponding {@link Retrofit} interface method parameter with {@link InWrapper} with wrapper class argument<li/>
 * <li>implement and add a {@link RequestPacker} for the wrapper class in constructor argument<li/>
 * <ul/>
 */
@SuppressWarnings("WeakerAccess") // leave methods available for tests, we only have two classes in the package anyway
public class InWrapperConverterFactory extends Converter.Factory {

    private static final TypeResolver DEFAULT_TYPE_RESOLVER = new DefaultTypeResolver();

    private final Map<Class, ResponseUnwrapper> responseUnwrappers;
    private final Map<Class, RequestPacker> requestPackers;
    private final Map<Class, TypeResolver> typeResolvers;

    /**
     * Package private, not supposed to be used from outside. Please use {@link Builder}
     */
    InWrapperConverterFactory(Map<Class, ResponseUnwrapper> responseUnwrappers, Map<Class, RequestPacker> requestPackers, Map<Class, TypeResolver> typeResolvers) {
        this.responseUnwrappers = responseUnwrappers;
        this.requestPackers = requestPackers;
        this.typeResolvers = typeResolvers;
    }

    /**
     * Implementation of {@link Converter.Factory#requestBodyConverter(Type, Annotation[], Annotation[], Retrofit)} method
     */
    @Nullable
    @Override
    public Converter<?, RequestBody> requestBodyConverter(final Type type, final Annotation[] parameterAnnotations, final Annotation[] methodAnnotations, Retrofit retrofit) {

        final InWrapper wrapperAnno = findWrapperAnnotation(parameterAnnotations);

        if (wrapperAnno != null) {
            Type wrappedType = getWrappedType(type, wrapperAnno.value(), parameterAnnotations, methodAnnotations);
            final Converter<Object, RequestBody> wrappedDelegate = retrofit.nextRequestBodyConverter(this, wrappedType, parameterAnnotations, methodAnnotations);
            return new Converter<Object, RequestBody>() {
                @Override
                public RequestBody convert(@Nonnull Object value) throws IOException {
                    return wrappedDelegate.convert(chainPack(value, wrapperAnno.value(), parameterAnnotations, methodAnnotations));
                }
            };
        }

        // If method is not annotated with InWrapper, just ignore this converter
        return null;
    }

    /**
     * Implementation of {@link Converter.Factory#responseBodyConverter(Type, Annotation[], Retrofit)} method
     */
    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(final Type type, final Annotation[] annotations, Retrofit retrofit) {

        final InWrapper wrapperAnno = findWrapperAnnotation(annotations);
        if (wrapperAnno != null) {
            Type wrappedType = getWrappedType(type, wrapperAnno.value(), null, annotations);
            final Converter<ResponseBody, ?> wrappedDelegate = retrofit.nextResponseBodyConverter(this, wrappedType, annotations);
            return new Converter<ResponseBody, Object>() {
                @Override
                public Object convert(@Nonnull ResponseBody body) throws IOException {
                    return chainUnwrap(wrappedDelegate.convert(body), wrapperAnno.value(), annotations);
                }
            };
        }

        // If method is not annotated with InWrapper, just ignore this converter
        return null;
    }

    /**
     * Сonvenience method for finding the {@link InWrapper} annotation in {@link Annotation} array
     *
     * @param annotations array of annotations
     * @return found {@link InWrapper} annotation or {@code null} if none found
     */
    @Nullable
    InWrapper findWrapperAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof InWrapper) {
                return (InWrapper) annotation;
            }
        }
        return null;
    }

    /**
     * Creates {@link Type} object representing the type of fully wrapped data
     *
     * @param dataType       actual data type
     * @param wrapperClasses wrapper classes chain
     * @return wrapped data type
     */
    Type getWrappedType(Type dataType, Class[] wrapperClasses, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        Type resultType = dataType;
        for (int i = wrapperClasses.length - 1; i >= 0; i--) {
            Class wrapperClass = wrapperClasses[i];
            TypeResolver resolver = typeResolvers.get(wrapperClass);
            if (resolver == null) resolver = DEFAULT_TYPE_RESOLVER;
            resultType = resolver.resolveType(dataType, wrapperClass, i, parameterAnnotations, methodAnnotations);
        }
        return resultType;
    }

    /**
     * Packs data in wrappers as described by {@code wrapperClasses} chain
     *
     * @param data                 actual data
     * @param wrapperClasses       wrapper classes chain
     * @param parameterAnnotations interface method parameter annotations
     * @param methodAnnotations    interface method annotations
     * @return packed data
     */
    Object chainPack(Object data, Class[] wrapperClasses, @Nullable Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        Object packedData = data;
        for (int i = wrapperClasses.length - 1; i >= 0; i--) {
            Class wrapperClass = wrapperClasses[i];
            RequestPacker packer = requestPackers.get(wrapperClass);
            if (packer == null)
                throw new RuntimeException("wrapper of type " + wrapperClass.getName()
                        + " is not supported, please add a " + RequestPacker.class.getSimpleName() + " for it");
            //noinspection unchecked (packer should be of required type)
            packedData = packer.pack(packedData, i, parameterAnnotations, methodAnnotations);
        }
        return packedData;
    }

    /**
     * Unwraps data packed in chain of wrappers
     *
     * @param wrappedData    wrapper containing actual data
     * @param wrapperClasses wrapper classes chain
     * @param annotations    interface method annotations
     * @return unwrapped data
     */
    Object chainUnwrap(Object wrappedData, Class[] wrapperClasses, Annotation[] annotations) {
        Object unwrappedData = wrappedData;
        for (int i = 0; i < wrapperClasses.length; i++) {
            Class wrapperClass = wrapperClasses[i];
            ResponseUnwrapper unwrapper = responseUnwrappers.get(wrapperClass);
            if (unwrapper == null)
                throw new RuntimeException("wrapper of type " + wrapperClass.getName()
                        + " is not supported, please add a " + ResponseUnwrapper.class.getSimpleName() + " for it");
            //noinspection unchecked (unwrapper should be of required type)
            unwrappedData = unwrapper.unwrap(unwrappedData, i, annotations);
        }
        return unwrappedData;
    }

    /**
     * A very simple {@link ParameterizedType} implementation
     */
    static final class ParameterizedTypeImpl implements ParameterizedType {
        private final Type ownerType;
        private final Type rawType;
        private final Type[] typeArguments;

        ParameterizedTypeImpl(Type ownerType, Type rawType, Type... typeArguments) {
            this.ownerType = ownerType;
            this.rawType = rawType;
            this.typeArguments = typeArguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }
    }

    /**
     * Extractor interface for response body objects.
     *
     * @param <W> response body wrapper
     * @param <T> wrapped data type
     */
    public interface ResponseUnwrapper<W, T> {
        /**
         * Extracts desired field from response wrapper
         *
         * @param wrapper     parsed wrapper instance
         * @param depth       current wrapper chain depth
         * @param annotations interface method annotations to parametrize the process (if needed)
         * @return actual data value
         */
        T unwrap(W wrapper, int depth, Annotation[] annotations);
    }

    /**
     * Packer interface for request body objects.
     *
     * @param <W> request body wrapper
     * @param <T> wrapped data type
     */
    public interface RequestPacker<W, T> {
        /**
         * Packs request in wrapper
         *
         * @param data                 actual data
         * @param depth                current wrapper chain depth
         * @param parameterAnnotations parameter annotations to parametrize the process (if needed)
         * @param methodAnnotations    interface method annotations to parametrize the process (if needed)
         * @return wrapped request body instance
         */
        W pack(T data, int depth, Annotation[] parameterAnnotations, Annotation[] methodAnnotations);
    }

    /**
     * Abstract class for customizing wrapper type resolution if it is a generic with more than one type parameter
     */
    public static abstract class TypeResolver {
        /**
         * Resolves type for given wrapper class and data type
         *
         * @param typeToWrap           type to be wrapped in given wrapper
         * @param wrapperClass         wrapper class
         * @param depth                current wrapper chain depth
         * @param parameterAnnotations parameter annotations to parametrize the process (if needed). Will be {@code null} for responses
         * @param methodAnnotations    interface method annotations to parametrize the process (if needed)
         * @return resolved wrapped type
         */
        abstract Type resolveType(Type typeToWrap, Class wrapperClass, int depth, Annotation[] parameterAnnotations, Annotation[] methodAnnotations);

        /**
         * Convenience method for creating a {@link ParameterizedType}
         *
         * @param ownerType     Qwner class type. For inner classes only, pass null if you don't know how to use it.
         * @param rawType       Raw wrapper type. In most cases it is a wrapper class.
         * @param typeArguments Generic parameters for raw type (in the same order they were declared in class). Parameters could also be a parametrized type.
         * @return resolved wrapped type
         */
        protected ParameterizedType createParametrizedType(Type ownerType, Type rawType, Type[] typeArguments) {
            return new ParameterizedTypeImpl(ownerType, rawType, typeArguments);
        }

        /**
         * Convenience method for creating a {@link ParameterizedType}
         *
         * @param rawType       Raw wrapper type. In most cases it is a wrapper class.
         * @param typeArguments Generic parameters for raw type (in the same order they were declared in class). Parameters could also be a parametrized type.
         * @return resolved wrapped type
         */
        protected ParameterizedType createParametrizedType(Type rawType, Type[] typeArguments) {
            return createParametrizedType(null, rawType, typeArguments);
        }
    }

    /**
     * Internal implementation of default type resolution strategy
     */
    private static final class DefaultTypeResolver extends TypeResolver {
        @Override
        Type resolveType(Type typeToWrap, Class wrapperClass, int depth, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
            Type[] typeParams = wrapperClass.getTypeParameters();
            if (typeParams.length == 0) {
                return wrapperClass;
            } else if (typeParams.length == 1) {
                return createParametrizedType(null, wrapperClass, new Type[]{typeToWrap});
            } else {
                throw new IllegalArgumentException("Unable to process " + wrapperClass.getSimpleName() + ". " + this.getClass().getSimpleName()
                        + " does not support wrapper classes with more than one type parameter. Please create a custom "
                        + TypeResolver.class.getName() + " implementation for " + wrapperClass.getName());
            }
        }
    }

    /**
     * Builder for {@link InWrapperConverterFactory}.
     * Allows registering packers and unwrappers in a nice type safe way
     */
    public static class Builder {
        private final Map<Class, ResponseUnwrapper> responseUnwrappers = new HashMap<>();
        private final Map<Class, RequestPacker> requestPackers = new HashMap<>();
        private final Map<Class, TypeResolver> typeResolvers = new HashMap<>();

        /**
         * Registers a {@link ResponseUnwrapper}
         *
         * @param wrapperClass      class to use th unwrapper for
         * @param responseUnwrapper the unwrapper instance
         * @param <W>               wrapper type
         * @param <T>               wrapped data type
         */
        public <W, T> Builder registerUnwrapper(Class<W> wrapperClass, ResponseUnwrapper<? extends W, T> responseUnwrapper) {
            responseUnwrappers.put(wrapperClass, responseUnwrapper);
            return this;
        }

        /**
         * Registers a {@link RequestPacker}
         *
         * @param wrapperClass  class to use the packer for
         * @param requestPacker the packer instance
         * @param <W>           wrapper type
         * @param <T>           wrapped data type
         */
        public <W, T> Builder registerPacker(Class<W> wrapperClass, RequestPacker<? extends W, T> requestPacker) {
            requestPackers.put(wrapperClass, requestPacker);
            return this;
        }

        /**
         * Registers a {@link TypeResolver}
         *
         * @param wrapperClass class to resolve type for
         * @param typeResolver the type resolver instance
         */
        public Builder registerTypeResolver(Class wrapperClass, TypeResolver typeResolver) {
            typeResolvers.put(wrapperClass, typeResolver);
            return this;
        }

        /**
         * Creates a {@link InWrapperConverterFactory} instance
         *
         * @return converter factory instance
         */
        public InWrapperConverterFactory build() {
            return new InWrapperConverterFactory(responseUnwrappers, requestPackers, typeResolvers);
        }
    }
}