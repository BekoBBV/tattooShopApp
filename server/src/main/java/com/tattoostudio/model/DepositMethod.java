package com.tattoostudio.model;

/** How a deposit was paid. */
public enum DepositMethod {
    /** Through the online payment page (Revolut). */
    ONLINE,
    /** In the studio; the admin marks it paid. */
    CASH
}
