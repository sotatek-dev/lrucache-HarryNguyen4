package dev.lillya;

public record Account(long id, long balance) implements Comparable<Account> {

    @Override
    public int compareTo(final Account that) {
        return Long.compare(this.balance, that.balance);
    }
}
