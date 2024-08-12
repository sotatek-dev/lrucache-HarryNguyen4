package dev.lillya;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public record SimpleLruAccountCache(
        int cacheSize,
        AtomicInteger currentIndex,
        AtomicInteger cacheHits,
        ConcurrentSkipListSet<LruNode<Account>> lruAccountSet,
        ConcurrentMap<Long, LruNode<Account>> accountMap,
        Queue<Consumer<Account>> accountUpdateSubscribers,
        ReentrantReadWriteLock lock) implements AccountCache {

    public SimpleLruAccountCache(final int cacheSize) {
        this(
                cacheSize,
                new AtomicInteger(0),
                new AtomicInteger(0),
                new ConcurrentSkipListSet<>(LruNode::compareAccessTime),
                new ConcurrentHashMap<>(cacheSize),
                new ConcurrentLinkedQueue<>(),
                new ReentrantReadWriteLock());
    }

    @Override
    public Account getAccountById(final long id) {
        // Get existing account
        final LruNode<Account> accountLruNode = executeReadLocked(() -> this.accountMap.get(id));

        // If account not found, bail out immediately
        if (accountLruNode == null) {
            return null;
        }

        // Otherwise, increment cache hits and update LRU set
        this.cacheHits.incrementAndGet();
        final Account account = accountLruNode.getValue();
        updateLruCache(account);
        return account;
    }

    @Override
    public void subscribeForAccountUpdates(final Consumer<Account> listener) {
        // If null passed, do nothing
        if (Objects.isNull(listener)) {
            return;
        }

        // Otherwise, add to subscribers
        this.accountUpdateSubscribers.add(listener);
    }

    @Override
    public List<Account> getTop3AccountsByBalance() {
        return this.lruAccountSet
                .stream()
                .map(LruNode::getValue)
                .sorted(Collections.reverseOrder())
                .limit(3)
                .toList();
    }

    @Override
    public int getAccountByIdHitCount() {
        return this.cacheHits.get();
    }

    @Override
    public void putAccount(final Account account) {
        // If null passed, do nothing
        if (Objects.isNull(account)) {
            return;
        }

        // Check if account exists in the cache, then update LRU cache state
        updateLruCache(account);

        // Notify subscribers for account updates
        this.accountUpdateSubscribers.parallelStream().forEach(listener -> listener.accept(account));
    }

    private void updateLruCache(final Account account) {
        try {
            this.lock.writeLock().lock();

            // Remove existing account from set
            final LruNode<Account> existingAccountLruNode =
                    executeReadLocked(() -> this.accountMap.get(account.id()));
            if (!Objects.isNull(existingAccountLruNode)) {
                this.lruAccountSet.remove(existingAccountLruNode);
            }

            // Check if current capacity passes maximum capacity
            // If passes, evict least recently used/accessed account from the map
            if (this.lruAccountSet.size() == this.cacheSize) {
                final LruNode<Account> evictNode = this.lruAccountSet.pollFirst();

                // Make sure the evicting object exists
                if (Objects.nonNull(evictNode)) {
                    final Account evictedAccount = evictNode.getValue();
                    executeReadLocked(() -> this.accountMap.remove(evictedAccount.id()));
                }
            }

            // Push the new account to the LRU set
            final LruNode<Account> newAccountLruNode = new LruNode<>(account, currentIndex.incrementAndGet());
            this.lruAccountSet.add(newAccountLruNode);
            executeReadLocked(() -> this.accountMap.put(account.id(), newAccountLruNode));
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private <V> V executeReadLocked(final Callable<V> callable) {
        try {
            this.lock.readLock().lock();
            return callable.call();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
