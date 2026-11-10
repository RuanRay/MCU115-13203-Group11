// Node 沒有 Workers 的 Cache API，以記憶體替身代替（只實作用到的 match／put）
import { beforeEach } from "vitest";

const stores = new Map<string, Map<string, Response>>();

const cacheStorage = {
  async open(name: string) {
    let store = stores.get(name);
    if (!store) stores.set(name, (store = new Map()));
    return {
      async match(key: string) {
        return store.get(key)?.clone();
      },
      async put(key: string, res: Response) {
        store.set(key, res.clone());
      },
    };
  },
};

Object.defineProperty(globalThis, "caches", { value: cacheStorage, configurable: true });

beforeEach(() => stores.clear());
