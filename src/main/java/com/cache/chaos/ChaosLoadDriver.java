package com.cache.chaos;

import com.cache.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ChaosLoadDriver {

    private final CacheServiceGrpc.CacheServiceBlockingStub stub;
    private final ManagedChannel channel;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

    public ChaosLoadDriver(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        this.stub = CacheServiceGrpc.newBlockingStub(channel);
    }

    public static void main(String[] args) {
        int duration = 60;
        int threads = 10;

        if (args.length >= 1) duration = Integer.parseInt(args[0]);
        if (args.length >= 2) threads = Integer.parseInt(args[1]);

        System.out.println("Starting chaos load driver...");
        System.out.println("Duration: " + duration + "s, Threads: " + threads);

        ChaosLoadDriver driver = new ChaosLoadDriver("localhost", 9090);
        driver.run(duration, threads);
        driver.shutdown();
    }

    public void run(int durationSeconds, int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> workerLoop(endTime));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(durationSeconds + 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        printResults();
    }

    private void workerLoop(long endTime) {
        int keyCounter = 0;
        while (System.currentTimeMillis() < endTime) {
            String key = "chaos-" + Thread.currentThread().getId() + "-" + keyCounter++;

            long start = System.currentTimeMillis();
            boolean ok = doWithRetry(() -> {
                stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .set(SetRequest.newBuilder()
                        .setKey(key)
                        .setValue("value-" + key)
                        .setTtlSeconds(300)
                        .build());
            });
            long elapsed = System.currentTimeMillis() - start;
            recordResult(ok, elapsed);

            start = System.currentTimeMillis();
            ok = doWithRetry(() -> {
                stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .get(GetRequest.newBuilder()
                        .setKey(key)
                        .build());
            });
            elapsed = System.currentTimeMillis() - start;
            recordResult(ok, elapsed);
        }
    }

    private boolean doWithRetry(Runnable operation) {
        try {
            operation.run();
            return true;
        } catch (Exception e) {
            retryCount.incrementAndGet();
            try {
                Thread.sleep(200);
                operation.run();
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    private void recordResult(boolean success, long latencyMs) {
        if (success) {
            successCount.incrementAndGet();
        } else {
            failureCount.incrementAndGet();
        }
        latencies.add(latencyMs);
    }

    private void printResults() {
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int total = sorted.size();

        if (total == 0) {
            System.out.println("No requests completed.");
            return;
        }

        long p50 = sorted.get((int) (total * 0.50));
        long p99 = sorted.get((int) (total * 0.99));
        long max = sorted.get(total - 1);

        System.out.println();
        System.out.println("========= CHAOS TEST RESULTS =========");
        System.out.println("Total requests: " + total);
        System.out.println("Successes:      " + successCount.get());
        System.out.println("Failures:       " + failureCount.get());
        System.out.println("Retries:        " + retryCount.get());
        System.out.println("Success rate:   " +
            String.format("%.2f%%", successCount.get() * 100.0 / total));
        System.out.println();
        System.out.println("Latency:");
        System.out.println("  p50: " + p50 + "ms");
        System.out.println("  p99: " + p99 + "ms");
        System.out.println("  max: " + max + "ms");
        System.out.println("======================================");
    }

    private void shutdown() {
        channel.shutdown();
    }
}
