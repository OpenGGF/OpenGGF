package openggf.racing.server.master;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

/** Authenticated loopback readiness probe for service/container supervisors. */
public final class MasterHealthCheckMain {
    private MasterHealthCheckMain() { }

    public static void main(String[] args) {
        int status = check(args);
        if (status != 0) {
            // Do not print configuration or request headers: they include operator secrets.
            System.err.println("Master health check failed");
            System.exit(status);
        }
    }

    static int check(String[] args) {
        if (args.length != 2 || !"--config".equals(args[0])) return 1;
        try {
            MasterConfig config = MasterConfig.load(Path.of(args[1]));
            if (config.adminToken().isBlank()) return 1;
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build()) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                                "http://127.0.0.1:" + config.adminPort() + "/admin/health"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer " + config.adminToken())
                        .GET().build();
                return client.send(request, HttpResponse.BodyHandlers.discarding())
                        .statusCode() == 204 ? 0 : 1;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 1;
        } catch (Exception unavailable) {
            return 1;
        }
    }
}
