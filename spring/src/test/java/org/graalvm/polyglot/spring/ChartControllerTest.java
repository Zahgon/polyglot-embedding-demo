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
package org.graalvm.polyglot.spring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChartControllerTest {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() {
    }

    @Test
    void rendersPostedChartDataAsSvg() throws Exception {
        HttpResponse<String> response = postChart("""
                {"values":[1,3,2,5,4],"width":400,"height":120,"color":"#16a34a"}
                """);

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("image/svg+xml"));
        assertTrue(response.body().startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 120\">"));
        assertTrue(response.body().contains("<path d=\"M"));
        assertTrue(response.body().contains("stroke=\"#16a34a\""));
    }

    @Test
    void rejectsUnsafeColor() throws Exception {
        HttpResponse<String> response = postChart("""
                {"values":[1,2,3],"color":"red"}
                """);

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("color must be a hex color"), response.body());
    }

    @Test
    void rejectsTooManyPoints() throws Exception {
        StringBuilder values = new StringBuilder("{\"values\":[");
        for (int i = 0; i < 201; i++) {
            if (i > 0) {
                values.append(',');
            }
            values.append(i);
        }
        values.append("]}");

        HttpResponse<String> response = postChart(values.toString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("values must contain between 2 and 200 numbers"), response.body());
    }

    private HttpResponse<String> postChart(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/chart"))
                .header("Content-Type", "application/json")
                .header("Accept", "image/svg+xml")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
