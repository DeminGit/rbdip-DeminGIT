package com.rbdip.bookstore.purchase;

/** Public purchase lookup contract, independent of order persistence. */
public interface PurchaseHistory {
    boolean hasPurchased(Long productId, String authorName);
}
