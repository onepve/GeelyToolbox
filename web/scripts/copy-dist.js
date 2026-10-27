import fs from 'fs';
import path from 'path';

const distFile = path.resolve('dist/index.html');
const targetFile = path.resolve('../app/src/main/assets/toolbox_ui.html');

if (fs.existsSync(distFile)) {
  const content = fs.readFileSync(distFile, 'utf-8');
  fs.writeFileSync(targetFile, content, 'utf-8');
  console.log(`[Vite-SingleFile] Successfully updated Android asset: ${targetFile} (${(content.length / 1024).toFixed(1)} KB)`);
} else {
  console.error(`Dist file not found at: ${distFile}`);
  process.exit(1);
}
