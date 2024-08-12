package dev.lillya;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SimpleLruAccountCacheTest {

    @Test
    @DisplayName("Should put and get account successfully")
    void shouldPutAndGetAccountSuccessfully() {
        // Given
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(1);
        final Account newAccount = new Account(1L, 1000L);

        // When
        lruAccountCache.putAccount(newAccount);

        // Then
        final Account savedAccount = lruAccountCache.getAccountById(1L);
        assertNotNull(savedAccount);
        assertEquals(1L, savedAccount.id());
        assertEquals(1000L, savedAccount.balance());
    }

    @Test
    @DisplayName("Should put and get account multithreaded successfully")
    void shouldPutAndGetAccountMultithreadedSuccessfully() {
        // Given
        final int accountsCount = 1000000;
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(accountsCount);

        // When
        IntStream
                .rangeClosed(1, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account newAccount = new Account(id, 1000L);
                    lruAccountCache.putAccount(newAccount);
                });

        // Then
        IntStream
                .rangeClosed(1, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account savedAccount = lruAccountCache.getAccountById(id);
                    assertNotNull(savedAccount, "Account with id %d not found".formatted(id));
                    assertEquals(id, savedAccount.id());
                    assertEquals(1000L, savedAccount.balance());
                });
    }

    @Test
    @DisplayName("Should put and get account of the same id multithreaded successfully")
    void shouldPutAndGetAccountOfTheSameIdMultithreadedSuccessfully() {
        // Given
        final int accountsCount = 1000000;
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(accountsCount);

        // When
        IntStream
                .rangeClosed(1, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account newAccount = new Account(1L, 1000L);
                    lruAccountCache.putAccount(newAccount);
                });

        // Then
        IntStream
                .rangeClosed(1, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account savedAccount = lruAccountCache.getAccountById(1L);
                    assertNotNull(savedAccount, "Account with id %d not found".formatted(1L));
                    assertEquals(1L, savedAccount.id());
                    assertEquals(1000L, savedAccount.balance());
                });
    }

    @Test
    @DisplayName("Should put and get account with eviction multithreaded successfully")
    void shouldPutAndGetAccountWithEvictionMultithreadedSuccessfully() {
        // Given
        final int accountsCount = 1000000;
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(500000);

        // When
        IntStream
                .rangeClosed(1, accountsCount)
                .forEach(id -> {
                    final Account newAccount = new Account(id, 1000L);
                    lruAccountCache.putAccount(newAccount);
                });

        // Then
        IntStream
                .rangeClosed(500001, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account savedAccount = lruAccountCache.getAccountById(id);
                    assertNotNull(savedAccount, "Account with id %d not found".formatted(id));
                    assertEquals(id, savedAccount.id());
                    assertEquals(1000L, savedAccount.balance());
                });

        // Try to get an evicted account
        assertNull(lruAccountCache.getAccountById(1L));
    }

    @Test
    @DisplayName("Should return correct hit counts")
    void shouldReturnCorrectHitCounts() {
        // Given
        final int accountsCount = 1000000;
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(accountsCount);

        // When
        IntStream
                .rangeClosed(1, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account newAccount = new Account(id, 1000L);
                    lruAccountCache.putAccount(newAccount);
                });

        // Then
        IntStream
                .rangeClosed(1, accountsCount)
                .parallel()
                .forEach(id -> {
                    final Account savedAccount = lruAccountCache.getAccountById(id);
                    assertNotNull(savedAccount, "Account with id %d not found".formatted(id));
                    assertEquals(id, savedAccount.id());
                    assertEquals(1000L, savedAccount.balance());
                });

        // Try to increase cache hit once more
        lruAccountCache.getAccountById(5L);
        assertEquals(1000001, lruAccountCache.getAccountByIdHitCount());
    }

    @Test
    @DisplayName("Should be able to listen to account changes")
    void shouldBeAbleToListenToAccountChanges() {
        // Given
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(3);
        final AtomicInteger count = new AtomicInteger(0);
        final Account account1 = new Account(1L, 3000L);
        final Account account2 = new Account(2L, 1000L);
        final Account account3 = new Account(3L, 6000L);
        final Consumer<Account> listener = account -> count.incrementAndGet();
        final Consumer<Account> listener2 = account -> count.incrementAndGet();

        // When
        lruAccountCache.subscribeForAccountUpdates(listener);
        lruAccountCache.subscribeForAccountUpdates(listener2);
        lruAccountCache.putAccount(account1);
        lruAccountCache.putAccount(account2);
        lruAccountCache.putAccount(account3);

        // Then
        assertEquals(6, count.get());
    }

    @Test
    @DisplayName("Should get top 3 by balance successfully")
    void shouldGetTop3ByBalanceSuccessfully() {
        // Given
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(3);
        final Account account = new Account(1L, 3000L);
        final Account account2 = new Account(2L, 1000L);
        final Account account3 = new Account(3L, 6000L);
        final Account account4 = new Account(4L, 100000L);

        // When
        lruAccountCache.putAccount(account);
        lruAccountCache.putAccount(account2);
        lruAccountCache.putAccount(account3);
        lruAccountCache.putAccount(account4);

        // Then
        final List<Account> top3AccountsByBalance = lruAccountCache.getTop3AccountsByBalance();
        assertEquals(3, top3AccountsByBalance.size());
        assertEquals(100000L, top3AccountsByBalance.get(0).balance());
        assertEquals(6000, top3AccountsByBalance.get(1).balance());
        assertEquals(1000, top3AccountsByBalance.get(2).balance());
    }

    @Test
    @DisplayName("Should get top 3 with cache hot by balance successfully")
    void shouldGetTop3ByBalanceWithCacheHotSuccessfully() {
        // Given
        final SimpleLruAccountCache lruAccountCache = new SimpleLruAccountCache(3);
        final Account account = new Account(1L, 3000L);
        final Account account2 = new Account(2L, 1000L);
        final Account account3 = new Account(3L, 6000L);
        final Account account4 = new Account(4L, 100000L);

        // When
        lruAccountCache.putAccount(account);
        lruAccountCache.putAccount(account2);
        lruAccountCache.putAccount(account3);
        lruAccountCache.getAccountById(1L);
        lruAccountCache.putAccount(account4);

        // Then
        final List<Account> top3AccountsByBalance = lruAccountCache.getTop3AccountsByBalance();
        assertEquals(3, top3AccountsByBalance.size());
        assertEquals(100000L, top3AccountsByBalance.get(0).balance());
        assertEquals(6000, top3AccountsByBalance.get(1).balance());
        assertEquals(3000, top3AccountsByBalance.get(2).balance());
    }
}
