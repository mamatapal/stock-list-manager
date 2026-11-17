package app.stocklist.mail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/** A local stand-in for Google's token endpoint and the Gmail send endpoint. Nothing in the tests calls Google. */
final class StubGoogle implements AutoCloseable {
    record Reply(int status, String body) {}

    final AtomicInteger tokenCalls = new AtomicInteger();
    final AtomicInteger sendCalls = new AtomicInteger();
    final List<String> tokenForms = new CopyOnWriteArrayList<>();
    final List<String> authorizations = new CopyOnWriteArrayList<>();
    final List<String> raws = new CopyOnWriteArrayList<>();

    /** Reply to the n-th token request (1-based). Default: a new access token "AT-n" valid for an hour. */
    volatile IntFunction<Reply> tokenReply = n -> new Reply(200,
            "{\"access_token\":\"AT-" + n + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
    /** HTTP status for the n-th send request (1-based). Default: 200. */
    volatile IntUnaryOperator sendStatus = n -> 200;
    /** Delay before answering a token request, to make concurrent callers overlap. */
    volatile long tokenDelayMillis = 0;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    StubGoogle() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/token", ex -> {
            int n = tokenCalls.incrementAndGet();
            tokenForms.add(read(ex));
            if (tokenDelayMillis > 0) sleep(tokenDelayMillis);
            Reply r = tokenReply.apply(n);
            reply(ex, r.status(), r.body());
        });
        server.createContext("/gmail/v1/users/me/messages/send", ex -> {
            int n = sendCalls.incrementAndGet();
            authorizations.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            String body = read(ex);
            int start = body.indexOf("\"raw\":\"") + 7;
            raws.add(body.substring(start, body.lastIndexOf('"')));
            reply(ex, sendStatus.applyAsInt(n), "{\"id\":\"m" + n + "\"}");
        });
        server.start();
    }

    URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    URI tokenUri() {
        return base().resolve("token");
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static String read(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void reply(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
