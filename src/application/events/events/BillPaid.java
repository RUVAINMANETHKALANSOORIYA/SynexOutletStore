package application.events.events;

import domain.common.Money;

public record BillPaid(String billNo, Money total, String channel, String user) {}
