package com.omarflex5.data.source;

public interface DataSourceCallback<T> {
    void onSuccess(T result);

    void onError(Throwable t);
}
