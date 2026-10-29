import type { FullConfig } from '@playwright/test';
import fs from 'node:fs';
import { chromium, firefox, webkit } from '@playwright/test';

/**
 * Runs once before the tests. Playwright tests a RUNNING shop with installed browsers; without them every test
 * fails with "ECONNREFUSED" or "Executable doesn't exist", which reads like hundreds of bugs. This stops the run at
 * once with one plain explanation instead.
 */
export default async function globalSetup(config: FullConfig) {
  const base = config.projects[0]?.use?.baseURL ?? 'http://localhost:5173';
  const problems: string[] = [];

  for (const [what, url] of [['storefront', base], ['API through the storefront', `${base}/api/products?size=1`]]) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(5000) });
      // the API must really answer (a storefront without its backend gives 404 or 5xx here)
      const ok = url.includes('/api/') ? r.ok : r.status < 500;
      if (!ok) problems.push(`${what} answers ${r.status} at ${url} - is the backend running?`);
    } catch {
      problems.push(`${what} is not reachable at ${url}`);
    }
  }
  if (problems.length) {
    problems.push('Start the whole shop first:  make app   (or: docker compose --profile app up -d --build --wait)',
      'Docker Desktop must be running. Stop the shop later with: make down');
  }

  // only the browsers of the projects that will actually run (--project chromium needs only Chromium)
  const args = process.argv.slice(2);
  const chosen = args.flatMap((a, i) => a.startsWith('--project=') ? [a.slice(10)] : a === '--project' ? [args[i + 1]] : []);
  const projects = chosen.length ? config.projects.filter(p => chosen.includes(p.name)) : config.projects;
  const engines = new Set(projects.map(p => p.use?.defaultBrowserType ?? p.use?.browserName ?? 'chromium'));
  const missing = [...engines].filter(name => {
    const type = { chromium, firefox, webkit }[name as 'chromium' | 'firefox' | 'webkit'];
    return type && !fs.existsSync(type.executablePath());
  });
  if (missing.length) {
    problems.push(`Browser not installed: ${missing.join(', ')}. Install once:  npx playwright install ${missing.join(' ')}`);
  }

  if (problems.length) {
    const line = '='.repeat(78);
    throw new Error(`\n${line}\n Playwright cannot start - nothing was tested:\n${problems.map(p => `   - ${p}`).join('\n')}\n${line}\n`);
  }
}
