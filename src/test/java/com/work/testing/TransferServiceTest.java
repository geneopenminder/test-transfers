package com.work.testing;

import com.work.testing.exception.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class TransferServiceTest
{

    private MoneyTransferService transferService;

    private final static int balance_1 = 500_000_000;
    private final static int balance_2 = 100_000_000;
    private final static int transfer_amount_1 = 15;

    private final static String account_1 = "account_1";
    private final static String account_2 = "account_2";
    private final static String account_not_exists = "account_3";

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before //before each test
    public void addAccounts() {
        transferService = new MoneyTransferService();
        transferService.addAccountWithBalance(account_1, balance_1);
        transferService.addAccountWithBalance(account_2, balance_2);
    }

    @Test
    public void singleSuccessTransfer()
    {
        transferService.transfer(account_1, account_2, transfer_amount_1);
        assertEquals( transferService.getAccountBalance(account_1),  balance_1 - transfer_amount_1);
        assertEquals( transferService.getAccountBalance(account_2),  balance_2 + transfer_amount_1);
    }

    @Test
    public void singleTransferNoFunds()
    {
        exception.expect(NoFundsException.class);
        transferService.transfer(account_1, account_2, balance_1 + transfer_amount_1);
    }

    @Test
    public void singleTransferBadAmount()
    {
        exception.expect(BadAmountException.class);
        transferService.transfer(account_1, account_2, -1);
    }

    @Test
    public void singleTransferBadAmountZero()
    {
        exception.expect(BadAmountException.class);
        transferService.transfer(account_1, account_2, 0);
    }

    @Test
    public void singleTransferAccountEquals()
    {
        exception.expect(EqualsAccountsException.class);
        transferService.transfer(account_1, account_1, transfer_amount_1);
    }

    @Test
    public void singleTransferAccountNotExistsFrom()
    {
        exception.expect(NotExistsException.class);
        transferService.transfer(account_not_exists, account_2, transfer_amount_1);
    }

    @Test
    public void singleTransferAccountNotExistsTo()
    {
        exception.expect(NotExistsException.class);
        transferService.transfer(account_1, account_not_exists, transfer_amount_1);
    }

    @Test
    public void singleTransferAccountNullFrom()
    {
        exception.expect(BadArgException.class);
        transferService.transfer(null, account_not_exists, transfer_amount_1);
    }

    @Test
    public void singleTransferAccountNullTo()
    {
        exception.expect(BadArgException.class);
        transferService.transfer(account_1, null, transfer_amount_1);
    }

    @Test
    public void multipleTransfers()
    {
        final int account1balanceBefore = transferService.getAccountBalance(account_1);
        final int account2balanceBefore = transferService.getAccountBalance(account_2);
        final int totalBalanceBefore = account1balanceBefore + account2balanceBefore;

        IntStream.range(0, 100).forEach( idx -> {
            transferService.transfer(account_1, account_2, ThreadLocalRandom.current().nextInt(1,20));
        });

        final int account1balanceAfter = transferService.getAccountBalance(account_1);
        final int account2balanceAfter = transferService.getAccountBalance(account_2);
        final int totalBalanceAfter = account1balanceBefore + account2balanceBefore;

        assertNotEquals(account1balanceBefore, account1balanceAfter);
        assertNotEquals(account2balanceBefore, account2balanceAfter);
        assertEquals(totalBalanceBefore, totalBalanceAfter);
    }

    @Test
    public void multipleThreadTransfers() throws Exception {
        final int cpuNumber = Runtime.getRuntime().availableProcessors();
        assertNotEquals(cpuNumber, 1); //can't test multi-thread at single core

        final int account1balanceBefore = transferService.getAccountBalance(account_1);
        final int account2balanceBefore = transferService.getAccountBalance(account_2);
        final int totalBalanceBefore = account1balanceBefore + account2balanceBefore;

        final ExecutorService executorService = Executors.newFixedThreadPool(cpuNumber);
        for (int i = 0; i < cpuNumber; i++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    IntStream.range(0, 1000_000).forEach( idx -> {
                        try {
                            transferService.transfer(account_1, account_2, 1);
                        } catch (SystemBusyException ignored) {
                            System.out.println("busy");
                        }
                    });
                }
            });
        }

        executorService.shutdown();
        //5 seconds will be enough
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        final int account1balanceAfter = transferService.getAccountBalance(account_1);
        final int account2balanceAfter = transferService.getAccountBalance(account_2);
        final int totalBalanceAfter = account1balanceBefore + account2balanceBefore;

        assertNotEquals(account1balanceBefore, account1balanceAfter);
        assertNotEquals(account2balanceBefore, account2balanceAfter);
        assertEquals(totalBalanceBefore, totalBalanceAfter);
    }

    //this test is always OK because of non blocking algorithm is used
    @Test
    public void multipleThreadTransfersDeadLockAvoidCheck() throws Exception {
        final int cpuNumber = Runtime.getRuntime().availableProcessors();
        assertNotEquals(cpuNumber, 1); //can't test multi-thread at single core

        final int account1balanceBefore = transferService.getAccountBalance(account_1);
        final int account2balanceBefore = transferService.getAccountBalance(account_2);
        final int totalBalanceBefore = account1balanceBefore + account2balanceBefore;

        final ExecutorService executorService = Executors.newFixedThreadPool(2);

        // 1 -> 2
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                IntStream.range(0, 1000_000).forEach( idx -> {
                    try {
                        transferService.transfer(account_1, account_2, 1);
                    } catch (SystemBusyException ignored) {
                        System.out.println("busy");
                    }
                });
            }
        });

        //reverse 2 -> 1
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                IntStream.range(0, 1000_000).forEach( idx -> {
                    try {
                        transferService.transfer(account_2, account_1, 1);
                    } catch (SystemBusyException ignored) {
                        System.out.println("busy");
                    }
                });
            }
        });

        executorService.shutdown();
        //5 seconds will be enough
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        final int account1balanceAfter = transferService.getAccountBalance(account_1);
        final int account2balanceAfter = transferService.getAccountBalance(account_2);
        final int totalBalanceAfter = account1balanceBefore + account2balanceBefore;

        assertNotEquals(account1balanceBefore, account1balanceAfter);
        assertNotEquals(account2balanceBefore, account2balanceAfter);
        assertEquals(totalBalanceBefore, totalBalanceAfter);
    }

}
