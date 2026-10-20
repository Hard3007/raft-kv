/**
 * On-disk storage engine: write-ahead log (CRC32 records, configurable fsync, torn-tail
 * truncation) and a B-Tree over fixed-size 4 KB pages with an LRU page cache.
 */
package io.github.hard3007.raftkv.storage;
