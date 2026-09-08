/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
(function () {
  function escapeXml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
  }

  globalThis.renderBarChart = function (title, xLabel, yLabel, x, y, width, height) {
    const margin = { top: 48, right: 20, bottom: 72, left: 64 };

    const xScale = d3.scaleBand()
        .domain(d3.range(y.length))
        .range([margin.left, width - margin.right])
        .padding(0.15);

    const yScale = d3.scaleLinear()
        .domain([0, d3.max(y) || 1])
        .nice()
        .range([height - margin.bottom, margin.top]);

    let ticks = "";
    for (const value of yScale.ticks(4)) {
      const tickY = yScale(value);
      ticks += `  <line x1="${margin.left - 4}" y1="${tickY}" x2="${width - margin.right}" y2="${tickY}" stroke="#e5e7eb"/>\n` +
          `  <text x="${margin.left - 8}" y="${tickY + 4}" text-anchor="end" font-size="11">${value}</text>\n`;
    }

    let rects = "";
    for (let i = 0; i < y.length; i++) {
      const barX = xScale(i);
      const barY = yScale(y[i]);
      const barHeight = yScale(0) - barY;
      rects += `  <rect x="${barX}" y="${barY}" width="${xScale.bandwidth()}" height="${barHeight}" fill="#2563eb"/>\n` +
          `  <text x="${barX + xScale.bandwidth() / 2}" y="${height - margin.bottom + 20}" text-anchor="middle" font-size="11">${escapeXml(x[i])}</text>\n`;
    }

    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}">\n` +
        `  <title>${escapeXml(title)}</title>\n` +
        `  <text x="${width / 2}" y="24" text-anchor="middle" font-size="16" font-weight="bold">${escapeXml(title)}</text>\n` +
        ticks +
        `  <line x1="${margin.left}" y1="${height - margin.bottom}" x2="${width - margin.right}" y2="${height - margin.bottom}" stroke="#111827"/>\n` +
        `  <line x1="${margin.left}" y1="${margin.top}" x2="${margin.left}" y2="${height - margin.bottom}" stroke="#111827"/>\n` +
        rects +
        `  <text x="${width / 2}" y="${height - 20}" text-anchor="middle" font-size="12">${escapeXml(xLabel)}</text>\n` +
        `  <text transform="translate(18 ${height / 2}) rotate(-90)" text-anchor="middle" font-size="12">${escapeXml(yLabel)}</text>\n` +
        `</svg>`;
  };
}());
