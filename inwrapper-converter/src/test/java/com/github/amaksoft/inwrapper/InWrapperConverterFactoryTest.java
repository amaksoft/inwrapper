package com.github.amaksoft.inwrapper;

import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

/**
 * Basic unit-tests for {@link InWrapperConverterFactory}
 * <p>
 * Created by amak on 25/02/18.
 */
public class InWrapperConverterFactoryTest {

    static class TestWrapper<T> {
        T value;

        TestWrapper(T value) {
            this.value = value;
        }
    }

    public static class TestPacker<T> implements InWrapperConverterFactory.RequestPacker<TestWrapper<T>, T> {
        @Override
        public TestWrapper<T> pack(T data, int depth, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
            return new TestWrapper<>(data);
        }
    }

    public static class TestUnwrapper<T> implements InWrapperConverterFactory.ResponseUnwrapper<TestWrapper<T>, T> {
        @Override
        public T unwrap(TestWrapper<T> wrapper, int depth, Annotation[] annotations) {
            return wrapper.value;
        }
    }

    private InWrapperConverterFactory factory = new InWrapperConverterFactory.Builder()
            .registerPacker(TestWrapper.class, new TestPacker<>())
            .registerUnwrapper(TestWrapper.class, new TestUnwrapper<>())
            .build();

    @Test
    public void testGetWrappedTypeSingleWrapperSingleTypeParameter() {
        Type wrappedType = InWrapperConverterFactory.getWrappedType(String.class, new Class[]{TestWrapper.class});

        assertThat(wrappedType, instanceOf(ParameterizedType.class));

        ParameterizedType parametrizedWrappedType = (ParameterizedType) wrappedType;

        assertThat(parametrizedWrappedType.getRawType(), instanceOf(Class.class));

        Class wrapperClass = (Class) parametrizedWrappedType.getRawType();

        assertThat(wrapperClass, is(equalTo((Class) TestWrapper.class)));

        assertThat(parametrizedWrappedType.getActualTypeArguments().length, is(equalTo(1)));
        assertThat(parametrizedWrappedType.getActualTypeArguments()[0], instanceOf(Class.class));

        Class wrappedClass = (Class) parametrizedWrappedType.getActualTypeArguments()[0];

        assertThat(wrappedClass, is(equalTo((Class) String.class)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWrappedTypeSingleWrapperTwoTypeParameters() {
        // we don't support more than one type parameter for wrappers
        InWrapperConverterFactory.getWrappedType(String.class, new Class[]{HashMap.Entry.class});
    }

    @Test
    public void testGetWrappedTypeNoTypeParameter() {
        Type wrappedType = InWrapperConverterFactory.getWrappedType(String.class, new Class[]{Object.class, TestWrapper.class, TestWrapper.class});

        assertThat("Wrapper type is supposed to be a class", wrappedType, instanceOf(Class.class));
        assertThat("Root wrapper is Object, type should be just Class<Object>", (Class) wrappedType, is(equalTo((Class) Object.class)));
    }

    @Test
    public void testChainPack() {
        String toWrap = "test";

        Class[] wrapperChain = new Class[]{TestWrapper.class, TestWrapper.class, TestWrapper.class};

        Object wrapped = factory.chainPack(toWrap, wrapperChain, new Annotation[]{}, new Annotation[]{});
        assertThat("Root wrapper class mismatch", wrapped, instanceOf(TestWrapper.class));

        Object l2Wrapper = ((TestWrapper) wrapped).value;
        assertThat("2nd level wrapper class mismatch", l2Wrapper, instanceOf(TestWrapper.class));

        Object l3Wrapper = ((TestWrapper) l2Wrapper).value;
        assertThat("3rd level wrapper class mismatch", l3Wrapper, instanceOf(TestWrapper.class));

        //noinspection unchecked
        assertThat("Wrapped value mismatch", ((TestWrapper<String>) l3Wrapper).value, is(equalTo(toWrap)));
    }

    @Test
    public void testChainUnwrap() {
        String toWrap = "test";

        Class[] wrapperChain = new Class[]{TestWrapper.class, TestWrapper.class, TestWrapper.class};
        Object wrapped = new TestWrapper<>(new TestWrapper<>(new TestWrapper<>(toWrap)));

        String unwrapped = (String) factory.chainUnwrap(wrapped, wrapperChain, new Annotation[]{});
        assertThat("Unwrapped value mismatch", unwrapped, is(equalTo(toWrap)));
    }

    @Test
    public void testPackUnwrap() {
        String toWrap = "test";

        Class[] wrapperChain = new Class[]{TestWrapper.class, TestWrapper.class, TestWrapper.class};
        Object wrapped = factory.chainPack(toWrap, wrapperChain, new Annotation[]{}, new Annotation[]{});
        String unwrapped = (String) factory.chainUnwrap(wrapped, wrapperChain, new Annotation[]{});
        assertThat("Unwrapped value mismatch", unwrapped, is(equalTo(toWrap)));
    }
}
