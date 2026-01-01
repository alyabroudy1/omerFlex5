package com.omarflex6.controller.base;

/**
 * Interface for controllers that handle item clicks.
 * 
 * @param <T> The type of item being clicked.
 */
public interface ItemClickController<T> {
    void onItemClick(T item);

    void onItemFocus(T item);
}
