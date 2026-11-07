/** 把后端返回的 http://localhost:8080/files/... 规范为 /files/...，走 Vite 代理，避免 LAN/手机裂图。 */
export function resolveAssetUrl(url: string | undefined | null): string {
  if (!url) return '';
  if (url.startsWith('/')) return url;
  const idx = url.indexOf('/files/');
  if (idx >= 0) return url.substring(idx);
  return url;
}
