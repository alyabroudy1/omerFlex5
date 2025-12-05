package com.omarflex5.ui.home;

/**
 * Represents the UI state for the Home screen.
 */
public class UiState {

    public enum State {
        LOADING,
        SUCCESS,
        ERROR
    }

    private final State state;
    private final String errorMessage;
    private final ErrorType errorType;

    public enum ErrorType {
        NETWORK, // No internet connection
        SERVER, // API/server error
        UNKNOWN // Generic error
    }

    private UiState(State state, String errorMessage, ErrorType errorType) {
        this.state = state;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    public static UiState loading() {
        return new UiState(State.LOADING, null, null);
    }

    public static UiState success() {
        return new UiState(State.SUCCESS, null, null);
    }

    public static UiState error(String message, ErrorType type) {
        return new UiState(State.ERROR, message, type);
    }

    public static UiState networkError() {
        return new UiState(State.ERROR, "لا يوجد اتصال بالإنترنت", ErrorType.NETWORK);
    }

    public static UiState serverError(String message) {
        return new UiState(State.ERROR, message, ErrorType.SERVER);
    }

    public State getState() {
        return state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public boolean isLoading() {
        return state == State.LOADING;
    }

    public boolean isSuccess() {
        return state == State.SUCCESS;
    }

    public boolean isError() {
        return state == State.ERROR;
    }
}
