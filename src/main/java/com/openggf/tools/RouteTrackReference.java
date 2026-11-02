package com.openggf.tools;

import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.ReferencePath;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Reads a route's recorded engine track (a gzip CSV of P1's centre and boundary epoch after every
 * row, written by an engine replay of the route on the engine its rows were authored against) as
 * a repair {@link ReferencePath}. The header binds the engine revision and the SHA-256 of the
 * route movie it was recorded from; a track for other rows is refused. A track is a scoring
 * target only: it never boots, seeds or restores a world, and it is never trace data.
 *
 * <p>Origin: shared route planner step 6 (2026-11-02), tracks recorded on 36aaddb37 for the SSZ1
 * and LRZ→HPZ→SSZ1 routes before the ROM-owned solid-contact change.
 */
public final class RouteTrackReference {
    private RouteTrackReference() {
    }

    /** The track's header line, e.g. {@code # engine 36aaddb37 movie X.bk2 sha256 ... zone 10 ...}. */
    public record Header(String engine, String movie, String movieSha256) { }

    public static Header header(Path track) throws IOException {
        try (var reader = open(track)) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("# engine ")) throw new IOException(track + " has no track header");
            String[] parts = line.substring(2).split(" ");
            String engine = null, movie = null, sha = null;
            for (int i = 0; i + 1 < parts.length; i += 2) {
                switch (parts[i]) {
                    case "engine" -> engine = parts[i + 1];
                    case "movie" -> movie = parts[i + 1];
                    case "sha256" -> sha = parts[i + 1];
                    default -> { }
                }
            }
            if (engine == null || movie == null || sha == null) throw new IOException(track + " header is incomplete");
            return new Header(engine, movie, sha);
        }
    }

    /**
     * The reference for {@code rows} (the route's rows) under {@code launch}. {@code movie} must be
     * the exact movie file the track was recorded from.
     */
    public static ReferencePath read(Path track, Path movie, LaunchContract launch, InputTape rows) throws IOException {
        Header header = header(track);
        String sha = HexFormat.of().formatHex(sha256(Files.readAllBytes(movie)));
        if (!sha.equals(header.movieSha256())) {
            throw new IOException(track + " was recorded from movie " + header.movieSha256() + ", not " + movie + " " + sha);
        }
        var xs = new int[rows.size()];
        var ys = new int[rows.size()];
        var epochs = new long[rows.size()];
        int n = 0;
        try (var reader = open(track)) {
            reader.readLine();
            if (!"row,x,y,epoch,dead".equals(reader.readLine())) throw new IOException(track + " has an unknown column set");
            String line;
            while ((line = reader.readLine()) != null && n < rows.size()) {
                String[] f = line.split(",");
                if (Integer.parseInt(f[0]) != n) throw new IOException(track + " skips row " + n);
                xs[n] = Integer.parseInt(f[1]);
                ys[n] = Integer.parseInt(f[2]);
                epochs[n] = Long.parseLong(f[3]);
                n++;
                if (f[4].equals("1")) break;
            }
        }
        if (n == 0) throw new IOException(track + " has no rows");
        return ReferencePath.recorded(launch, rows.slice(0, n), Arrays.copyOf(xs, n), Arrays.copyOf(ys, n),
                Arrays.copyOf(epochs, n), "openggf " + header.engine() + " recorded track " + track.getFileName());
    }

    private static BufferedReader open(Path track) throws IOException {
        var in = Files.newInputStream(track);
        return new BufferedReader(new InputStreamReader(track.toString().endsWith(".gz") ? new GZIPInputStream(in) : in,
                StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
