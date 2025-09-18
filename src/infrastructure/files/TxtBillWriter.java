package infrastructure.files;

import domain.billing.Bill;
import domain.billing.BillWriter;

import java.nio.file.*; import java.io.*;

public final class TxtBillWriter implements BillWriter {
    private final Path outDir;
    public TxtBillWriter(Path outDir){ this.outDir=outDir; try { Files.createDirectories(outDir); } catch(IOException ignored){} }
    public void write(Bill bill){
        Path p = outDir.resolve(bill.number()+".txt");
        try (var w = Files.newBufferedWriter(p)) { w.write(bill.renderText()); }
        catch(IOException e){ throw new UncheckedIOException(e); }
    }
}
