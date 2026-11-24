/* Browser integration checks for the exact bundled runtime. See docs/mermaid-rendering.md. */
const assert = require("node:assert/strict");
const path = require("node:path");
const { chromium } = require(process.env.AURA_PLAYWRIGHT_MODULE || "playwright");

async function main() {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.AURA_CHROMIUM_EXECUTABLE ? { executablePath: process.env.AURA_CHROMIUM_EXECUTABLE } : {}),
  });
  try {
    const page = await browser.newPage();
    const requests = [];
    await page.route(/^https?:/, route => {
      requests.push(route.request().url());
      route.abort();
    });
    await page.setContent(`<!doctype html>
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src blob: data:">
      <div id="diagram-host"></div>`);
    const resources = path.resolve(__dirname, "../../main/resources/diagram/mermaid");
    await page.addScriptTag({ path: path.join(resources, "mermaid.min.js") });
    await page.addScriptTag({ path: path.join(resources, "runtime.js") });

    const sources = [
      "graph RL\n A[Hello] --> B[World]",
      "flowchart TD\n subgraph 中文模块\n A[开始] --> B{通过?}\n end\n B -->|是| C[结束]",
      'flowchart LR\n A["quoted #quot; text"] --> B["<script>window.hacked=true</script>"]',
    ];
    for (const source of sources) {
      for (const dark of [false, true]) {
        const result = await page.evaluate(request => auraDiagram.render(request), { source, dark });
        assert(result.width > 0 && result.height > 0);
        const png = Buffer.from(result.png, "base64");
        assert.equal(png.subarray(1, 4).toString(), "PNG");
        assert(png.readUInt32BE(16) <= 4096 && png.readUInt32BE(20) <= 4096);
        assert(!/<foreignObject|<script|<style/.test(result.svg));
      }
    }

    for (const source of [
      "flowchart LR\n A --> [",
      "sequenceDiagram\nA->>B:hi",
      '%%{init:{"securityLevel":"loose"}}%%\nflowchart LR\nA-->B',
      "flowchart LR\n" + "A".repeat(21000),
    ]) {
      const rejected = await page.evaluate(async source => {
        try {
          await auraDiagram.render({ source, dark: false });
          return false;
        } catch {
          return true;
        }
      }, source);
      assert(rejected);
    }
    assert.deepEqual(requests, []);
    assert.equal(await page.evaluate(() => window.hacked), undefined);
    assert.equal(await page.locator("#diagram-host").innerHTML(), "");
    console.log("10 browser render/fallback cases passed; no network requests.");
  } finally {
    await browser.close();
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
