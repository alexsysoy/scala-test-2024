package alex.test.impl;

import alex.test.interfaces.ApplicationStatusResponse;
import alex.test.interfaces.Client;
import alex.test.interfaces.Handler;
import alex.test.interfaces.Response;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HandlerImpl implements Handler {
    private static final int THREADS = 2;
    private static final int TIMEOUT = 15;
    private final Client client;
    private final ThreadPoolExecutor executor;
    private int retriesCount;

    private LocalDateTime errorTime;

    public HandlerImpl(Client client) {
        this.client = client;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREADS);
        this.retriesCount = 0;
        this.errorTime = LocalDateTime.now();
    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {
        LocalDateTime startTime = LocalDateTime.now();
        try {
            Response response = CompletableFuture.anyOf(
                            CompletableFuture.supplyAsync(() -> client.getApplicationStatus1(id), executor),
                            CompletableFuture.supplyAsync(() -> client.getApplicationStatus2(id), executor)
                    )
                    .thenApply(answer -> (Response) answer)
                    .get(TIMEOUT, TimeUnit.SECONDS);

            if (response instanceof Response.Success success) {
                return new ApplicationStatusResponse.Success(success.applicationId(), success.applicationStatus());
            } else if (response instanceof Response.RetryAfter) {
                return new ApplicationStatusResponse.Failure(handleError(startTime), retriesCount);
            } else {
                return new ApplicationStatusResponse.Failure(handleError(startTime), retriesCount);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return new ApplicationStatusResponse.Failure(handleError(startTime), retriesCount);
        }
    }

    private Duration handleError(LocalDateTime current) {
        retriesCount = retriesCount + 1;
        Duration duration = Duration.between(current, errorTime);
        errorTime = current;
        return duration;
    }
}
