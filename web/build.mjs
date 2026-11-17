import { spawnSync } from 'node:child_process'

const command = process.platform === 'win32' ? 'rspack.cmd' : 'rspack'
const result = spawnSync(command, ['build', '-c', 'rspack.config.ts'], {
  env: { ...process.env, NODE_ENV: 'production' },
  stdio: 'inherit',
  shell: process.platform === 'win32',
})
process.exit(result.status ?? 1)
