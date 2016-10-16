package org.insight.urlexpander.utils;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Cache implements Map<String, String> {

  public enum CacheType {
    MEMORY, // fastutil
    LEVELDB, // leveldb compatible? https://github.com/edsu/unshrtn https://github.com/fusesource/leveldbjni or https://github.com/dain/leveldb
    REDIS // ?
  }

  private Map<String, String> cache;

  public Cache(CacheType type) {
    switch (type) {

      case LEVELDB:
        break;

      case MEMORY:
        this.cache = new Object2ObjectOpenHashMap<String, String>();
        break;

      case REDIS:
        break;

      default:
        break;
    }
  }

  @Override
  public int size() {
    return cache.size();
  }

  @Override
  public boolean isEmpty() {
    return cache.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return cache.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return cache.containsValue(value);
  }

  @Override
  public String get(Object key) {
    return cache.get(key);
  }

  @Override
  public String put(String key, String value) {
    return cache.put(key, value);
  }

  @Override
  public String remove(Object key) {
    return cache.remove(key);
  }

  @Override
  public void putAll(Map<? extends String, ? extends String> m) {
    cache.putAll(m);
  }

  @Override
  public void clear() {
    cache.clear();
  }

  @Override
  public Set<String> keySet() {
    return cache.keySet();
  }

  @Override
  public Collection<String> values() {
    return cache.values();
  }

  @Override
  public Set<java.util.Map.Entry<String, String>> entrySet() {
    return cache.entrySet();
  }

}
