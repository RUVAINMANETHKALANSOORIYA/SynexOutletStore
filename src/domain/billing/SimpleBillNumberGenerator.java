package domain.billing;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class SimpleBillNumberGenerator implements BillNumberGenerator {
    private final AtomicInteger seq = new AtomicInteger(1);
    public String next(){
        String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "POS-" + d + "-" + String.format("%04d", seq.getAndIncrement());
    }
}
