import { useCallback, useEffect, useState } from 'react';
import { adminApi } from '../api/admin';

const STORAGE_KEY = 'wig_admin_token';

/**
 * 运营后台鉴权 hook（MVP 单 token）。
 * 关键设计：
 *   - token 持久化到 localStorage，刷新页面不掉线
 *   - login 时调一次 ping 接口验证 token 有效性，避免输错后续接口连环报错
 *   - 任何后续接口报 401，组件应主动调 logout()
 */
export function useAdminAuth() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [authed, setAuthed] = useState(false);
  const [pinging, setPinging] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) {
      setAuthed(false);
      return;
    }
    let cancelled = false;
    setPinging(true);
    adminApi
      .ping(token)
      .then(() => {
        if (!cancelled) setAuthed(true);
      })
      .catch((e: Error) => {
        if (cancelled) return;
        setError(e.message);
        setAuthed(false);
        localStorage.removeItem(STORAGE_KEY);
        setToken(null);
      })
      .finally(() => {
        if (!cancelled) setPinging(false);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  const login = useCallback(async (input: string) => {
    setError(null);
    setPinging(true);
    try {
      await adminApi.ping(input);
      localStorage.setItem(STORAGE_KEY, input);
      setToken(input);
      setAuthed(true);
      return true;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      return false;
    } finally {
      setPinging(false);
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setToken(null);
    setAuthed(false);
  }, []);

  return { token, authed, pinging, error, login, logout };
}
