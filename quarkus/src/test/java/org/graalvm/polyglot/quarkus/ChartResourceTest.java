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
package org.graalvm.polyglot.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;

@QuarkusTest
class ChartResourceTest {

    @Test
    void rendersPostedChartDataAsSvg() {
        given()
                .contentType("application/json")
                .accept("image/svg+xml")
                .body("""
                        {"title":"Fruit sold","xLabel":"Fruit","yLabel":"Count","x":["Apples","Bananas","Cherries"],"y":[4,7,5],"width":480,"height":320}
                        """)
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
}
