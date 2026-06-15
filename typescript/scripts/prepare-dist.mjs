import { rename, rm } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const distDir = path.resolve(__dirname, '..', 'dist');

await rename(path.join(distDir, 'esm', 'index.js'), path.join(distDir, 'index.mjs'));
await rename(path.join(distDir, 'cjs', 'index.js'), path.join(distDir, 'index.cjs'));

await rm(path.join(distDir, 'esm'), { recursive: true, force: true });
await rm(path.join(distDir, 'cjs'), { recursive: true, force: true });
