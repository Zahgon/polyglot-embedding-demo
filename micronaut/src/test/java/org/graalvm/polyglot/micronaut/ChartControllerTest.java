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
package org.graalvm.polyglot.micronaut;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class ChartControllerTest {

    @Inject
    EmbeddedApplication<?> application;

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void applicationStarts() {
        Assertions.assertTrue(application.isRunning());
    }

    @Test
    void rendersPostedChartDataAsSvg() {
        HttpRequest<String> request = HttpRequest.POST("/chart", """
                {"values":[1,3,2,5,4],"width":400,"height":120,"color":"#16a34a"}
                """)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.IMAGE_SVG_TYPE);

        String response = client.toBlocking().retrieve(request);

        Assertions.assertTrue(response.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 120\">"));
        Assertions.assertTrue(response.contains("<path d=\"M"));
        Assertions.assertTrue(response.contains("stroke=\"#16a34a\""));
    }

    @Test
    void rejectsUnsafeColor() {
        HttpRequest<String> request = HttpRequest.POST("/chart", """
                {"values":[1,2,3],"color":"red"}
                """)
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.IMAGE_SVG_TYPE);

        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(request));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        String body = exception.getResponse().getBody(String.class).orElse("");
        Assertions.assertTrue(body.contains("color must be a hex color"), body);
    }

    @Test
    void rejectsTooManyPoints() {
        StringBuilder values = new StringBuilder("{\"values\":[");
        for (int i = 0; i < 201; i++) {
            if (i > 0) {
                values.append(',');
            }
            values.append(i);
        }
        values.append("]}");

        HttpRequest<String> request = HttpRequest.POST("/chart", values.toString())
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .accept(MediaType.IMAGE_SVG_TYPE);

        HttpClientResponseException exception = Assertions.assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(request));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        String body = exception.getResponse().getBody(String.class).orElse("");
        Assertions.assertTrue(body.contains("values must contain between 2 and 200 numbers"), body);
    }
}
