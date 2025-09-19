package application.events.events;

public record RestockThresholdHit(String itemCode, int totalQtyLeft, int threshold) {}
