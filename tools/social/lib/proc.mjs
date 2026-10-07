import { spawn } from 'node:child_process';

/** Runs a command, collects stdout (Buffer) and stderr (string), rejects on a non-zero exit. */
export function run(cmd, args, { input } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
    const out = [];
    let err = '';
    child.stdout.on('data', (d) => out.push(d));
    child.stderr.on('data', (d) => {
      err += d.toString();
    });
    child.on('error', reject);
    child.on('close', (code) => {
      const stdout = Buffer.concat(out);
      if (code === 0) resolve({ stdout, stderr: err });
      else reject(new Error(`${cmd} ${args.join(' ')} exited with ${code}\n${err.slice(-2000)}`));
    });
    if (input) child.stdin.end(input);
    else child.stdin.end();
  });
}

export async function probeDuration(file) {
  const { stdout } = await run('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'default=nw=1:nk=1', file]);
  const d = Number(stdout.toString().trim());
  if (!Number.isFinite(d) || d <= 0) throw new Error(`could not read the duration of ${file}`);
  return d;
}
