package com.github.amaksoft.inwrapper;

import com.google.gson.Gson;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

import java.io.IOException;
import java.lang.annotation.Annotation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

/**
 * Integrated tests for {@link InWrapperConverterFactory}
 * (using actual {@link Retrofit} setup)
 * <p>
 * Created by amak on 25/02/18.
 */
public class InWrapperConverterFactoryIntegratedTest {

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

    interface ApiService {
        @POST("/")
        @InWrapper({TestWrapper.class, TestWrapper.class})
        Call<String> getTestData(
                @Body @InWrapper({TestWrapper.class, TestWrapper.class, TestWrapper.class}) String body
        );
    }

    private static Gson gson = new Gson();
    private static ApiService apiService;

    @Rule
    public final MockWebServer mockWebServer = new MockWebServer();

    @Before
    public void setUp() {
        InWrapperConverterFactory inWrapperConverterFactory = new InWrapperConverterFactory.Builder()
                .registerPacker(TestWrapper.class, new TestPacker<>())
                .registerUnwrapper(TestWrapper.class, new TestUnwrapper<>())
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(inWrapperConverterFactory)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .baseUrl(mockWebServer.url("/"))
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    @Test
    public void testRequestResponse() throws IOException, InterruptedException {

        String requestValue = "REQUEST";
        String responseValue = "RESPONSE";

        Object actualResponse = new TestWrapper<>(new TestWrapper<>(responseValue));
        mockWebServer.enqueue(
                new MockResponse().setBody(gson.toJson(actualResponse))
        );

        Call<String> call = apiService.getTestData(requestValue);
        Response<String> response = call.execute();

        assertThat("Request failed", response.isSuccessful());

        String resultRequestValue = mockWebServer.takeRequest().getBody().readUtf8();
        Object expectedRequest = new TestWrapper<>(new TestWrapper<>(new TestWrapper<>(requestValue)));
        String expectedRequestValue = gson.toJson(expectedRequest);

        assertThat("Request value mismatch", resultRequestValue, is(equalTo(expectedRequestValue)));

        String resultResponseValue = response.body();

        assertThat("Response value mismatch", resultResponseValue, is(equalTo(responseValue)));
    }
}
