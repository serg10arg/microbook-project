package com.microbook.itemservice.domain.exception;

public class ItemNotFoundException extends RuntimeException {

    private final String itemId;

    public ItemNotFoundException(String itemId) {
        super("Item not found with id: " + itemId);
        this.itemId = itemId;
    }

    public String getItemId() {
        return itemId;
    }
}
