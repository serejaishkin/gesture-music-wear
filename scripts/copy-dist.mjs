import { mkdirSync, cpSync, rmSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const assets = join(root, 'app', 'src', 'main', 'assets');

rmSync(assets, { recursive: true, force: true });
mkdirSync(assets, { recursive: true });
cpSync(join(root, 'dist'), assets, { recursive: true });

console.log(`Web assets copied to ${assets}`);