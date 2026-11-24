/* Mermaid runs locally; normalized SVG and a bounded PNG are returned to the Compose view. */
window.auraDiagram = (() => {
  const presentationProperties = [
    "fill", "stroke", "stroke-width", "stroke-dasharray", "stroke-dashoffset",
    "stroke-linecap", "stroke-linejoin", "opacity", "fill-opacity", "stroke-opacity",
    "font-family", "font-size", "font-weight", "font-style", "text-anchor",
    "dominant-baseline", "visibility", "display", "color", "marker-start", "marker-end"
  ];

  async function render(request) {
    if (request.source.length > 20000 || /%%\s*\{/.test(request.source)) {
      throw new Error("Unsupported diagram configuration");
    }
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: "strict",
      htmlLabels: false,
      theme: request.dark ? "dark" : "default",
      fontFamily: "sans-serif",
      maxTextSize: 20000,
      maxEdges: 200,
      flowchart: { htmlLabels: false, useMaxWidth: false },
      // Source directives cannot replace the rendering or isolation settings.
      secure: ["securityLevel", "htmlLabels", "theme", "flowchart", "maxTextSize", "maxEdges"]
    });
    const type = mermaid.detectType(request.source);
    if (type !== "flowchart" && type !== "flowchart-v2") throw new Error("Unsupported diagram type");

    const host = document.getElementById("diagram-host");
    try {
      const result = await mermaid.render("aura-mermaid-svg", request.source);
      host.innerHTML = result.svg;
      const svg = host.querySelector("svg");
      if (!svg || svg.querySelector("foreignObject, script, image")) throw new Error("Unsupported SVG content");
      const bounds = svg.viewBox.baseVal;
      const width = bounds.width;
      const height = bounds.height;
      if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0 || width > 32000 || height > 32000) {
        throw new Error("Invalid diagram dimensions");
      }

      // Skia has no browser CSS cascade. Resolve inherited classes before dropping the stylesheet.
      for (const element of [svg, ...svg.querySelectorAll("*")]) {
        const computed = getComputedStyle(element);
        const attributes = presentationProperties.map(property => [property, computed.getPropertyValue(property)]);
        for (const [property, raw] of attributes) {
          const value = raw.replace(/url\(["']?[^)"']*#([^\s)"']+)["']?\)/g, "url(#$1)");
          if (value) element.setAttribute(property, value);
        }
        element.removeAttribute("style");
      }
      svg.querySelectorAll("style").forEach(element => element.remove());
      svg.querySelectorAll("a").forEach(element => {
        element.removeAttribute("href");
        element.removeAttribute("xlink:href");
      });
      svg.setAttribute("width", String(width));
      svg.setAttribute("height", String(height));
      svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
      const serialized = new XMLSerializer().serializeToString(svg);
      if (serialized.length > 2000000) throw new Error("Diagram output is too large");
      // The bundled Skia version omits some SVG text/markers. Rasterize the same SVG in Chromium.
      const scale = Math.min(3, 4096 / width, 4096 / height, Math.sqrt(8000000 / (width * height)));
      const canvas = document.createElement("canvas");
      canvas.width = Math.ceil(width * scale);
      canvas.height = Math.ceil(height * scale);
      const context = canvas.getContext("2d");
      const image = new Image();
      const url = URL.createObjectURL(new Blob([serialized], { type: "image/svg+xml;charset=utf-8" }));
      try {
        await new Promise((resolve, reject) => {
          image.onload = resolve;
          image.onerror = reject;
          image.src = url;
        });
        context.scale(scale, scale);
        context.drawImage(image, 0, 0, width, height);
        const png = canvas.toDataURL("image/png").split(",")[1];
        if (png.length > 8000000) throw new Error("Diagram image is too large");
        return { svg: serialized, png, width, height };
      } finally {
        URL.revokeObjectURL(url);
      }
    } finally {
      host.replaceChildren();
      document.getElementById("daura-mermaid-svg")?.remove();
    }
  }
  return { render };
})();
