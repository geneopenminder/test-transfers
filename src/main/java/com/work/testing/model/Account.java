package com.work.testing.model;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Account {

    public String id;
    public volatile int amount;
    public AtomicBoolean lock = new AtomicBoolean(false);

    public Account(String id, int amount) {
        this.id = id;
        this.amount = amount;
    }

    public void debit(int debitAmount) {
        amount -= debitAmount;
    }

    public void credit(int creditAmount) {
        amount += creditAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
