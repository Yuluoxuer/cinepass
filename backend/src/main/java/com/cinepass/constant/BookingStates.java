package com.cinepass.constant;

/**
 * BookingState 常量（系分 §4）。
 */
public final class BookingStates {

    public static final String IDLE = "Idle";
    public static final String SELECT_MOVIE = "SelectMovie";
    public static final String SELECT_CINEMA = "SelectCinema";
    public static final String SELECT_SHOW = "SelectShow";
    public static final String SELECT_SEAT = "SelectSeat";
    public static final String CONFIRM_ORDER = "ConfirmOrder";
    public static final String PAY_MOCK = "PayMock";
    public static final String TICKET_ISSUED = "TicketIssued";

    private BookingStates() {
    }
}
