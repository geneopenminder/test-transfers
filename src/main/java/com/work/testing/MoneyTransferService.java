package com.work.testing;

import com.work.testing.exception.*;
import com.work.testing.model.Account;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MoneyTransferService {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    private static final long TRY_LOCK_TIMEOUT = 1000;

    public void addAccountWithBalance(String id, int amount) {
        accounts.putIfAbsent(id, new Account(id, amount));
    }

    public int getAccountBalance(String id) {
        return accounts.get(id).amount; //not check exists ... for simplifying
    }

    public void transfer(String fromId, String toId, int amount) {

        if (fromId == null || toId == null) {
            throw new BadArgException();
        }

        if (!accounts.containsKey(fromId) || !accounts.containsKey(toId)) {
            throw new NotExistsException();
        }

        if (fromId.equalsIgnoreCase(toId)) {
            throw new EqualsAccountsException();
        }

        if (amount <= 0) {
            throw new BadAmountException();
        }

        //lock order by hashing
        final Account fromAccount = accounts.get(fromId);
        final Account toAccount = accounts.get(toId);

        if (fromAccount.amount < amount) {
            throw new NoFundsException();
        }

        final int fromHash = fromId.hashCode();
        final int toHash = toId.hashCode();

        boolean transferCompleted = false;

        //due to non blocking algorithm is used, dead lock is impossible for more than TRY_LOCK_TIMEOUT
        //but hash ordered lock help to reduce contentions and failed by TRY_LOCK_TIMEOUT transfers

        if (fromHash < toHash) {
            transferCompleted = transferWithLock(fromAccount, toAccount, amount);
        } else if (toHash < fromHash) {
            transferCompleted = transferWithLock(toAccount, fromAccount, amount);
        } else {
            try {
                lock.tryLock(TRY_LOCK_TIMEOUT, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException();
            }
            try {
                fromAccount.debit(amount);
                toAccount.credit(amount);
                transferCompleted = true;
            } finally {
                lock.unlock();
            }
        }

        if (!transferCompleted) {
            throw new SystemBusyException();
        }

    }

    private boolean transferWithLock(Account from, Account to, int amount) {
        try {
            if (tryLock(from) && tryLock(to)) {
                from.debit(amount);
                to.credit(amount);
                return true;
            } else {
                return false;
            }
        } finally {
            unlock(to);
            unlock(from);
        }
    }

    public boolean tryLock(Account account) {
        final long start = System.nanoTime();
        do {
            if (account.lock.compareAndSet(false, true)) {
                return true;
            }
        } while (System.nanoTime() - start < TRY_LOCK_TIMEOUT);

        return false;
    }

    public void unlock(Account account) {
        account.lock.set(false);
    }

}
