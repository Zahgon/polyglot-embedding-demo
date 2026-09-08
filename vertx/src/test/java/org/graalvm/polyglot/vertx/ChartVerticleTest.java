/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.polyglot.vertx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.restassured.RestAssured;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the service over HTTP. {@link #start()} and {@link #stop()} are the seam the packaged-mode
 * subclass replaces, so every assertion below runs unchanged against both the in-JVM server and the
 * runnable artifact.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChartVerticleTest {

    private static final String CHART_JSON = """
                    {"title":"Fruit sold","xLabel":"Fruit","yLabel":"Count","x":["Apples","Bananas","Cherries"],"y":[4,7,5],"width":480,"height":320}
                    """;

    private Vertx vertx;
    private HttpClient client;
    private int port;

    @BeforeAll
    void startService() {
        port = start();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        RestAssured.port = port;
    }

    @AfterAll
    void stopService() {
        stop();
    }

    /**
     * Starts the service under test and returns the port it accepts requests on.
     */
    protected int start() {
        vertx = Vertx.vertx();
        ChartVerticle verticle = new ChartVerticle(0);
        vertx.deployVerticle(verticle).toCompletionStage().toCompletableFuture().join();
        return verticle.port();
    }

    protected void stop() {
        if (vertx != null) {
            vertx.close().toCompletionStage().toCompletableFuture().join();
        }
    }

    @Test
    void rendersPostedChartDataAsSvg() {
        given()
                .contentType("application/json")
                .accept("image/svg+xml")
                .body(CHART_JSON)
                .when()
                .post("/chart")
                .then()
                .statusCode(200)
                .contentType("image/svg+xml")
                .body(startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 480 320\">"))
                .body(containsString("<title>Fruit sold</title>"))
                .body(containsString(">Fruit<"))
                .body(containsString(">Count<"))
                .body(containsString("<rect x=\""));
    }

    @Test
    void rejectsNegativeBarValue() {
        given()
                .contentType("application/json")
                .accept("image/svg+xml")
                .body("""
                        {"title":"Fruit sold","xLabel":"Fruit","yLabel":"Count","x":["Apples"],"y":[-1]}
                        """)
                .when()
                .post("/chart")
                .then()
                .statusCode(400)
                .body(containsString("y values must be finite, non-negative numbers"));
    }

    @Test
    void rejectsTooManyBars() {
        StringBuilder chartJson = new StringBuilder("{\"x\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                chartJson.append(',');
            }
            chartJson.append("\"Bar ").append(i).append('\"');
        }
        chartJson.append("],\"y\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                chartJson.append(',');
            }
            chartJson.append(i);
        }
        chartJson.append("]}");

        given()
                .contentType("application/json")
                .accept("image/svg+xml")
                .body(chartJson.toString())
                .when()
                .post("/chart")
                .then()
                .statusCode(400)
                .body(containsString("x and y must contain the same number of entries, between 1 and 100"));
    }

    // The assertions below pin the wire contract the migration has to keep. Each one fails if a
    // seam of the HTTP layer is wired differently from the service it replaces, even though the
    // service would still build and the tests above would still pass.

    @Test
    void svgResponseCarriesNoCharsetParameter() {
        HttpResponse<byte[]> response = post("/chart", CHART_JSON.getBytes(StandardCharsets.UTF_8), "application/json", "image/svg+xml");
        assertEquals(200, response.statusCode());
        assertEquals("image/svg+xml", contentType(response));
    }

    @Test
    void unicodeLabelsAreServedAsUtf8() {
        String unicode = """
                        {"title":"Obst äöü 中文 éè","xLabel":"Frucht Ä","yLabel":"Anzahl Ö","x":["Äpfel","Bananen 中","Kirschen é"],"y":[4,7,5]}
                        """;
        HttpResponse<byte[]> response = post("/chart", unicode.getBytes(StandardCharsets.UTF_8), "application/json", "image/svg+xml");
        assertEquals(200, response.statusCode());
        for (String label : new String[]{"Obst äöü 中文 éè", "Äpfel", "Bananen 中", "Kirschen é", "Frucht Ä", "Anzahl Ö"}) {
            byte[] expected = label.getBytes(StandardCharsets.UTF_8);
            assertTrue(indexOf(response.body(), expected) >= 0, () -> "Response is not UTF-8 encoded, missing: " + label);
        }
    }

    @Test
    void missingRequestContentTypeIsStillAccepted() {
        HttpResponse<byte[]> response = post("/chart", CHART_JSON.getBytes(StandardCharsets.UTF_8), null, null);
        assertEquals(200, response.statusCode());
        assertEquals("image/svg+xml", contentType(response));
    }

    @Test
    void acceptingOnlyTextStillReturnsSvg() {
        HttpResponse<byte[]> response = post("/chart", CHART_JSON.getBytes(StandardCharsets.UTF_8), "application/json", "text/plain");
        assertEquals(200, response.statusCode());
        assertEquals("image/svg+xml", contentType(response));
    }

    @Test
    void unsupportedRequestContentTypeIsRejectedWithoutBody() {
        HttpResponse<byte[]> response = post("/chart", CHART_JSON.getBytes(StandardCharsets.UTF_8), "text/plain", "image/svg+xml");
        assertEquals(415, response.statusCode());
        assertEquals(0, response.body().length);
        assertNull(contentType(response));
        assertTrue(response.headers().firstValue("accept").isEmpty(), "415 must not advertise the accepted media type");
    }

    @Test
    void unsupportedAcceptHeaderIsRejectedWithoutBody() {
        HttpResponse<byte[]> response = post("/chart", CHART_JSON.getBytes(StandardCharsets.UTF_8), "application/json", "application/pdf");
        assertEquals(406, response.statusCode());
        assertEquals(0, response.body().length);
        assertNull(contentType(response));
    }

    @Test
    void unparseableJsonIsRejectedWithoutMessage() {
        HttpResponse<byte[]> response = post("/chart", "{not json".getBytes(StandardCharsets.UTF_8), "application/json", "image/svg+xml");
        assertEquals(400, response.statusCode());
        assertEquals(0, response.body().length);
        assertNull(contentType(response));
    }

    @Test
    void emptyBodyIsRejectedWithMessage() {
        HttpResponse<byte[]> response = post("/chart", new byte[0], "application/json", "image/svg+xml");
        assertEquals(400, response.statusCode());
        assertEquals("text/plain", contentType(response));
        assertArrayEquals("Request body must contain chart input JSON.".getBytes(StandardCharsets.UTF_8), response.body());
    }

    @Test
    void unknownPropertiesInRequestAreIgnored() {
        String withExtra = """
                        {"title":"Fruit sold","surprise":42,"x":["Apples"],"y":[4]}
                        """;
        HttpResponse<byte[]> response = post("/chart", withExtra.getBytes(StandardCharsets.UTF_8), "application/json", "image/svg+xml");
        assertEquals(200, response.statusCode());
        assertEquals("image/svg+xml", contentType(response));
    }

    @Test
    void unknownRouteRendersTheFrameworkNotFoundPage() {
        HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("/missing")).GET());
        assertEquals(404, response.statusCode());
        assertEquals("text/html; charset=utf-8", contentType(response));
        assertArrayEquals("<html><body><h1>Resource not found</h1></body></html>".getBytes(StandardCharsets.UTF_8), response.body());
    }

    @Test
    void trailingPathSegmentIsNotFound() {
        HttpResponse<byte[]> response = post("/chart/extra", CHART_JSON.getBytes(StandardCharsets.UTF_8), "application/json", "image/svg+xml");
        assertEquals(404, response.statusCode());
        assertEquals("text/html; charset=utf-8", contentType(response));
    }

    @Test
    void otherMethodsOnTheChartPathAreRejectedWithoutBody() {
        for (String method : new String[]{"GET", "PUT", "DELETE"}) {
            HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("/chart"))
                            .method(method, BodyPublishers.noBody()));
            assertEquals(405, response.statusCode(), () -> method + " on /chart");
            assertEquals(0, response.body().length, () -> method + " on /chart");
            assertNull(contentType(response), () -> method + " on /chart");
            assertTrue(response.headers().firstValue("allow").isEmpty(), () -> method + " on /chart must not advertise the allowed methods");
        }
    }

    private HttpResponse<byte[]> post(String path, byte[] body, String contentType, String accept) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).POST(BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (accept != null) {
            request.header("Accept", accept);
        }
        return send(request);
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder request) {
        try {
            return client.send(request.timeout(Duration.ofSeconds(60)).build(), BodyHandlers.ofByteArray());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String contentType(HttpResponse<byte[]> response) {
        return response.headers().firstValue("content-type").orElse(null);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer: for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
