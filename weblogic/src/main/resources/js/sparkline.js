/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
(function () {
  globalThis.renderSparkline = function (inputJson) {
    const input = JSON.parse(inputJson);
    const values = input.values;
    const width = input.width || 320;
    const height = input.height || 80;
    const color = input.color || "#2563eb";

    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min;
    const x = i => (i / (values.length - 1)) * width;
    let y;
    if (span === 0) {
      y = () => height / 2;
    } else if (Number.isFinite(span)) {
      y = v => height - ((v - min) / span) * height;
    } else {
      const maxAbs = Math.max(...values.map(v => Math.abs(v))) || 1;
      const scaled = values.map(v => v / maxAbs);
      const scaledMin = Math.min(...scaled);
      const scaledSpan = Math.max(...scaled) - scaledMin || 1;
      y = v => height - (((v / maxAbs) - scaledMin) / scaledSpan) * height;
    }

    const line = d3.line()
        .x((d, i) => x(i))
        .y(d => y(d))
        .curve(d3.curveMonotoneX);

    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}">\n` +
        `  <path d="${line(values)}" fill="none" stroke="${color}" stroke-width="2"/>\n` +
        `</svg>`;
  };
}());
