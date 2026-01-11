package com.example.jutjubic.exceptions;

/**
 * Izuzetak koji se baca kada korisnik prekorači dozvoljeni broj komentara.
 * Ograničenje: 60 komentara po satu.
 */
public class CommentLimitExceededException extends RuntimeException {

    private final int limit;
    private final int currentCount;
    private final long minutesUntilReset;

    public CommentLimitExceededException(int limit, int currentCount, long minutesUntilReset) {
        super(String.format(
            "Prekoračili ste ograničenje od %d komentara po satu. Trenutno imate %d komentara. " +
            "Pokušajte ponovo za %d minuta.",
            limit, currentCount, minutesUntilReset
        ));
        this.limit = limit;
        this.currentCount = currentCount;
        this.minutesUntilReset = minutesUntilReset;
    }

    public int getLimit() {
        return limit;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public long getMinutesUntilReset() {
        return minutesUntilReset;
    }
}

