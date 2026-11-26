import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

const env = { ...process.env };
if (process.platform === 'darwin') {
  const java = spawnSync('/usr/libexec/java_home', ['-v', '21'], { encoding: 'utf8' });
  if (java.status === 0) {
    env.JAVA_HOME = java.stdout.trim();
    env.PATH = `${join(env.JAVA_HOME, 'bin')}:${env.PATH}`;
  }
}
const result = spawnSync(process.platform === 'win32' ? 'mvn.cmd' : 'mvn',
  ['-B', '-ntp', 'verify'], { env, stdio: 'inherit', shell: process.platform === 'win32' });
if (result.error) console.error('Java 21+ and Apache Maven are required:', result.error.message);
process.exit(result.status ?? 1);
